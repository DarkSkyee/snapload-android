package com.example.mediadownloader.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediadownloader.data.AppPreferences
import com.example.mediadownloader.data.DownloadRepository
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.DownloadUiState
import com.example.mediadownloader.model.DownloadedItem
import com.example.mediadownloader.model.MediaMetadata
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
                if (state is DownloadUiState.Success) {
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
            }
        }

        // Silent auto-updates on launch
        viewModelScope.launch {
            try {
                repository.checkAndAutoUpdateEngineIfNeeded(force = false)
            } catch (_: Exception) {}

            try {
                val autoUpdateResult = updateManager?.checkAutoUpdateOnLaunch()
                if (autoUpdateResult is UpdateCheckResult.UpdateAvailable) {
                    _uiState.update { it.copy(availableRelease = autoUpdateResult.releaseInfo) }
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
        metadataJob?.cancel()\n        _uiState.update {\n            it.copy(\n                urlInput = \"\",\n                detectedPlatform = LinkParser.Platform.UNKNOWN,\n                metadata = null,\n                metadataError = null\n            )\n        }\n        DownloadService.resetState()\n    }\n\n    fun startDownload(context: Context) {\n        val url = LinkParser.extractUrl(_uiState.value.urlInput) ?: _uiState.value.urlInput.trim()\n        if (url.isBlank()) return\n\n        val title = _uiState.value.metadata?.title\n            ?: \"SnapLoad_${System.currentTimeMillis()}\"\n        val config = _uiState.value.downloadConfig\n\n        DownloadService.startDownload(context, url, config, title)\n    }\n\n    fun cancelDownload(context: Context) {\n        DownloadService.cancelDownload(context)\n    }\n\n    fun resetDownloadState() {\n        DownloadService.resetState()\n    }\n\n    fun updateEngine() {\n        if (_uiState.value.isUpdatingEngine) return\n        _uiState.update { it.copy(isUpdatingEngine = true, engineUpdateMessage = null) }\n        viewModelScope.launch {\n            val result = repository.updateEngine()\n            _uiState.update {\n                it.copy(\n                    isUpdatingEngine = false,\n                    engineUpdateMessage = result.getOrNull() ?: \"Error: ${result.exceptionOrNull()?.message}\",\n                    engineVersion = repository.getEngineVersion()\n                )\n            }\n        }\n    }\n\n    fun clearEngineUpdateMessage() {\n        _uiState.update { it.copy(engineUpdateMessage = null) }\n    }\n\n    fun setLanguage(langCode: String) {\n        preferences?.selectedLanguage = langCode\n        _uiState.update { it.copy(currentLanguage = langCode) }\n    }\n\n    fun setAutoUpdateEngine(enabled: Boolean) {\n        preferences?.autoUpdateEngine = enabled\n        _uiState.update { it.copy(autoUpdateEngine = enabled) }\n    }\n\n    fun setAutoUpdateApp(enabled: Boolean) {\n        preferences?.autoUpdateApp = enabled\n        _uiState.update { it.copy(autoUpdateApp = enabled) }\n    }\n\n    fun checkForAppUpdates(manual: Boolean = true) {\n        val mgr = updateManager ?: return\n        if (_uiState.value.isCheckingAppUpdate) return\n\n        _uiState.update { it.copy(isCheckingAppUpdate = true, appUpdateMessage = null) }\n        viewModelScope.launch {\n            val result = mgr.checkForUpdates(force = true)\n            _uiState.update { current ->\n                when (result) {\n                    is UpdateCheckResult.UpdateAvailable -> {\n                        current.copy(\n                            isCheckingAppUpdate = false,\n                            availableRelease = result.releaseInfo,\n                            appUpdateMessage = null\n                        )\n                    }\n                    is UpdateCheckResult.UpToDate -> {\n                        current.copy(\n                            isCheckingAppUpdate = false,\n                            availableRelease = null,\n                            appUpdateMessage = if (manual) \"SnapLoad is up to date (${result.currentVersion})\" else null\n                        )\n                    }\n                    is UpdateCheckResult.Error -> {\n                        current.copy(\n                            isCheckingAppUpdate = false,\n                            availableRelease = null,\n                            appUpdateMessage = if (manual) \"Update check failed: ${result.message}\" else null\n                        )\n                    }\n                }\n            }\n        }\n    }\n\n    fun downloadAndInstallUpdate(releaseInfo: ReleaseInfo) {\n        val mgr = updateManager ?: return\n        val apkUrl = releaseInfo.apkDownloadUrl ?: return\n\n        val cachedApk = mgr.getDownloadedUpdateApk()\n        if (cachedApk != null) {\n            val installResult = mgr.promptInstallApk(cachedApk)\n            if (installResult.isFailure) {\n                val error = installResult.exceptionOrNull()\n                _uiState.update {\n                    it.copy(\n                        appUpdateMessage = if (error is SecurityException) {\n                            \"Please allow installing apps from SnapLoad in Settings, then tap Install again.\"\n                        } else {\n                            \"Failed to start installer: ${error?.message}\"\n                        }\n                    )\n                }\n            }\n            return\n        }\n\n        _uiState.update { it.copy(isDownloadingAppUpdate = true, appUpdateProgress = 0) }\n\n        viewModelScope.launch {\n            val downloadResult = mgr.downloadApk(apkUrl) { percent ->\n                _uiState.update { it.copy(appUpdateProgress = percent) }\n            }\n\n            _uiState.update { it.copy(isDownloadingAppUpdate = false) }\n\n            if (downloadResult.isSuccess) {\n                val apkFile = downloadResult.getOrThrow()\n                val installResult = mgr.promptInstallApk(apkFile)\n                if (installResult.isFailure) {\n                    val error = installResult.exceptionOrNull()\n                    _uiState.update {\n                        it.copy(\n                            appUpdateMessage = if (error is SecurityException) {\n                                \"Please allow installing apps from SnapLoad in Settings, then tap Install again.\"\n                            } else {\n                                \"Failed to start installer: ${error?.message}\"\n                            }\n                        )\n                    }\n                }\n            } else {\n                _uiState.update {\n                    it.copy(appUpdateMessage = \"Failed to download update: ${downloadResult.exceptionOrNull()?.message}\")\n                }\n            }\n        }\n    }\n\n    fun dismissUpdateDialog() {\n        _uiState.update { it.copy(availableRelease = null) }\n    }\n\n    fun clearAppUpdateMessage() {\n        _uiState.update { it.copy(appUpdateMessage = null) }\n    }\n\n    private fun fetchMetadataForUrl(url: String) {\n        metadataJob?.cancel()\n        metadataJob = viewModelScope.launch {\n            _uiState.update { it.copy(isFetchingMetadata = true, metadataError = null) }\n            val result = repository.fetchMetadata(url)\n            _uiState.update { current ->\n                if (result.isSuccess) {\n                    current.copy(\n                        isFetchingMetadata = false,\n                        metadata = result.getOrNull(),\n                        metadataError = null\n                    )\n                } else {\n                    current.copy(\n                        isFetchingMetadata = false,\n                        metadata = null,\n                        metadataError = \"Failed to load preview (download may still work): ${result.exceptionOrNull()?.message}\"\n                    )\n                }\n            }\n        }\n    }\n}\n