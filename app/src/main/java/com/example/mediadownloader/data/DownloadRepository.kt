package com.example.mediadownloader.data

import android.content.Context
import android.util.Log
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.MediaMetadata
import com.example.mediadownloader.util.LinkParser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

interface DownloadRepository {
    suspend fun fetchMetadata(url: String): Result<MediaMetadata>
    suspend fun downloadMedia(
        url: String,
        format: DownloadFormat,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<File>
    suspend fun cancelDownload(downloadId: String): Boolean
    suspend fun updateEngine(): Result<String>
    fun getEngineVersion(): String
}

class DefaultDownloadRepository(private val context: Context) : DownloadRepository {

    companion object {
        private const val TAG = "DownloadRepo"
    }

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching metadata for: $url")
            val platform = LinkParser.detectPlatform(url)
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
            }
            val videoInfo = YoutubeDL.getInstance().getInfo(request)
            val metadata = MediaMetadata(
                url = url,
                title = videoInfo.title ?: "Pobrane_multimedia_${System.currentTimeMillis()}",
                author = videoInfo.uploader ?: videoInfo.uploaderId ?: "",
                thumbnailUrl = videoInfo.thumbnail,
                durationSeconds = videoInfo.duration.toLong(),
                platform = platform
            )
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch metadata: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun downloadMedia(
        url: String,
        format: DownloadFormat,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(context.cacheDir, "downloads").apply { mkdirs() }
            val uniqueFileId = UUID.randomUUID().toString().substring(0, 8)
            val outputTemplate = "${downloadDir.absolutePath}/media_${uniqueFileId}.%(ext)s"

            val request = YoutubeDLRequest(url).apply {
                addOption("-o", outputTemplate)
                addOption("--no-mtime")
                addOption("--no-playlist")

                when (format) {
                    DownloadFormat.MP4 -> {
                        addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best")
                        addOption("--merge-output-format", "mp4")
                    }
                    DownloadFormat.MP3 -> {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        addOption("--audio-quality", "0")
                    }
                }
            }

            Log.d(TAG, "Executing download $downloadId for $url ($format)")

            YoutubeDL.getInstance().execute(request, downloadId) { progress, etaInSeconds, line ->
                // Clean and format line for UI display (e.g. download speed)
                val statusText = parseStatusLine(line, progress, etaInSeconds)
                onProgress(progress, etaInSeconds, statusText)
            }

            // Locate the generated file
            val expectedExtension = format.extension
            val matchingFiles = downloadDir.listFiles { file ->
                file.isFile && file.name.startsWith("media_${uniqueFileId}") && file.extension.equals(expectedExtension, ignoreCase = true)
            }

            val targetFile = matchingFiles?.firstOrNull() ?: downloadDir.listFiles { file ->
                file.isFile && file.name.startsWith("media_${uniqueFileId}")
            }?.firstOrNull()

            if (targetFile != null && targetFile.exists() && targetFile.length() > 0L) {
                Log.i(TAG, "Download finished successfully: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                Result.success(targetFile)
            } else {
                val errorMsg = "Pobieranie zakończone, ale nie znaleziono pliku wyjściowego w ${downloadDir.absolutePath}"
                Log.e(TAG, errorMsg)
                Result.failure(IllegalStateException(errorMsg))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download error for id $downloadId: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun cancelDownload(downloadId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().destroyProcessById(downloadId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel process $downloadId: ${e.message}")
            false
        }
    }

    override suspend fun updateEngine(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            val newVersion = YoutubeDL.getInstance().versionName(context) ?: "nieznana"
            Result.success("Zaktualizowano silnik yt-dlp ($status). Wersja: $newVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update yt-dlp engine: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun getEngineVersion(): String {
        return try {
            YoutubeDL.getInstance().versionName(context) ?: "Niedostępna"
        } catch (_: Exception) {
            "Niedostępna"
        }
    }

    private fun parseStatusLine(line: String?, progress: Float, eta: Long): String {
        if (line.isNullOrBlank()) {
            return "${progress.toInt()}%"
        }
        // Extract speed if present in line, e.g., "12.3MiB/s"
        val speedMatch = "([0-9.]+ ?[kKMGT]?i?B/s)".toRegex().find(line)
        val speed = speedMatch?.value

        return buildString {
            append("${progress.toInt()}%")
            if (!speed.isNullOrBlank()) {
                append(" • $speed")
            }
            if (eta > 0) {
                val m = eta / 60
                val s = eta % 60
                append(String.format(" • ETA %02d:%02d", m, s))
            }
        }
    }
}
