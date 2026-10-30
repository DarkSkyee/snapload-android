package com.example.mediadownloader.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object FFmpegEngineManager {
    private const val TAG = "FFmpegManager"
    private val FFMPEG_RELEASE_URLS = listOf(
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.3/ffmpeg-arm64-v8a.zip",
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.2/ffmpeg-arm64-v8a.zip",
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.1/ffmpeg-arm64-v8a.zip",
        "https://github.com/DarkSkyee/snapload-android/releases/download/v3.0.0/ffmpeg-arm64-v8a.zip"
    )

    private val mutex = Mutex()

    fun getFFmpegDir(context: Context): File {
        val base = File(context.applicationContext.noBackupFilesDir, "youtubedl-android")
        val packages = File(base, "packages")
        return File(packages, "ffmpeg")
    }

    fun isInstalled(context: Context): Boolean {
        val dir = getFFmpegDir(context)
        if (!dir.exists() || !dir.isDirectory) return false
        val libDir = File(dir, "usr/lib")
        if (!libDir.exists() || !libDir.isDirectory) return false
        val files = libDir.listFiles() ?: return false
        val avcodecFiles = files.filter { it.name.startsWith("libavcodec") }
        if (avcodecFiles.isEmpty()) return false
        val hasRealLibrary = avcodecFiles.any { it.length() > 100 * 1024L }
        if (!hasRealLibrary) return false
        val avcodec61 = File(libDir, "libavcodec.so.61")
        if (avcodec61.exists() && isCorruptPlaceholder(avcodec61)) {
            return false
        }
        return true
    }

    fun getFFmpegLocation(context: Context): String {
        val targetDir = getFFmpegDir(context)
        val binDir = File(targetDir, "bin")
        if (File(binDir, "ffmpeg").exists() && File(binDir, "ffprobe").exists()) {
            return binDir.absolutePath
        }
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val libFfmpeg = File(nativeDir, "libffmpeg.so")
        if (libFfmpeg.exists()) {
            return libFfmpeg.absolutePath
        }
        return targetDir.absolutePath
    }

    fun repairSymlinksAndBinaries(context: Context) {
        val targetDir = getFFmpegDir(context)
        val libDir = File(targetDir, "usr/lib")
        if (libDir.exists() && libDir.isDirectory) {
            val files = libDir.listFiles() ?: emptyArray()
            for (file in files) {
                if (isCorruptPlaceholder(file)) {
                    val targetName = try {
                        file.readText().trim()
                    } catch (_: Throwable) { "" }

                    if (targetName.isNotEmpty() && (targetName.endsWith(".so") || targetName.contains(".so."))) {
                        val targetFile = File(libDir, targetName)
                        if (targetFile.exists() && targetFile.length() > 1024L) {
                            file.delete()
                            var linked = false
                            try {
                                android.system.Os.symlink(targetName, file.absolutePath)
                                linked = true
                            } catch (t: Throwable) {
                                Log.w(TAG, "Symlink failed for ${file.name} -> $targetName: ${t.message}")
                            }
                            if (!linked) {
                                try {
                                    targetFile.copyTo(file, overwrite = true)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Copy fallback failed for ${file.name}: ${t.message}")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Setup bin directory and binary aliases
        try {
            val binDir = File(targetDir, "bin").apply { mkdirs() }
            val nativeDir = File(context.applicationInfo.nativeLibraryDir)
            val nativeFfmpeg = File(nativeDir, "libffmpeg.so")
            val nativeFfprobe = File(nativeDir, "libffprobe.so")

            if (nativeFfmpeg.exists()) {
                setupBinaryAlias(nativeFfmpeg, File(binDir, "ffmpeg"))
                setupBinaryAlias(nativeFfmpeg, File(targetDir, "ffmpeg"))
                setupBinaryAlias(nativeFfmpeg, File(targetDir, "libffmpeg.so"))
            }

            if (nativeFfprobe.exists()) {
                setupBinaryAlias(nativeFfprobe, File(binDir, "ffprobe"))
                setupBinaryAlias(nativeFfprobe, File(targetDir, "ffprobe"))
                setupBinaryAlias(nativeFfprobe, File(targetDir, "libffprobe.so"))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed creating binary aliases: ${t.message}")
        }
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            val stat = android.system.Os.lstat(file.absolutePath)
            android.system.OsConstants.S_ISLNK(stat.st_mode)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isCorruptPlaceholder(file: File): Boolean {
        if (!file.exists() || file.isDirectory) return false
        if (isSymlink(file)) return false
        if (file.length() in 1..1024L) {
            return try {
                file.inputStream().use { stream ->
                    val header = ByteArray(4)
                    val read = stream.read(header)
                    if (read < 4) true
                    else header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() || header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
                }
            } catch (_: Throwable) {
                true
            }
        }
        return false
    }

    private fun setupBinaryAlias(source: File, destination: File) {
        if (destination.exists()) {
            if (isCorruptPlaceholder(destination)) {
                destination.delete()
            } else {
                destination.setExecutable(true, false)
                return
            }
        }
        var created = false
        try {
            android.system.Os.symlink(source.absolutePath, destination.absolutePath)
            created = true
        } catch (_: Throwable) {}
        if (!created) {
            try {
                source.copyTo(destination, overwrite = true)
            } catch (_: Throwable) {}
        }
        destination.setExecutable(true, false)
    }

    suspend fun ensureFFmpegInstalled(
        context: Context,
        onProgress: ((percent: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        repairSymlinksAndBinaries(context)
        if (isInstalled(context)) return@withContext true

        mutex.withLock {
            repairSymlinksAndBinaries(context)
            if (isInstalled(context)) return@withLock true

            val targetDir = getFFmpegDir(context)
            targetDir.mkdirs()

            val tempZip = File(context.cacheDir, "ffmpeg_download.tmp")

            for (ffmpegUrl in FFMPEG_RELEASE_URLS) {
                try {
                    Log.i(TAG, "Downloading FFmpeg native engine package from $ffmpegUrl")
                    val url = URL(ffmpegUrl)
                    var connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "SnapLoad-Android/3.0.3")
                    }

                    var responseCode = connection.responseCode
                    if (responseCode in 300..399) {
                        val redirectUrl = connection.getHeaderField("Location")
                        if (!redirectUrl.isNullOrBlank()) {
                            connection.disconnect()
                            connection = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 15000
                                readTimeout = 30000
                                instanceFollowRedirects = true
                                setRequestProperty("User-Agent", "SnapLoad-Android/3.0.3")
                            }
                            responseCode = connection.responseCode
                        }
                    }

                    if (responseCode !in 200..299) {
                        throw java.io.IOException("FFmpeg download failed with HTTP code $responseCode")
                    }

                    val totalBytes = connection.contentLengthLong
                    var downloadedBytes = 0L

                    BufferedInputStream(connection.inputStream).use { input ->
                        FileOutputStream(tempZip).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            var lastReported = -1

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                    if (percent != lastReported) {
                                        lastReported = percent
                                        onProgress?.invoke(percent)
                                    }
                                }
                            }
                        }
                    }

                    Log.i(TAG, "Extracting FFmpeg to ${targetDir.absolutePath}...")
                    unzip(tempZip, targetDir)
                    tempZip.delete()

                    repairSymlinksAndBinaries(context)

                    val success = isInstalled(context)
                    Log.i(TAG, "FFmpeg installed successfully: $success")
                    if (success) return@withLock true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed downloading FFmpeg from $ffmpegUrl: ${e.message}")
                    if (tempZip.exists()) tempZip.delete()
                }
            }
            repairSymlinksAndBinaries(context)
            isInstalled(context)
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                val canonicalDestPath = destDir.canonicalPath
                val canonicalEntryPath = newFile.canonicalPath
                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    throw SecurityException("Zip traversal attempt: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
