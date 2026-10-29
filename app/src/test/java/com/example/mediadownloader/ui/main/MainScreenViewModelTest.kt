package com.example.mediadownloader.ui.main

import com.example.mediadownloader.data.DownloadRepository
import com.example.mediadownloader.model.AudioFormat
import com.example.mediadownloader.model.AudioQuality
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.MediaMetadata
import com.example.mediadownloader.model.MediaType
import com.example.mediadownloader.model.VideoFormat
import com.example.mediadownloader.model.VideoQuality
import com.example.mediadownloader.util.LinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDownloadRepository
    private lateinit var viewModel: MainScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDownloadRepository()
        viewModel = MainScreenViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_defaultsAreCorrect() {
        val state = viewModel.uiState.value
        assertEquals("", state.urlInput)
        assertEquals(LinkParser.Platform.UNKNOWN, state.detectedPlatform)
        assertEquals(DownloadFormat.MP4, state.selectedFormat)
        assertEquals(MediaType.VIDEO, state.downloadConfig.mediaType)
        assertEquals(VideoFormat.MP4, state.downloadConfig.videoFormat)
        assertEquals(VideoQuality.BEST, state.downloadConfig.videoQuality)
        assertFalse(state.isFetchingMetadata)
        assertNull(state.metadata)
        assertEquals("2026.01.01", state.engineVersion)
    }

    @Test
    fun onUrlChanged_detectsYouTubePlatformAndFetchesMetadata() = runTest {
        val ytUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        fakeRepository.metadataToReturn = Result.success(
            MediaMetadata(
                url = ytUrl,
                title = "Rick Astley - Never Gonna Give You Up",
                author = "RickAstleyVEVO",
                durationSeconds = 213,
                platform = LinkParser.Platform.YOUTUBE
            )
        )

        viewModel.onUrlChanged(ytUrl)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ytUrl, state.urlInput)
        assertEquals(LinkParser.Platform.YOUTUBE, state.detectedPlatform)
        assertNotNull(state.metadata)
        assertEquals("Rick Astley - Never Gonna Give You Up", state.metadata?.title)
        assertFalse(state.isFetchingMetadata)
    }

    @Test
    fun onUrlChanged_detectsTikTokPlatform() = runTest {
        val tiktokUrl = "https://www.tiktok.com/@user/video/123456789"
        viewModel.onUrlChanged(tiktokUrl)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(tiktokUrl, state.urlInput)
        assertEquals(LinkParser.Platform.TIKTOK, state.detectedPlatform)
    }

    @Test
    fun handleSharedText_extractsUrlAndFetchesMetadata() = runTest {
        val sharedText = "Check out this TikTok video: https://vm.tiktok.com/ZMxxxxxx/ it's super funny!"
        val expectedCleanUrl = "https://vm.tiktok.com/ZMxxxxxx/"
        fakeRepository.metadataToReturn = Result.success(
            MediaMetadata(
                url = expectedCleanUrl,
                title = "Funny TikTok",
                platform = LinkParser.Platform.TIKTOK
            )
        )

        viewModel.handleSharedText(sharedText)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(expectedCleanUrl, state.urlInput)
        assertEquals(LinkParser.Platform.TIKTOK, state.detectedPlatform)
        assertEquals("Funny TikTok", state.metadata?.title)
    }

    @Test
    fun onFormatSelected_updatesFormat() {
        viewModel.onFormatSelected(DownloadFormat.MP3)
        assertEquals(DownloadFormat.MP3, viewModel.uiState.value.selectedFormat)
        assertEquals(MediaType.AUDIO, viewModel.uiState.value.downloadConfig.mediaType)
        assertEquals(AudioFormat.MP3, viewModel.uiState.value.downloadConfig.audioFormat)

        viewModel.onFormatSelected(DownloadFormat.MP4)
        assertEquals(DownloadFormat.MP4, viewModel.uiState.value.selectedFormat)
        assertEquals(MediaType.VIDEO, viewModel.uiState.value.downloadConfig.mediaType)
        assertEquals(VideoFormat.MP4, viewModel.uiState.value.downloadConfig.videoFormat)
    }

    @Test
    fun onConfigChanged_updatesConfigAndFormat() {
        val newConfig = DownloadConfiguration(
            mediaType = MediaType.AUDIO,
            audioFormat = AudioFormat.FLAC,
            audioQuality = AudioQuality.BEST
        )
        viewModel.onConfigChanged(newConfig)
        assertEquals(newConfig, viewModel.uiState.value.downloadConfig)
        assertEquals(DownloadFormat.FLAC, viewModel.uiState.value.selectedFormat)
    }

    @Test
    fun setLanguage_updatesCurrentLanguage() {
        viewModel.setLanguage("pl")
        assertEquals("pl", viewModel.uiState.value.currentLanguage)

        viewModel.setLanguage("en")
        assertEquals("en", viewModel.uiState.value.currentLanguage)
    }

    @Test
    fun setAutoUpdateToggles_updateState() {
        viewModel.setAutoUpdateEngine(false)
        assertFalse(viewModel.uiState.value.autoUpdateEngine)

        viewModel.setAutoUpdateApp(false)
        assertFalse(viewModel.uiState.value.autoUpdateApp)
    }

    @Test
    fun onClearInput_resetsAllFields() = runTest {
        viewModel.onUrlChanged("https://youtu.be/123")
        advanceUntilIdle()

        viewModel.onClearInput()
        val state = viewModel.uiState.value
        assertEquals("", state.urlInput)
        assertEquals(LinkParser.Platform.UNKNOWN, state.detectedPlatform)
        assertNull(state.metadata)
    }

    @Test
    fun updateEngine_executesSuccessfully() = runTest {
        fakeRepository.updateResult = Result.success("Zaktualizowano silnik yt-dlp")
        viewModel.updateEngine()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUpdatingEngine)
        assertEquals("Zaktualizowano silnik yt-dlp", state.engineUpdateMessage)
    }
}

private class FakeDownloadRepository : DownloadRepository {
    var metadataToReturn: Result<MediaMetadata> = Result.success(
        MediaMetadata(
            url = "https://youtube.com/watch?v=sample",
            title = "Sample Video",
            author = "Sample Channel",
            platform = LinkParser.Platform.YOUTUBE
        )
    )
    var updateResult: Result<String> = Result.success("Zaktualizowano")
    var currentVersion: String = "2026.01.01"

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> = metadataToReturn

    override suspend fun downloadMedia(
        url: String,
        format: DownloadFormat,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<File> {
        onProgress(50f, 10, "50% • 5 MB/s")
        return Result.success(File.createTempFile("fake_media", ".${format.extension}"))
    }

    override suspend fun downloadMedia(
        url: String,
        config: DownloadConfiguration,
        downloadId: String,
        onProgress: (progress: Float, etaSeconds: Long, statusText: String) -> Unit
    ): Result<File> {
        onProgress(50f, 10, "50% • 5 MB/s")
        return Result.success(File.createTempFile("fake_media", ".${config.extension}"))
    }

    override suspend fun cancelDownload(downloadId: String): Boolean = true

    override suspend fun updateEngine(): Result<String> = updateResult

    override suspend fun checkAndAutoUpdateEngineIfNeeded(force: Boolean): Result<String>? = null

    override fun getEngineVersion(): String = currentVersion
}
