package com.example.mediadownloader.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.mediadownloader.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val releaseInfo: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/DarkSkyee/snapload-android/releases/latest"
        private const val CHECK_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes cooldown

        fun cleanVersionTag(tag: String): String {
            return tag.trim().removePrefix("v").removePrefix("V").trim()
        }

        fun isNewerVersion(remote: String, current: String): Boolean {
            val cleanRemote = cleanVersionTag(remote)
            val cleanCurrent = cleanVersionTag(current)
            if (cleanRemote == cleanCurrent) return false

            val remoteParts = cleanRemote.split(".").map { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
            val currentParts = cleanCurrent.split(".").map { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }

            val length = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until length) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        /**
         * Purge any lingering APK and temp files from the updates cache directory.
         * Calling this on app launch and after installation prevents periodic Play Protect scanner flags.
         */
        fun cleanupUpdatesDir(context: Context) {
            try {
                val updatesDir = File(context.cacheDir, "updates")
                if (updatesDir.exists()) {
                    updatesDir.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".apk", ignoreCase = true) ||
                            file.name.endsWith(".tmp", ignoreCase = true)
                        ) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cleanup updates directory: ${e.message}")
            }
        }
    }

    private val preferences = AppPreferences(context)

    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }

    /**
     * Verifies that the APK is non-empty, parseable by PackageManager, targets this app's package name,
     * and has a versionCode strictly greater than the currently installed version.
     */
    fun verifyApkArchive(apkFile: File, expectedTargetVersion: String? = null): Result<PackageInfo> {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return Result.failure(IllegalStateException("APK file is missing or empty"))
        }

        val archiveInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Failed to parse package archive: ${e.message}"))
        } ?: return Result.failure(IllegalStateException("File is corrupt or not a valid Android APK archive"))

        if (archiveInfo.packageName != context.packageName) {
            apkFile.delete()
            return Result.failure(
                SecurityException("APK package name mismatch: expected ${context.packageName}, got ${archiveInfo.packageName}")
            )
        }

        val currentCode = getCurrentVersionCode()
        val newCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }

        if (newCode < currentCode) {
            apkFile.delete()
            return Result.failure(
                IllegalStateException("Downloaded APK versionCode ($newCode) is older than current ($currentCode)")
            )
        }

        if (expectedTargetVersion != null) {
            val cleanExpected = cleanVersionTag(expectedTargetVersion)
            val cleanArchiveVersion = archiveInfo.versionName?.let { cleanVersionTag(it) }
            if (cleanArchiveVersion != null &&
                cleanArchiveVersion != cleanExpected &&
                cleanArchiveVersion.substringBefore('-') != cleanExpected.substringBefore('-')
            ) {
                apkFile.delete()
                return Result.failure(
                    IllegalStateException("Downloaded APK version ($cleanArchiveVersion) does not match expected target ($cleanExpected)")
                )
            }
        }

        return Result.success(archiveInfo)
    }

    suspend fun checkForUpdates(force: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersionName()
            Log.d(TAG, "Checking for updates. Current version: $currentVersion")

            val url = URL(GITHUB_RELEASES_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "SnapLoad-Android/${currentVersion}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Error("GitHub API response code: $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "No release notes provided.")
            val htmlUrl = json.optString("html_url", "https://github.com/DarkSkyee/snapload-android")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                        break
                    }
                }
            }

            preferences.lastAppUpdateCheck = System.currentTimeMillis()

            if (isNewerVersion(tagName, currentVersion)) {
                val releaseInfo = ReleaseInfo(
                    tagName = tagName,
                    versionName = releaseTitle.ifBlank { tagName },
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    htmlUrl = htmlUrl
                )
                UpdateCheckResult.UpdateAvailable(releaseInfo)
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}", e)
            UpdateCheckResult.Error(e.message ?: "Network error checking updates")
        }
    }

    suspend fun checkAutoUpdateOnLaunch(): UpdateCheckResult? {
        if (!preferences.autoUpdateApp) return null

        val now = System.currentTimeMillis()
        val timeSinceLastCheck = now - preferences.lastAppUpdateCheck

        if (preferences.lastAppUpdateCheck == 0L || timeSinceLastCheck > CHECK_INTERVAL_MS) {
            return checkForUpdates(force = false)
        }
        return null
    }

    fun getDownloadedUpdateApk(targetVersion: String? = null): File? {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) return null

        // Remove legacy unversioned update file if present
        val legacyFile = File(updatesDir, "SnapLoad-update.apk")
        if (legacyFile.exists()) {
            legacyFile.delete()
        }

        val cleanTarget = targetVersion?.let { cleanVersionTag(it) }
        val targetFileName = if (cleanTarget != null) "SnapLoad-v$cleanTarget.apk" else null

        val candidate = if (targetFileName != null) {
            File(updatesDir, targetFileName)
        } else {
            updatesDir.listFiles()?.firstOrNull { it.extension.equals("apk", ignoreCase = true) }
        }

        if (candidate == null || !candidate.exists() || candidate.length() == 0L) {
            return null
        }

        val verification = verifyApkArchive(candidate, targetVersion)
        if (verification.isFailure) {
            Log.w(TAG, "Cached update APK ${candidate.name} failed verification: ${verification.exceptionOrNull()?.message}. Deleting.")
            candidate.delete()
            return null
        }

        return candidate
    }

    suspend fun downloadApk(
        downloadUrl: String,
        targetVersion: String? = null,
        onProgress: (percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Strictly enforce HTTPS
            if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext Result.failure(
                    SecurityException("Insecure download URL: updates must be served strictly over HTTPS")
                )
            }

            // Always write to private app cache directory (never public Downloads)
            val updatesDir = File(context.cacheDir, "updates").apply {
                mkdirs()
            }

            // Clean up any stale APKs or partial downloads in updatesDir
            cleanupUpdatesDir(context)

            val cleanTarget = targetVersion?.let { cleanVersionTag(it) } ?: "update"
            val destinationFile = File(updatesDir, "SnapLoad-v$cleanTarget.apk")
            val tempFile = File(updatesDir, "SnapLoad-v$cleanTarget.tmp")
            if (tempFile.exists()) tempFile.delete()
            if (destinationFile.exists()) destinationFile.delete()

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0
            val maxRedirects = 5

            while (true) {
                if (!currentUrl.startsWith("https://", ignoreCase = true)) {
                    return@withContext Result.failure(
                        SecurityException("Insecure redirect URL: updates must be served strictly over HTTPS")
                    )
                }

                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "SnapLoad-Android/${getCurrentVersionName()}")
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308
                ) {
                    val location = connection.getHeaderField("Location")
                        ?: return@withContext Result.failure(IllegalStateException("Redirect header missing"))
                    currentUrl = location
                    redirectCount++
                    if (redirectCount > maxRedirects) {
                        return@withContext Result.failure(IllegalStateException("Too many redirects: $redirectCount"))
                    }
                    connection.disconnect()
                    continue
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(IllegalStateException("Download failed with HTTP $responseCode"))
                }

                break
            }

            val totalLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalLength > 0L) {
                            val percent = ((downloadedBytes * 100) / totalLength).toInt().coerceIn(0, 100)
                            onProgress(percent)
                        }
                    }
                    output.flush()
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded APK is empty"))
            }

            if (totalLength > 0L && downloadedBytes < totalLength) {
                tempFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("Download truncated: received $downloadedBytes of $totalLength bytes")
                )
            }

            // Verify package integrity, package name, and versionCode before finalizing
            val verification = verifyApkArchive(tempFile, targetVersion)
            if (verification.isFailure) {
                tempFile.delete()
                return@withContext Result.failure(
                    verification.exceptionOrNull() ?: IllegalStateException("Corrupt or invalid update archive")
                )
            }

            if (!tempFile.renameTo(destinationFile)) {
                tempFile.inputStream().use { src ->
                    destinationFile.outputStream().use { dst ->
                        src.copyTo(dst)
                    }
                }
                tempFile.delete()
            }

            onProgress(100)
            Result.success(destinationFile)

        } catch (e: Exception) {
            Log.e(TAG, "APK download error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Modern Android PackageInstaller session API replacing deprecated Intent.ACTION_VIEW dropper heuristics.
     */
    fun promptInstallApk(apkFile: File): Result<Unit> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val canInstall = context.packageManager.canRequestPackageInstalls()
                if (!canInstall) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(manageIntent)
                    return Result.failure(SecurityException("App install permission requested"))
                }
            }

            // Verify integrity and versionCode before starting installer session
            val verification = verifyApkArchive(apkFile)
            if (verification.isFailure) {
                return Result.failure(
                    verification.exceptionOrNull() ?: IllegalStateException("Invalid APK for installation")
                )
            }

            val packageInstaller = context.packageManager.packageInstaller
            val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST)
                }
            }

            val sessionId = packageInstaller.createSession(sessionParams)
            var sessionCommitted = false
            try {
                packageInstaller.openSession(sessionId).use { activeSession ->
                    activeSession.openWrite("base.apk", 0, apkFile.length()).use { out ->
                        apkFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                        activeSession.fsync(out)
                    }

                    val statusIntent = Intent(context, InstallReceiver::class.java).apply {
                        action = InstallReceiver.ACTION_INSTALL_STATUS
                        putExtra(InstallReceiver.EXTRA_APK_PATH, apkFile.absolutePath)
                    }

                    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        statusIntent,
                        pendingIntentFlags
                    )

                    activeSession.commit(pendingIntent.intentSender)
                    sessionCommitted = true
                }
            } catch (e: Exception) {
                if (!sessionCommitted) {
                    try {
                        packageInstaller.abandonSession(sessionId)
                    } catch (abandonEx: Exception) {
                        Log.w(TAG, "Failed to abandon failed session $sessionId: ${abandonEx.message}")
                    }
                }
                throw e
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch PackageInstaller session: ${e.message}", e)
            Result.failure(e)
        }
    }
}
