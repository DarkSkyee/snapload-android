package com.example.mediadownloader.ui.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediadownloader.data.AppPreferences
import com.example.mediadownloader.data.DownloadRepository
import com.example.mediadownloader.model.AudioFormat
import com.example.mediadownloader.model.AudioQuality
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.DownloadItem
import com.example.mediadownloader.model.DownloadStatus
import com.example.mediadownloader.model.MediaMetadata
import com.example.mediadownloader.model.MediaType
import com.example.mediadownloader.model.PlaylistBatchSelection
import com.example.mediadownloader.model.PlaylistMetadata
import com.example.mediadownloader.model.VideoFormat
import com.example.mediadownloader.model.VideoQuality
import com.example.mediadownloader.service.DownloadService
import com.example.mediadownloader.updater.AppUpdateManager
import com.example.mediadownloader.updater.ReleaseInfo
import com.example.mediadownloader.updater.UpdateCheckResult
import com.example.mediadownloader.util.LinkParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class MainUiState(
    val urlInput: String = "",
    val detectedPlatform: LinkParser.Platform = LinkParser.Platform.UNKNOWN,
    val isFetchingMetadata: Boolean = false,
    val metadata: MediaMetadata? = null,
    val metadataError: String? = null,
    val selectedFormat: DownloadFormat = DownloadFormat.MP4,
    val downloadConfig: DownloadConfiguration = DownloadConfiguration(),
    val activeDownloads: List<DownloadItem> = emptyList(),
    val currentLanguage: String = "pl",
    val autoUpdateEngine: Boolean = true,
    val autoUpdateApp: Boolean = true,
    val engineVersion: String = "Ready",
    val appVersion: String = "1.0",
    val isUpdatingEngine: Boolean = false,
    val engineUpdateMessage: String? = null,
    val isCheckingAppUpdate: Boolean = false,
    val availableRelease: ReleaseInfo? = null,
    val isDownloadingAppUpdate: Boolean = false,
    val appUpdateProgress: Int = 0,
    val updateErrorMessage: String? = null,
    val downloadedApkFile: File? = null,
    val playlistSelection: PlaylistBatchSelection = PlaylistBatchSelection(),
    val isBatchDownloading: Boolean = false,
    val batchProgressText: String? = null
)

class MainScreenViewModel(
    private val repository: DownloadRepository,
    context: Context? = null
) : ViewModel() {

    companion object {
        private const val TAG = "MainScreenVM"
        private const val METADATA_DEBOUNCE_MS = 600L
    }

    private val preferences: AppPreferences? = context?.let { AppPreferences(it) }
    private val updateManager: AppUpdateManager? = context?.let { AppUpdateManager(it) }

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentLanguage = preferences?.selectedLanguage ?: "pl",
            autoUpdateEngine = preferences?.autoUpdateEngine ?: true,
            autoUpdateApp = preferences?.autoUpdateApp ?: true,
            engineVersion = repository.getEngineVersion(),
            appVersion = updateManager?.getCurrentVersionName() ?: "1.0"
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var metadataFetchJob: Job? = null

    init {
        // Observe active downloads from DownloadService
        viewModelScope.launch {
            DownloadService.activeDownloads.collect { downloads ->
                _uiState.update { it.copy(activeDownloads = downloads) }
            }
        }

        // Perform background engine / app auto-update checks on launch if enabled
        viewModelScope.launch {
            try {
                repository.checkAndAutoUpdateEngineIfNeeded(force = false)
            } catch (_: Throwable) {}

            try {
                if (preferences?.autoUpdateApp != false) {
                    checkForAppUpdates(manual = false)
                }
            } catch (_: Throwable) {}
        }
    }

    fun onUrlChanged(newUrl: String) {
        val trimmed = newUrl.trim()
        val platform = LinkParser.detectPlatform(trimmed)
        _uiState.update {
            it.copy(
                urlInput = newUrl,
                detectedPlatform = platform,
                metadata = null,
                metadataError = null,
                playlistSelection = PlaylistBatchSelection()
            )
        }

        metadataFetchJob?.cancel()

        if (trimmed.isNotBlank() && LinkParser.isValidUrl(trimmed)) {
            metadataFetchJob = viewModelScope.launch {
                delay(METADATA_DEBOUNCE_MS)
                fetchMetadata(trimmed)
            }
        }
    }

    fun handleSharedText(sharedText: String) {
        val extractedUrl = LinkParser.extractUrl(sharedText)
        if (!extractedUrl.isNullOrBlank()) {
            onUrlChanged(extractedUrl)
        } else {
            onUrlChanged(sharedText.trim())
        }
    }

    fun onClearInput() {
        metadataFetchJob?.cancel()
        _uiState.update {
            it.copy(
                urlInput = "",
                detectedPlatform = LinkParser.Platform.UNKNOWN,
                isFetchingMetadata = false,
                metadata = null,
                metadataError = null,
                playlistSelection = PlaylistBatchSelection()
            )
        }
    }

    fun onFormatSelected(format: DownloadFormat) {
        val newConfig = DownloadConfiguration.fromDownloadFormat(format)
        _uiState.update {
            it.copy(
                selectedFormat = format,
                downloadConfig = newConfig
            )
        }
    }

    fun onConfigChanged(newConfig: DownloadConfiguration) {
        val matchingFormat = when (newConfig.mediaType) {
            MediaType.VIDEO -> when (newConfig.videoFormat) {
                VideoFormat.MP4 -> DownloadFormat.MP4
                VideoFormat.MKV -> DownloadFormat.MKV
                VideoFormat.WEBM -> DownloadFormat.WEBM
            }
            MediaType.AUDIO -> when (newConfig.audioFormat) {
                AudioFormat.MP3 -> DownloadFormat.MP3
                AudioFormat.M4A -> DownloadFormat.M4A
                AudioFormat.FLAC -> DownloadFormat.FLAC
                AudioFormat.WAV -> DownloadFormat.WAV
                AudioFormat.OPUS -> DownloadFormat.OPUS
            }
        }

        _uiState.update {
            it.copy(
                downloadConfig = newConfig,
                selectedFormat = matchingFormat
            )
        }
    }

    fun togglePlaylistItemSelection(id: String) {
        _uiState.update { state ->
            val currentSelected = state.playlistSelection.selectedItemIds
            val newSelected = if (currentSelected.contains(id)) {
                currentSelected - id
            } else {
                currentSelected + id
            }
            state.copy(playlistSelection = state.playlistSelection.copy(selectedItemIds = newSelected))
        }
    }

    fun selectAllPlaylistItems() {
        val allIds = _uiState.value.metadata?.playlistInfo?.items?.map { it.id }?.toSet() ?: emptySet()
        _uiState.update { state ->
            state.copy(playlistSelection = state.playlistSelection.copy(selectedItemIds = allIds))
        }
    }

    fun deselectAllPlaylistItems() {
        _uiState.update { state ->
            state.copy(playlistSelection = state.playlistSelection.copy(selectedItemIds = emptySet()))
        }
    }

    fun startDownload(context: Context) {
        val state = _uiState.value
        val url = state.urlInput.trim()
        if (url.isBlank()) return

        val metadata = state.metadata
        if (metadata != null && metadata.isPlaylist && metadata.playlistInfo != null) {
            startBatchPlaylistDownload(context, metadata.playlistInfo)
            return
        }

        val title = metadata?.title ?: "Media_${System.currentTimeMillis()}"
        val author = metadata?.author
        val thumbnailUrl = metadata?.thumbnailUrl

        DownloadService.startDownload(
            context = context,
            url = url,
            config = state.downloadConfig,
            title = title,
            author = author,
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun startBatchPlaylistDownload(context: Context, playlistInfo: PlaylistMetadata) {
        val selectedIds = _uiState.value.playlistSelection.selectedItemIds
        val itemsToDownload = if (selectedIds.isEmpty()) {
            playlistInfo.items
        } else {
            playlistInfo.items.filter { selectedIds.contains(it.id) }
        }

        if (itemsToDownload.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBatchDownloading = true) }
            for ((idx, item) in itemsToDownload.withIndex()) {
                _uiState.update {
                    it.copy(batchProgressText = "Queueing ${idx + 1}/${itemsToDownload.size}: ${item.title}")
                }
                DownloadService.startDownload(
                    context = context,
                    url = item.sourceUrl,
                    config = _uiState.value.downloadConfig,
                    title = item.title,
                    author = item.author,
                    thumbnailUrl = item.thumbnailUrl
                )
                delay(400) // Small spacing between starting tasks
            }
            _uiState.update {
                it.copy(
                    isBatchDownloading = false,
                    batchProgressText = null
                )
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        DownloadService.cancelDownload(downloadId)
    }

    fun retryDownload(context: Context, item: DownloadItem) {
        DownloadService.startDownload(
            context = context,
            url = item.url,
            config = item.config,
            title = item.title,
            author = item.author,
            thumbnailUrl = item.thumbnailUrl
        )
    }

    fun removeDownload(downloadId: String) {
        DownloadService.removeDownload(downloadId)
    }

    fun clearCompletedDownloads() {
        DownloadService.clearCompleted()
    }

    fun setLanguage(languageCode: String) {
        preferences?.selectedLanguage = languageCode
        _uiState.update { it.copy(currentLanguage = languageCode) }
    }

    fun setAutoUpdateEngine(enabled: Boolean) {
        preferences?.autoUpdateEngine = enabled
        _uiState.update { it.copy(autoUpdateEngine = enabled) }
    }

    fun setAutoUpdateApp(enabled: Boolean) {
        preferences?.autoUpdateApp = enabled
        _uiState.update { it.copy(autoUpdateApp = enabled) }
    }

    fun updateEngine() {
        if (_uiState.value.isUpdatingEngine) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingEngine = true, engineUpdateMessage = null) }
            val result = repository.updateEngine()
            _uiState.update {
                it.copy(
                    isUpdatingEngine = false,
                    engineUpdateMessage = result.getOrNull() ?: result.exceptionOrNull()?.message,
                    engineVersion = repository.getEngineVersion()
                )
            }
        }
    }

    fun checkForAppUpdates(manual: Boolean = true) {
        val manager = updateManager ?: return
        if (_uiState.value.isCheckingAppUpdate) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingAppUpdate = true, updateErrorMessage = null) }
            val checkResult = manager.checkForUpdates(force = manual)
            when (checkResult) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _uiState.update {
                        it.copy(
                            isCheckingAppUpdate = false,
                            availableRelease = checkResult.releaseInfo
                        )
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    _uiState.update {
                        it.copy(
                            isCheckingAppUpdate = false,
                            availableRelease = null
                        )
                    }
                }
                is UpdateCheckResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isCheckingAppUpdate = false,
                            updateErrorMessage = if (manual) checkResult.message else null
                        )
                    }
                }
            }
        }
    }

    fun startDownloadingAppUpdate(releaseInfo: ReleaseInfo) {
        val manager = updateManager ?: return
        val apkUrl = releaseInfo.apkDownloadUrl ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingAppUpdate = true,
                    appUpdateProgress = 0,
                    updateErrorMessage = null
                )
            }

            val result = manager.downloadApk(apkUrl) { pct ->
                _uiState.update { it.copy(appUpdateProgress = pct) }
            }

            result.onSuccess { apkFile ->
                _uiState.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        downloadedApkFile = apkFile
                    )
                }
                manager.promptInstallApk(apkFile)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        updateErrorMessage = "Update download failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(availableRelease = null, updateErrorMessage = null) }
    }

    private suspend fun fetchMetadata(url: String) {
        _uiState.update { it.copy(isFetchingMetadata = true, metadataError = null) }
        val result = repository.fetchMetadata(url)
        result.onSuccess { meta ->
            _uiState.update {
                val updatedConfig = if (meta.isAudioOnly) {
                    it.downloadConfig.copy(
                        mediaType = MediaType.AUDIO,
                        audioFormat = AudioFormat.MP3
                    )
                } else {
                    it.downloadConfig
                }

                val allIds = meta.playlistInfo?.items?.map { item -> item.id }?.toSet() ?: emptySet()
                val playlistSelection = if (meta.isPlaylist) {
                    PlaylistBatchSelection(selectedItemIds = allIds)
                } else {
                    PlaylistBatchSelection()
                }

                it.copy(
                    isFetchingMetadata = false,
                    metadata = meta,
                    metadataError = null,
                    downloadConfig = updatedConfig,
                    selectedFormat = if (meta.isAudioOnly) DownloadFormat.MP3 else it.selectedFormat,
                    playlistSelection = playlistSelection
                )
            }
        }.onFailure { error ->
            Log.e(TAG, "Metadata extraction failed: ${error.message}", error)
            _uiState.update {
                it.copy(
                    isFetchingMetadata = false,
                    metadataError = error.message ?: "Failed to load media info"
                )
            }
        }
    }
}
