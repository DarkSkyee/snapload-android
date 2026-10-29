package com.example.mediadownloader.data

import android.content.Context
import android.util.Log
import com.example.mediadownloader.model.AudioFormat
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.MediaMetadata
import com.example.mediadownloader.model.MediaType
import com.example.mediadownloader.model.VideoFormat
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
    suspend fun downloadMedia(
        url: String,
        config: DownloadConfiguration,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<File>
    suspend fun cancelDownload(downloadId: String): Boolean
    suspend fun updateEngine(): Result<String>
    suspend fun checkAndAutoUpdateEngineIfNeeded(force: Boolean = false): Result<String>?
    fun getEngineVersion(): String
}

class DefaultDownloadRepository(private val context: Context) : DownloadRepository {

    companion object {
        private const val TAG = "DownloadRepo"
        private const val MAX_CACHE_AGE_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val ENGINE_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val preferences = AppPreferences(context)

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
                title = videoInfo.title ?: "SnapLoad_${System.currentTimeMillis()}",
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
    ): Result<File> {
        return downloadMedia(url, DownloadConfiguration.fromDownloadFormat(format), downloadId, onProgress)
    }

    override suspend fun downloadMedia(
        url: String,
        config: DownloadConfiguration,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            YoutubeDLInitializer.ensureInitialized(context)
            val downloadDir = File(context.cacheDir, "downloads").apply { mkdirs() }
            cleanOldCacheFiles(downloadDir)

            val uniqueFileId = UUID.randomUUID().toString().substring(0, 8)
            val outputTemplate = "${downloadDir.absolutePath}/snapload_${uniqueFileId}.%(ext)s"

            val request = YoutubeDLRequest(url).apply {
                addOption("-o", outputTemplate)
                addOption("--no-mtime")
                addOption("--no-playlist")

                if (config.mediaType == MediaType.VIDEO) {
                    addOption("-f", config.videoQuality.ytdlFormatSelector)
                    addOption("--merge-output-format", config.videoFormat.extension)
                    addOption("--remux-video", config.videoFormat.extension)
                } else {
                    addOption("-x")
                    addOption("--audio-format", config.audioFormat.extension)
                    addOption("--audio-quality", config.audioQuality.ytdlQuality)
                }
            }

            Log.d(TAG, "Executing download $downloadId for $url ($config)")

            YoutubeDL.getInstance().execute(request, downloadId) { progress, etaInSeconds, line ->
                val statusText = parseStatusLine(line, progress, etaInSeconds)
                onProgress(progress, etaInSeconds, statusText)
            }

            // Locate final file avoiding temp/part/auxiliary files
            val expectedExtension = config.extension
            val exactFile = File(downloadDir, "snapload_${uniqueFileId}.$expectedExtension")

            val targetFile = if (exactFile.exists() && exactFile.length() > 0L) {
                exactFile
            } else {
                val candidateFiles = downloadDir.listFiles { file ->
                    file.isFile &&
                            file.name.startsWith("snapload_${uniqueFileId}") &&
                            !file.name.endsWith(".part", ignoreCase = true) &&
                            !file.name.endsWith(".ytdl", ignoreCase = true) &&
                            !file.name.endsWith(".temp", ignoreCase = true) &&
                            !file.name.endsWith(".aria2", ignoreCase = true) &&
                            !file.name.endsWith(".jpg", ignoreCase = true) &&
                            !file.name.endsWith(".jpeg", ignoreCase = true) &&
                            !file.name.endsWith(".png", ignoreCase = true) &&
                            !file.name.endsWith(".webp", ignoreCase = true)
                } ?: emptyArray()

                candidateFiles.firstOrNull { it.extension.equalsIgnoreCase(expectedExtension) && it.length() > 0L }
                    ?: candidateFiles.filter { it.length() > 0L }.maxByOrNull { it.length() }
            }

            if (targetFile != null && targetFile.exists() && targetFile.length() > 0L) {
                Log.i(TAG, "Download finished successfully: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                Result.success(targetFile)
            } else {
                val errorMsg = "Download completed, but output file not found in ${downloadDir.absolutePath}"
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
            val newVersion = YoutubeDL.getInstance().versionName(context) ?: "unknown"
            preferences.lastEngineUpdateCheck = System.currentTimeMillis()
            Result.success("yt-dlp engine updated ($status). Version: $newVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update yt-dlp engine: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun checkAndAutoUpdateEngineIfNeeded(force: Boolean): Result<String>? {
        if (!force && !preferences.autoUpdateEngine) return null

        val now = System.currentTimeMillis()
        val timeSinceLastCheck = now - preferences.lastEngineUpdateCheck

        if (force || timeSinceLastCheck > ENGINE_UPDATE_INTERVAL_MS || preferences.lastEngineUpdateCheck == 0L) {
            return updateEngine()
        }
        return null
    }

    override fun getEngineVersion(): String {
        return try {
            if (YoutubeDLInitializer.isReady()) {
                YoutubeDL.getInstance().versionName(context) ?: "Ready"
            } else {
                "Initializing..."
            }
        } catch (_: Exception) {
            "Ready"
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
            return "100% • Merging video & audio (FFmpeg)..."
        }
        if (line.contains("[ExtractAudio]") || (line.contains("Destination:") && line.contains(".mp3"))) {
            return "100% • Converting audio track (FFmpeg)..."
        }
        if (line.contains("[ffmpeg]")) {
            return "100% • Processing media (FFmpeg)..."
        }

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

    private fun String.equalsIgnoreCase(other: String): Boolean =
        this.equals(other, ignoreCase = true)
}
