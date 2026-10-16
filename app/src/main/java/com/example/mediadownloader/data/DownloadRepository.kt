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
        private const val MAX_CACHE_AGE_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            YoutubeDLInitializer.ensureInitialized(context)
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
            YoutubeDLInitializer.ensureInitialized(context)
            val downloadDir = File(context.cacheDir, "downloads").apply { mkdirs() }
            cleanOldCacheFiles(downloadDir)

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
                val statusText = parseStatusLine(line, progress, etaInSeconds)
                onProgress(progress, etaInSeconds, statusText)
            }

            // Locate the generated final file, strictly avoiding partial / intermediate files
            val expectedExtension = format.extension
            val targetFile = downloadDir.listFiles { file ->
                file.isFile &&
                        file.name.startsWith("media_${uniqueFileId}") &&
                        !file.name.endsWith(".part", ignoreCase = true) &&
                        !file.name.endsWith(".ytdl", ignoreCase = true) &&
                        !file.name.endsWith(".temp", ignoreCase = true) &&
                        file.extension.equals(expectedExtension, ignoreCase = true)
            }?.firstOrNull() ?: downloadDir.listFiles { file ->
                file.isFile &&
                        file.name.startsWith("media_${uniqueFileId}") &&
                        !file.name.endsWith(".part", ignoreCase = true) &&
                        !file.name.endsWith(".ytdl", ignoreCase = true) &&
                        !file.name.endsWith(".temp", ignoreCase = true)
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
            YoutubeDLInitializer.ensureInitialized(context)
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
            if (YoutubeDLInitializer.isReady()) {
                YoutubeDL.getInstance().versionName(context) ?: "Dostępny"
            } else {
                "Inicjalizacja..."
            }
        } catch (_: Exception) {
            "Dostępny"
        }
    }

    private fun cleanOldCacheFiles(directory: File) {
        try {
            val now = System.currentTimeMillis()
            directory.listFiles()?.forEach { file ->
                if (file.isFile && (now - file.lastModified() > MAX_CACHE_AGE_MS)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune cache: ${e.message}")
        }
    }

    private fun parseStatusLine(line: String?, progress: Float, eta: Long): String {
        if (line.isNullOrBlank()) {
            return "${progress.toInt()}%"
        }

        // Inform user clearly when FFmpeg post-processing / conversion is executing
        if (line.contains("[Merger]") || line.contains("Merging formats")) {
            return "100% • Łączenie obrazu i dźwięku (FFmpeg)..."
        }
        if (line.contains("[ExtractAudio]") || (line.contains("Destination:") && line.contains(".mp3"))) {
            return "100% • Konwersja audio do formatu MP3..."
        }
        if (line.contains("[ffmpeg]")) {
            return "100% • Przetwarzanie multimediów (FFmpeg)..."
        }

        // Extract download speed if present in yt-dlp output, e.g., "12.3MiB/s"
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
