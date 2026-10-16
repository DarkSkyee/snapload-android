package com.example.mediadownloader.model

import android.net.Uri
import com.example.mediadownloader.util.LinkParser

enum class DownloadFormat(val displayName: String, val extension: String, val mimeType: String) {
    MP4("MP4 (Wideo HD)", "mp4", "video/mp4"),
    MP3("MP3 (Tylko dźwięk)", "mp3", "audio/mpeg")
}

data class MediaMetadata(
    val url: String,
    val title: String,
    val author: String = "",
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0,
    val platform: LinkParser.Platform = LinkParser.Platform.UNKNOWN
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
    val format: DownloadFormat,
    val sizeBytes: Long,
    val timestamp: Long
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
        val format: DownloadFormat
    ) : DownloadUiState
    data class Error(val message: String) : DownloadUiState
}
