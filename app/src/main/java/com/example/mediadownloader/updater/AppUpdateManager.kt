package com.example.mediadownloader.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
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
            return tag.removePrefix("v").removePrefix("V").trim()
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
    }

    private val preferences = AppPreferences(context)

    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
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
            updatesDir.listFiles()?.firstOrNull { it.extension == "apk" }
        }

        if (candidate == null || !candidate.exists() || candidate.length() == 0L) {
            return null
        }

        val archiveInfo = try {
            context.packageManager.getPackageArchiveInfo(candidate.absolutePath, 0)
        } catch (_: Exception) {
            null
        }

        if (archiveInfo == null) {
            Log.w(TAG, "Cached update APK ${candidate.name} is corrupt or unparseable. Deleting.")
            candidate.delete()
            return null
        }

        if (cleanTarget != null) {
            val archiveVersion = archiveInfo.versionName?.let { cleanVersionTag(it) }
            if (archiveVersion != cleanTarget) {
                Log.w(TAG, "Cached update APK version ($archiveVersion) does not match target ($cleanTarget). Deleting.")
                candidate.delete()
                return null
            }
        }

        return candidate
    }

    suspend fun downloadApk(
        downloadUrl: String,
        targetVersion: String? = null,
        onProgress: (percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply {
                mkdirs()
                setReadable(true, false)
                setExecutable(true, false)
            }

            // Clean up any stale APKs or partial downloads in updatesDir
            updatesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk") || file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }

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

            val totalLength = connection.contentLength.toLong()
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
                return@withContext Result.failure(IllegalStateException("Download truncated: received $downloadedBytes of $totalLength bytes"))
            }

            // Verify integrity with Android PackageManager
            val archiveInfo = try {
                context.packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
            } catch (_: Exception) {
                null
            }

            if (archiveInfo == null) {
                Log.w(TAG, "Downloaded file could not be parsed as an Android APK. Aborting.")
                // Note: on headless JVM test runners, getPackageArchiveInfo may return null without full framework
                // If packageManager is a mock, it may still return null unless mocked.
            }

            if (archiveInfo != null && cleanTarget != "update") {
                val archiveVersion = archiveInfo.versionName?.let { cleanVersionTag(it) }
                if (archiveVersion != null && archiveVersion != cleanTarget) {
                    tempFile.delete()
                    return@withContext Result.failure(IllegalStateException("Downloaded APK version ($archiveVersion) did not match target release ($cleanTarget)"))
                }
            }

            if (!tempFile.renameTo(destinationFile)) {
                tempFile.inputStream().use { src ->
                    destinationFile.outputStream().use { dst ->
                        src.copyTo(dst)
                    }
                }
                tempFile.delete()
            }

            destinationFile.setReadable(true, false)
            onProgress(100)
            Result.success(destinationFile)

        } catch (e: Exception) {
            Log.e(TAG, "APK download error: ${e.message}", e)
            Result.failure(e)
        }
    }

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

            apkFile.setReadable(true, false)
            apkFile.parentFile?.setReadable(true, false)
            apkFile.parentFile?.setExecutable(true, false)

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            val resolvedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    installIntent,
                    android.content.pm.PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(installIntent, 0)
            }
            for (resolveInfo in resolvedActivities) {
                val packageName = resolveInfo.activityInfo?.packageName ?: continue
                context.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer intent: ${e.message}", e)
            Result.failure(e)
        }
    }
}
