package com.example.mediadownloader.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediadownloader.data.AppPreferences
import com.example.mediadownloader.data.DownloadRepository
import com.example.mediadownloader.model.AudioFormat
import com.example.mediadownloader.model.AudioQuality
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.DownloadUiState
import com.example.mediadownloader.model.DownloadedItem
import com.example.mediadownloader.model.MediaMetadata
import com.example.mediadownloader.model.MediaType
import com.example.mediadownloader.service.DownloadService
import com.example.mediadownloader.updater.AppUpdateManager
import com.example.mediadownloader.updater.ReleaseInfo
import com.example.mediadownloader.updater.UpdateCheckResult
import com.example.mediadownloader.util.LinkParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val urlInput: String = "",
    val detectedPlatform: LinkParser.Platform = LinkParser.Platform.UNKNOWN,
    val selectedFormat: DownloadFormat = DownloadFormat.MP4,
    val downloadConfig: DownloadConfiguration = DownloadConfiguration(),
    val isFetchingMetadata: Boolean = false,
    val metadata: MediaMetadata? = null,
    val metadataError: String? = null,
    val engineVersion: String = "",
    val appVersion: String = "1.0",
    val engineUpdateMessage: String? = null,
    val isUpdatingEngine: Boolean = false,
    val isCheckingAppUpdate: Boolean = false,
    val isDownloadingAppUpdate: Boolean = false,
    val appUpdateProgress: Int = 0,
    val availableRelease: ReleaseInfo? = null,
    val appUpdateMessage: String? = null,
    val currentLanguage: String = "en",
    val autoUpdateEngine: Boolean = true,
    val autoUpdateApp: Boolean = true,
    val recentDownloads: List<DownloadedItem> = emptyList()
)

class MainScreenViewModel(
    private val repository: DownloadRepository,
    context: Context? = null
) : ViewModel() {

    private val preferences: AppPreferences? = context?.let { AppPreferences(it) }
    private val updateManager: AppUpdateManager? = context?.let { AppUpdateManager(it) }

    private val _uiState = MutableStateFlow(
        MainUiState(
            engineVersion = repository.getEngineVersion(),
            appVersion = updateManager?.getCurrentVersionName() ?: "1.0",
            currentLanguage = preferences?.selectedLanguage ?: "en",
            autoUpdateEngine = preferences?.autoUpdateEngine ?: true,
            autoUpdateApp = preferences?.autoUpdateApp ?: true
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadUiState> = DownloadService.downloadState

    private var metadataJob: Job? = null

    init {
        // Observe download state to automatically record successful downloads in history
        viewModelScope.launch {
            DownloadService.downloadState.collect { state ->
                when (state) {
                    is DownloadUiState.Success -> {
                        val item = DownloadedItem(
                            id = System.currentTimeMillis().toString(),
                            title = state.title,
                            uriString = state.uri.toString(),
                            format = state.format,
                            config = state.config,
                            sizeBytes = 0L,
                            timestamp = System.currentTimeMillis()
                        )
                        _uiState.update { current ->
                            current.copy(
                                recentDownloads = listOf(item) + current.recentDownloads.filter { it.uriString != item.uriString }
                            )
                        }
                    }
                    is DownloadUiState.PlaylistSuccess -> {
                        val item = DownloadedItem(
                            id = System.currentTimeMillis().toString(),
                            title = "${state.playlistTitle} (${state.successCount}/${state.totalCount})",
                            uriString = "",
                            format = DownloadFormat.MP3,
                            config = DownloadConfiguration(mediaType = MediaType.AUDIO, audioFormat = AudioFormat.MP3),
                            sizeBytes = 0L,
                            timestamp = System.currentTimeMillis()
                        )
                        _uiState.update { current ->
                            current.copy(
                                recentDownloads = listOf(item) + current.recentDownloads
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        // Silent auto-updates on launch
        viewModelScope.launch {
            try {
                repository.checkAndAutoUpdateEngineIfNeeded(force = false)
            } catch (_: Exception) {}

            try {
                if (preferences?.autoUpdateApp != false) {
                    checkForAppUpdates(manual = false)
                }
            } catch (_: Exception) {}
        }
    }

    fun onUrlChanged(newUrl: String) {
        val cleanUrl = LinkParser.extractUrl(newUrl) ?: newUrl.trim()
        val platform = LinkParser.detectPlatform(cleanUrl)

        _uiState.update {
            it.copy(
                urlInput = newUrl,
                detectedPlatform = platform,
                metadataError = null
            )
        }

        if (LinkParser.isSupported(cleanUrl) && cleanUrl != _uiState.value.metadata?.url) {
            fetchMetadataForUrl(cleanUrl)
        }
    }

    fun onPasteUrl(text: String?) {
        val extractedUrl = LinkParser.extractUrl(text) ?: text?.trim().orEmpty()
        onUrlChanged(extractedUrl)
    }

    fun handleSharedText(sharedText: String?) {
        val extractedUrl = LinkParser.extractUrl(sharedText) ?: return
        onUrlChanged(extractedUrl)
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
        _uiState.update {
            it.copy(
                downloadConfig = newConfig,
                selectedFormat = newConfig.asDownloadFormat
            )
        }
    }

    fun onClearInput() {
        metadataJob?.cancel()
        _uiState.update {
            it.copy(
                urlInput = "",
                detectedPlatform = LinkParser.Platform.UNKNOWN,
                metadata = null,
                metadataError = null
            )
        }
        DownloadService.resetState()
    }

    fun startDownload(context: Context) {
        val url = LinkParser.extractUrl(_uiState.value.urlInput) ?: _uiState.value.urlInput.trim()
        if (url.isBlank()) return

        val metadata = _uiState.value.metadata
        val config = _uiState.value.downloadConfig

        if (metadata?.isPlaylist == true && metadata.playlistInfo != null) {
            DownloadService.startPlaylistDownload(context, metadata.playlistInfo, config)
        } else {
            val title = metadata?.title ?: "SnapLoad_${System.currentTimeMillis()}"
            DownloadService.startDownload(context, url, config, title)
        }
    }

    fun cancelDownload(context: Context) {
        DownloadService.cancelDownload(context)
    }

    fun resetDownloadState() {
        DownloadService.resetState()
    }

    fun setLanguage(language: String) {
        preferences?.selectedLanguage = language
        _uiState.update { it.copy(currentLanguage = language) }
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

        _uiState.update { it.copy(isUpdatingEngine = true, engineUpdateMessage = null) }
        viewModelScope.launch {
            val result = repository.updateEngine()
            _uiState.update { current ->
                current.copy(
                    isUpdatingEngine = false,
                    engineVersion = repository.getEngineVersion(),
                    engineUpdateMessage = result.getOrNull() ?: ("Engine update failed: " + result.exceptionOrNull()?.message)
                )
            }
        }
    }

    fun clearEngineUpdateMessage() {
        _uiState.update { it.copy(engineUpdateMessage = null) }
    }

    fun checkForAppUpdates(manual: Boolean = true) {
        val mgr = updateManager ?: return
        if (_uiState.value.isCheckingAppUpdate) return

        _uiState.update { it.copy(isCheckingAppUpdate = true, appUpdateMessage = null) }
        viewModelScope.launch {
            val result = if (manual) mgr.checkForUpdates(force = true) else (mgr.checkAutoUpdateOnLaunch() ?: mgr.checkForUpdates(force = false))
            _uiState.update { current ->
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        current.copy(
                            isCheckingAppUpdate = false,
                            availableRelease = result.releaseInfo,
                            appUpdateMessage = null
                        )
                    }
                    is UpdateCheckResult.UpToDate -> {
                        current.copy(
                            isCheckingAppUpdate = false,
                            availableRelease = null,
                            appUpdateMessage = if (manual) "SnapLoad is up to date (${result.currentVersion})" else null
                        )
                    }
                    is UpdateCheckResult.Error -> {
                        current.copy(
                            isCheckingAppUpdate = false,
                            availableRelease = null,
                            appUpdateMessage = if (manual) "Update check failed: ${result.message}" else null
                        )
                    }
                }
            }
        }
    }

    fun downloadAndInstallUpdate(releaseInfo: ReleaseInfo) {
        val mgr = updateManager ?: return
        val apkUrl = releaseInfo.apkDownloadUrl ?: return

        val cachedApk = mgr.getDownloadedUpdateApk()
        if (cachedApk != null) {
            val installResult = mgr.promptInstallApk(cachedApk)
            if (installResult.isFailure) {
                val error = installResult.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        appUpdateMessage = if (error is SecurityException) {
                            "Please allow installing apps from SnapLoad in Settings, then tap Install again."
                        } else {
                            "Failed to start installer: ${error?.message}"
                        }
                    )
                }
            }
            return
        }

        _uiState.update { it.copy(isDownloadingAppUpdate = true, appUpdateProgress = 0) }

        viewModelScope.launch {
            val downloadResult = mgr.downloadApk(apkUrl) { percent ->
                _uiState.update { it.copy(appUpdateProgress = percent) }
            }

            _uiState.update { it.copy(isDownloadingAppUpdate = false) }

            if (downloadResult.isSuccess) {
                val apkFile = downloadResult.getOrThrow()
                val installResult = mgr.promptInstallApk(apkFile)
                if (installResult.isFailure) {
                    val error = installResult.exceptionOrNull()
                    _uiState.update {
                        it.copy(
                            appUpdateMessage = if (error is SecurityException) {
                                "Please allow installing apps from SnapLoad in Settings, then tap Install again."
                            } else {
                                "Failed to start installer: ${error?.message}"
                            }
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(appUpdateMessage = "Failed to download update: ${downloadResult.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(availableRelease = null) }
    }

    fun clearAppUpdateMessage() {
        _uiState.update { it.copy(appUpdateMessage = null) }
    }

    private fun fetchMetadataForUrl(url: String) {
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            _uiState.update { it.copy(isFetchingMetadata = true, metadataError = null) }
            val result = repository.fetchMetadata(url)
            _uiState.update { current ->
                if (result.isSuccess) {
                    val meta = result.getOrNull()
                    val autoAudioConfig = if (meta?.isAudioOnly == true && current.downloadConfig.mediaType == MediaType.VIDEO) {
                        DownloadConfiguration(
                            mediaType = MediaType.AUDIO,
                            audioFormat = AudioFormat.MP3,
                            audioQuality = AudioQuality.BEST
                        )
                    } else {
                        current.downloadConfig
                    }
                    current.copy(
                        isFetchingMetadata = false,
                        metadata = meta,
                        downloadConfig = autoAudioConfig,
                        selectedFormat = autoAudioConfig.asDownloadFormat,
                        metadataError = null
                    )
                } else {
                    current.copy(
                        isFetchingMetadata = false,
                        metadata = null,
                        metadataError = "Failed to load preview (download may still work): ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }
}
