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
        if (!libDir.exists()) return false
        val files = libDir.listFiles() ?: return false
        return files.any { it.name.startsWith("libavcodec") }
    }

    suspend fun ensureFFmpegInstalled(
        context: Context,
        onProgress: ((percent: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInstalled(context)) return@withContext true

        mutex.withLock {
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
                        setRequestProperty("User-Agent", "SnapLoad-Android/3.0.1")
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
                                setRequestProperty("User-Agent", "SnapLoad-Android/3.0.1")
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

                    val success = isInstalled(context)
                    Log.i(TAG, "FFmpeg installed successfully: $success")
                    if (success) return@withLock true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed downloading FFmpeg from $ffmpegUrl: ${e.message}")
                    if (tempZip.exists()) tempZip.delete()
                }
            }
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
