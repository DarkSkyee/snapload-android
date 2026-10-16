package com.example.mediadownloader.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediadownloader.data.DownloadRepository
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.DownloadUiState
import com.example.mediadownloader.model.DownloadedItem
import com.example.mediadownloader.model.MediaMetadata
import com.example.mediadownloader.service.DownloadService
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
    val isFetchingMetadata: Boolean = false,
    val metadata: MediaMetadata? = null,
    val metadataError: String? = null,
    val engineVersion: String = "",
    val engineUpdateMessage: String? = null,
    val isUpdatingEngine: Boolean = false,
    val recentDownloads: List<DownloadedItem> = emptyList()
)

class MainScreenViewModel(
    private val repository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(engineVersion = repository.getEngineVersion()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadUiState> = DownloadService.downloadState

    private var metadataJob: Job? = null

    init {
        // Observe download state to automatically record successful downloads in history
        viewModelScope.launch {
            DownloadService.downloadState.collect { state ->
                if (state is DownloadUiState.Success) {
                    val currentMeta = _uiState.value.metadata
                    val item = DownloadedItem(
                        id = System.currentTimeMillis().toString(),
                        title = state.title,
                        uriString = state.uri.toString(),
                        format = state.format,
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
        _uiState.update { it.copy(selectedFormat = format) }
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

        val title = _uiState.value.metadata?.title
            ?: "Media_${System.currentTimeMillis()}"
        val format = _uiState.value.selectedFormat

        DownloadService.startDownload(context, url, format, title)
    }

    fun cancelDownload(context: Context) {
        DownloadService.cancelDownload(context)
    }

    fun resetDownloadState() {
        DownloadService.resetState()
    }

    fun updateEngine() {
        if (_uiState.value.isUpdatingEngine) return
        _uiState.update { it.copy(isUpdatingEngine = true, engineUpdateMessage = null) }
        viewModelScope.launch {
            val result = repository.updateEngine()
            _uiState.update {
                it.copy(
                    isUpdatingEngine = false,
                    engineUpdateMessage = result.getOrNull() ?: "Błąd: ${result.exceptionOrNull()?.message}",
                    engineVersion = repository.getEngineVersion()
                )
            }
        }
    }

    fun clearEngineUpdateMessage() {
        _uiState.update { it.copy(engineUpdateMessage = null) }
    }

    private fun fetchMetadataForUrl(url: String) {
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            _uiState.update { it.copy(isFetchingMetadata = true, metadataError = null) }
            val result = repository.fetchMetadata(url)
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(
                        isFetchingMetadata = false,
                        metadata = result.getOrNull(),
                        metadataError = null
                    )
                } else {
                    current.copy(
                        isFetchingMetadata = false,
                        metadata = null,
                        metadataError = "Nie udało się pobrać podglądu (pobieranie nadal może działać): ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }
}
