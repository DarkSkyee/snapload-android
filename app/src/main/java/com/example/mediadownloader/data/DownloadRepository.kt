package com.example.mediadownloader.data

import android.content.Context
import android.util.Log
import com.example.mediadownloader.R
import com.example.mediadownloader.model.AudioFormat
import com.example.mediadownloader.model.AudioQuality
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.MediaMetadata
import com.example.mediadownloader.model.MediaType
import com.example.mediadownloader.model.PlaylistItem
import com.example.mediadownloader.model.PlaylistItemStatus
import com.example.mediadownloader.model.PlaylistMetadata
import com.example.mediadownloader.model.VideoFormat
import com.example.mediadownloader.model.VideoQuality
import com.example.mediadownloader.util.LinkParser
import com.example.mediadownloader.util.SpotifyHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class DownloadFileResult(
    val file: File,
    val effectiveConfig: DownloadConfiguration,
    val fallbackNotice: String? = null
)

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

    suspend fun downloadMediaDetailed(
        url: String,
        config: DownloadConfiguration,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<DownloadFileResult> {
        val res = downloadMedia(url, config, downloadId, onProgress)
        return res.map { DownloadFileResult(it, config, null) }
    }

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
            val isPlaylistUrl = LinkParser.isPlaylist(url)

            // 1. Handle Spotify links
            if (platform == LinkParser.Platform.SPOTIFY) {
                if (isPlaylistUrl) {
                    val spotifyPlaylist = SpotifyHelper.fetchPlaylist(url)
                    if (spotifyPlaylist != null && spotifyPlaylist.tracks.isNotEmpty()) {
                        val items = spotifyPlaylist.tracks.mapIndexed { idx, track ->
                            PlaylistItem(
                                id = "sp_$idx",
                                index = idx + 1,
                                title = track.title,
                                author = track.artist,
                                durationSeconds = track.durationSeconds,
                                thumbnailUrl = track.thumbnailUrl ?: spotifyPlaylist.thumbnailUrl,
                                sourceUrl = track.searchQuery
                            )
                        }
                        val playlistMeta = PlaylistMetadata(
                            title = spotifyPlaylist.title,
                            author = spotifyPlaylist.author,
                            thumbnailUrl = spotifyPlaylist.thumbnailUrl,
                            itemCount = items.size,
                            items = items
                        )
                        return@withContext Result.success(
                            MediaMetadata(
                                url = url,
                                title = spotifyPlaylist.title,
                                author = spotifyPlaylist.author,
                                thumbnailUrl = spotifyPlaylist.thumbnailUrl,
                                durationSeconds = items.sumOf { it.durationSeconds },
                                platform = LinkParser.Platform.SPOTIFY,
                                isPlaylist = true,
                                playlistInfo = playlistMeta,
                                isAudioOnly = true
                            )
                        )
                    }
                }

                // Single Spotify track
                val spotifyTrack = SpotifyHelper.fetchTrack(url)
                if (spotifyTrack != null) {
                    return@withContext Result.success(
                        MediaMetadata(
                            url = url,
                            title = "${spotifyTrack.artist} - ${spotifyTrack.title}",
                            author = spotifyTrack.artist,
                            thumbnailUrl = spotifyTrack.thumbnailUrl,
                            durationSeconds = spotifyTrack.durationSeconds,
                            platform = LinkParser.Platform.SPOTIFY,
                            isPlaylist = false,
                            isAudioOnly = true
                        )
                    )
                }
            }

            // 2. Handle YouTube / YT Music Playlist
            if (isPlaylistUrl && (platform == LinkParser.Platform.YOUTUBE || platform == LinkParser.Platform.YOUTUBE_MUSIC || platform == LinkParser.Platform.OTHER)) {
                try {
                    val playlistReq = YoutubeDLRequest(url).apply {
                        addOption("--flat-playlist")
                        addOption("-J")
                        addOption("--no-warnings")
                    }
                    val playlistResp = YoutubeDL.getInstance().execute(playlistReq)
                    val json = JSONObject(playlistResp.out)
                    val plTitle = json.optString("title", "YouTube Playlist")
                    val plUploader = json.optString("uploader", json.optString("channel", ""))
                    val entries = json.optJSONArray("entries")
                    val items = mutableListOf<PlaylistItem>()

                    if (entries != null) {
                        for (i in 0 until entries.length()) {
                            val entry = entries.optJSONObject(i) ?: continue
                            val id = entry.optString("id", "$i")
                            val title = entry.optString("title", "Item ${i + 1}")
                            val uploader = entry.optString("uploader", plUploader)
                            val duration = entry.optLong("duration", 0L)
                            val thumb = entry.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                            val itemUrl = entry.optString("url").let { u ->
                                if (u.startsWith("http")) u else "https://www.youtube.com/watch?v=$id"
                            }
                            items.add(
                                PlaylistItem(
                                    id = id,
                                    index = i + 1,
                                    title = title,
                                    author = uploader,
                                    durationSeconds = duration,
                                    thumbnailUrl = thumb,
                                    sourceUrl = itemUrl
                                )
                            )
                        }
                    }

                    if (items.isNotEmpty()) {
                        val firstThumb = items.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                        val playlistMeta = PlaylistMetadata(
                            title = plTitle,
                            author = plUploader,
                            thumbnailUrl = firstThumb,
                            itemCount = items.size,
                            items = items
                        )
                        return@withContext Result.success(
                            MediaMetadata(
                                url = url,
                                title = plTitle,
                                author = plUploader,
                                thumbnailUrl = firstThumb,
                                durationSeconds = items.sumOf { it.durationSeconds },
                                platform = platform,
                                isPlaylist = true,
                                playlistInfo = playlistMeta,
                                isAudioOnly = (platform == LinkParser.Platform.YOUTUBE_MUSIC && !url.contains("watch"))
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Flat playlist extraction failed, falling back to standard getInfo: ${e.message}")
                }
            }

            // 3. Single video / media item info
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
                platform = platform,
                isAudioOnly = platform == LinkParser.Platform.SPOTIFY
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
        val detailed = downloadMediaDetailed(url, DownloadConfiguration.fromDownloadFormat(format), downloadId, onProgress)
        return detailed.map { it.file }
    }

    override suspend fun downloadMedia(
        url: String,
        config: DownloadConfiguration,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<File> {
        val detailed = downloadMediaDetailed(url, config, downloadId, onProgress)
        return detailed.map { it.file }
    }

    override suspend fun downloadMediaDetailed(
        url: String,
        config: DownloadConfiguration,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<DownloadFileResult> = withContext(Dispatchers.IO) {
        try {
            YoutubeDLInitializer.ensureInitialized(context)
            val downloadDir = File(context.cacheDir, "downloads").apply { mkdirs() }
            cleanOldCacheFiles(downloadDir)

            val platform = LinkParser.detectPlatform(url)

            // Resolve target download URL and query
            var targetUrl = url
            var isAudioOnlySource = false

            if (platform == LinkParser.Platform.SPOTIFY) {
                isAudioOnlySource = true
                val spTrack = SpotifyHelper.fetchTrack(url)
                targetUrl = spTrack?.searchQuery ?: "ytsearch1:$url audio"
            }

            // Fallback requirement:
            // "Jeśli wybierzesz mp4 a dany plik nie ma mp4 to pobierze mp3 i cie o tym powiadomii"
            var effectiveConfig = config
            var fallbackNotice: String? = null

            if (config.mediaType == MediaType.VIDEO && isAudioOnlySource) {
                effectiveConfig = DownloadConfiguration(
                    mediaType = MediaType.AUDIO,
                    audioFormat = AudioFormat.MP3,
                    audioQuality = AudioQuality.BEST
                )
                fallbackNotice = context.getString(R.string.fallback_audio_only_to_mp3)
                Log.i(TAG, "Audio-only source detected while video was chosen. Falling back to MP3.")
            }

            // Ensure FFmpeg is ready if required for audio extraction or video merging
            val needsFfmpeg = effectiveConfig.mediaType == MediaType.AUDIO && effectiveConfig.audioFormat == AudioFormat.MP3
            if (needsFfmpeg && !FFmpegEngineManager.isInstalled(context)) {
                onProgress(5f, 0L, "Preparing media converter...")
                FFmpegEngineManager.ensureFFmpegInstalled(context) { pct ->
                    onProgress(pct.toFloat().coerceIn(0f, 90f), 0L, "Setting up converter ($pct%)...")
                }
            }

            val uniqueFileId = UUID.randomUUID().toString().substring(0, 8)
            val outputTemplate = "${downloadDir.absolutePath}/snapload_${uniqueFileId}.%(ext)s"

            val request = YoutubeDLRequest(targetUrl).apply {
                addOption("-o", outputTemplate)
                addOption("--no-mtime")
                addOption("--no-playlist")

                if (effectiveConfig.mediaType == MediaType.VIDEO) {
                    addOption("-f", effectiveConfig.videoQuality.ytdlFormatSelector)
                    addOption("--merge-output-format", effectiveConfig.videoFormat.extension)
                    addOption("--remux-video", effectiveConfig.videoFormat.extension)
                } else {
                    addOption("-x")
                    addOption("--audio-format", effectiveConfig.audioFormat.extension)
                    addOption("--audio-quality", effectiveConfig.audioQuality.ytdlQuality)
                }
            }

            Log.d(TAG, "Executing download $downloadId for $targetUrl ($effectiveConfig)")

            var executionFailed = false
            try {
                YoutubeDL.getInstance().execute(request, downloadId) { progress, etaInSeconds, line ->
                    val statusText = parseStatusLine(line, progress, etaInSeconds)
                    onProgress(progress, etaInSeconds, statusText)
                }
            } catch (e: Exception) {
                // Secondary fallback check: If video download failed due to no matching video streams
                if (effectiveConfig.mediaType == MediaType.VIDEO && (e.message?.contains("format", ignoreCase = true) == true || e.message?.contains("Requested format", ignoreCase = true) == true)) {
                    Log.w(TAG, "Video format failed. Retrying with MP3 fallback: ${e.message}")
                    executionFailed = true
                } else {
                    throw e
                }
            }

            if (executionFailed) {
                effectiveConfig = DownloadConfiguration(
                    mediaType = MediaType.AUDIO,
                    audioFormat = AudioFormat.MP3,
                    audioQuality = AudioQuality.BEST
                )
                fallbackNotice = context.getString(R.string.fallback_audio_only_to_mp3)

                val retryRequest = YoutubeDLRequest(targetUrl).apply {
                    addOption("-o", outputTemplate)
                    addOption("--no-mtime")
                    addOption("--no-playlist")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
                YoutubeDL.getInstance().execute(retryRequest, downloadId) { progress, etaInSeconds, line ->
                    val statusText = parseStatusLine(line, progress, etaInSeconds)
                    onProgress(progress, etaInSeconds, statusText)
                }
            }

            // Locate final file avoiding temp/part/auxiliary files
            val expectedExtension = effectiveConfig.extension
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
                Result.success(DownloadFileResult(targetFile, effectiveConfig, fallbackNotice))
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
