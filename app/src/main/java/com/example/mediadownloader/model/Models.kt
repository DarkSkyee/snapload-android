package com.example.mediadownloader.model

import android.net.Uri
import com.example.mediadownloader.util.LinkParser

enum class MediaType {
    VIDEO, AUDIO
}

enum class VideoFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String
) {
    MP4("MP4", "mp4", "video/mp4"),
    MKV("MKV", "mkv", "video/x-matroska"),
    WEBM("WebM", "webm", "video/webm")
}

enum class AudioFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String
) {
    MP3("MP3", "mp3", "audio/mpeg"),
    M4A("M4A", "m4a", "audio/mp4"),
    FLAC("FLAC", "flac", "audio/flac"),
    WAV("WAV", "wav", "audio/x-wav"),
    OPUS("OPUS", "opus", "audio/opus")
}

enum class VideoQuality(
    val displayName: String,
    val maxHeight: Int?,
    val ytdlFormatSelector: String
) {
    BEST("Best (Original / 4K)", null, "bestvideo+bestaudio/best"),
    P1080("1080p FHD", 1080, "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"),
    P720("720p HD", 720, "bestvideo[height<=720]+bestaudio/best[height<=720]/best"),
    P480("480p SD", 480, "bestvideo[height<=480]+bestaudio/best[height<=480]/best")
}

enum class AudioQuality(
    val displayName: String,
    val ytdlQuality: String
) {
    BEST("320 kbps (Best)", "0"),
    HIGH("256 kbps", "2"),
    MEDIUM("192 kbps", "4"),
    LOW("128 kbps", "6")
}

data class DownloadConfiguration(
    val mediaType: MediaType = MediaType.VIDEO,
    val videoFormat: VideoFormat = VideoFormat.MP4,
    val videoQuality: VideoQuality = VideoQuality.BEST,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val audioQuality: AudioQuality = AudioQuality.BEST
) {
    val extension: String
        get() = if (mediaType == MediaType.VIDEO) videoFormat.extension else audioFormat.extension

    val mimeType: String
        get() = if (mediaType == MediaType.VIDEO) videoFormat.mimeType else audioFormat.mimeType

    val formatDisplayName: String
        get() = if (mediaType == MediaType.VIDEO) videoFormat.displayName else audioFormat.displayName

    val qualityDisplayName: String
        get() = if (mediaType == MediaType.VIDEO) videoQuality.displayName else audioQuality.displayName

    val asDownloadFormat: DownloadFormat
        get() = when {
            mediaType == MediaType.VIDEO && videoFormat == VideoFormat.MKV -> DownloadFormat.MKV
            mediaType == MediaType.VIDEO && videoFormat == VideoFormat.WEBM -> DownloadFormat.WEBM
            mediaType == MediaType.VIDEO -> DownloadFormat.MP4
            mediaType == MediaType.AUDIO && audioFormat == AudioFormat.M4A -> DownloadFormat.M4A
            mediaType == MediaType.AUDIO && audioFormat == AudioFormat.FLAC -> DownloadFormat.FLAC
            mediaType == MediaType.AUDIO && audioFormat == AudioFormat.WAV -> DownloadFormat.WAV
            mediaType == MediaType.AUDIO && audioFormat == AudioFormat.OPUS -> DownloadFormat.OPUS
            else -> DownloadFormat.MP3
        }

    companion object {
        fun fromDownloadFormat(format: DownloadFormat): DownloadConfiguration {
            return when (format) {
                DownloadFormat.MP4 -> DownloadConfiguration(MediaType.VIDEO, VideoFormat.MP4)
                DownloadFormat.MKV -> DownloadConfiguration(MediaType.VIDEO, VideoFormat.MKV)
                DownloadFormat.WEBM -> DownloadConfiguration(MediaType.VIDEO, VideoFormat.WEBM)
                DownloadFormat.MP3 -> DownloadConfiguration(MediaType.AUDIO, audioFormat = AudioFormat.MP3)
                DownloadFormat.M4A -> DownloadConfiguration(MediaType.AUDIO, audioFormat = AudioFormat.M4A)
                DownloadFormat.FLAC -> DownloadConfiguration(MediaType.AUDIO, audioFormat = AudioFormat.FLAC)
                DownloadFormat.WAV -> DownloadConfiguration(MediaType.AUDIO, audioFormat = AudioFormat.WAV)
                DownloadFormat.OPUS -> DownloadConfiguration(MediaType.AUDIO, audioFormat = AudioFormat.OPUS)
            }
        }
    }
}

enum class DownloadFormat(val displayName: String, val extension: String, val mimeType: String) {
    MP4("MP4", "mp4", "video/mp4"),
    MKV("MKV", "mkv", "video/x-matroska"),
    WEBM("WebM", "webm", "video/webm"),
    MP3("MP3", "mp3", "audio/mpeg"),
    M4A("M4A", "m4a", "audio/mp4"),
    FLAC("FLAC", "flac", "audio/flac"),
    WAV("WAV", "wav", "audio/x-wav"),
    OPUS("OPUS", "opus", "audio/opus");

    val isVideo: Boolean
        get() = this == MP4 || this == MKV || this == WEBM
}

enum class PlaylistItemStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class PlaylistItem(
    val id: String,
    val index: Int,
    val title: String,
    val author: String = "",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val sourceUrl: String = "",
    val status: PlaylistItemStatus = PlaylistItemStatus.PENDING,
    val progress: Float = 0f,
    val error: String? = null
)

data class PlaylistMetadata(
    val title: String,
    val author: String = "",
    val thumbnailUrl: String? = null,
    val itemCount: Int = 0,
    val items: List<PlaylistItem> = emptyList()
)

data class MediaMetadata(
    val url: String,
    val title: String,
    val author: String = "",
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0,
    val platform: LinkParser.Platform = LinkParser.Platform.UNKNOWN,
    val isPlaylist: Boolean = false,
    val playlistInfo: PlaylistMetadata? = null,
    val isAudioOnly: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return ""
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
}

data class DownloadedItem(
    val id: String,
    val title: String,
    val uriString: String,
    val format: DownloadFormat = DownloadFormat.MP4,
    val config: DownloadConfiguration = DownloadConfiguration(),
    val sizeBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data object LoadingMetadata : DownloadUiState
    data class Downloading(
        val progress: Float,
        val speed: String,
        val etaSeconds: Long
    ) : DownloadUiState
    data class Processing(val stage: String) : DownloadUiState
    data object SavingToGallery : DownloadUiState
    data class Success(
        val title: String,
        val uri: Uri,
        val format: DownloadFormat = DownloadFormat.MP4,
        val config: DownloadConfiguration = DownloadConfiguration(),
        val fallbackNotice: String? = null
    ) : DownloadUiState

    data class PlaylistDownloading(
        val playlistTitle: String,
        val currentIndex: Int,
        val totalCount: Int,
        val currentItemTitle: String,
        val currentItemProgress: Float,
        val overallProgress: Float,
        val speed: String = "",
        val etaSeconds: Long = 0L,
        val items: List<PlaylistItem> = emptyList()
    ) : DownloadUiState

    data class PlaylistSuccess(
        val playlistTitle: String,
        val totalCount: Int,
        val successCount: Int,
        val failedCount: Int,
        val items: List<PlaylistItem> = emptyList(),
        val fallbackNotice: String? = null
    ) : DownloadUiState

    data class Error(val message: String) : DownloadUiState
}
