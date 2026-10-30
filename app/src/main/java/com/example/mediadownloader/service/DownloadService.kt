package com.example.mediadownloader.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.mediadownloader.DownloaderApplication
import com.example.mediadownloader.MainActivity
import com.example.mediadownloader.R
import com.example.mediadownloader.data.DefaultDownloadRepository
import com.example.mediadownloader.data.DownloadRepository
import com.example.mediadownloader.model.AudioFormat
import com.example.mediadownloader.model.AudioQuality
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.DownloadUiState
import com.example.mediadownloader.model.MediaType
import com.example.mediadownloader.model.PlaylistItem
import com.example.mediadownloader.model.PlaylistItemStatus
import com.example.mediadownloader.model.PlaylistMetadata
import com.example.mediadownloader.model.VideoFormat
import com.example.mediadownloader.model.VideoQuality
import com.example.mediadownloader.util.MediaStoreSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"

        const val ACTION_START_DOWNLOAD = "com.example.mediadownloader.action.START_DOWNLOAD"
        const val ACTION_START_PLAYLIST_DOWNLOAD = "com.example.mediadownloader.action.START_PLAYLIST_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.mediadownloader.action.CANCEL_DOWNLOAD"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_VIDEO_FORMAT = "extra_video_format"
        const val EXTRA_VIDEO_QUALITY = "extra_video_quality"
        const val EXTRA_AUDIO_FORMAT = "extra_audio_format"
        const val EXTRA_AUDIO_QUALITY = "extra_audio_quality"

        // In-memory holder for playlist payload to bypass Intent size limits
        var pendingPlaylistToDownload: PlaylistMetadata? = null

        private val _downloadState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
        val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

        fun startDownload(context: Context, url: String, format: DownloadFormat, title: String) {
            startDownload(context, url, DownloadConfiguration.fromDownloadFormat(format), title)
        }

        fun startDownload(context: Context, url: String, config: DownloadConfiguration, title: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT, config.asDownloadFormat.name)
                putExtra(EXTRA_MEDIA_TYPE, config.mediaType.name)
                putExtra(EXTRA_VIDEO_FORMAT, config.videoFormat.name)
                putExtra(EXTRA_VIDEO_QUALITY, config.videoQuality.name)
                putExtra(EXTRA_AUDIO_FORMAT, config.audioFormat.name)
                putExtra(EXTRA_AUDIO_QUALITY, config.audioQuality.name)
            }
            startServiceCompat(context, intent)
        }

        fun startPlaylistDownload(context: Context, playlist: PlaylistMetadata, config: DownloadConfiguration) {
            pendingPlaylistToDownload = playlist
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_PLAYLIST_DOWNLOAD
                putExtra(EXTRA_TITLE, playlist.title)
                putExtra(EXTRA_FORMAT, config.asDownloadFormat.name)
                putExtra(EXTRA_MEDIA_TYPE, config.mediaType.name)
                putExtra(EXTRA_VIDEO_FORMAT, config.videoFormat.name)
                putExtra(EXTRA_VIDEO_QUALITY, config.videoQuality.name)
                putExtra(EXTRA_AUDIO_FORMAT, config.audioFormat.name)
                putExtra(EXTRA_AUDIO_QUALITY, config.audioQuality.name)
            }
            startServiceCompat(context, intent)
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        }

        fun resetState() {
            _downloadState.value = DownloadUiState.Idle
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var downloadJob: Job? = null
    private var currentDownloadId: String? = null
    private lateinit var repository: DownloadRepository
    private lateinit var notificationManager: NotificationManager

    override fun attachBaseContext(newBase: Context) {
        val localizedBase = try {
            val prefs = com.example.mediadownloader.data.AppPreferences(newBase)
            val lang = prefs.selectedLanguage
            com.example.mediadownloader.util.LocaleHelper.createLocalizedContext(newBase, lang)
        } catch (_: Exception) {
            newBase
        }
        super.attachBaseContext(localizedBase)
    }

    override fun onCreate() {
        super.onCreate()
        repository = DefaultDownloadRepository(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = extractConfigFromIntent(intent)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "SnapLoad Download"

        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                handleStartSingleDownload(url, config, title)
            }
            ACTION_START_PLAYLIST_DOWNLOAD -> {
                val playlist = pendingPlaylistToDownload
                if (playlist != null && playlist.items.isNotEmpty()) {
                    handleStartPlaylistDownload(playlist, config)
                } else {
                    stopSelf()
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                handleCancelDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun extractConfigFromIntent(intent: Intent?): DownloadConfiguration {
        val mediaType = intent?.getStringExtra(EXTRA_MEDIA_TYPE)?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
            ?: MediaType.VIDEO
        val videoFormat = intent?.getStringExtra(EXTRA_VIDEO_FORMAT)?.let { runCatching { VideoFormat.valueOf(it) }.getOrNull() }
            ?: VideoFormat.MP4
        val videoQuality = intent?.getStringExtra(EXTRA_VIDEO_QUALITY)?.let { runCatching { VideoQuality.valueOf(it) }.getOrNull() }
            ?: VideoQuality.BEST
        val audioFormat = intent?.getStringExtra(EXTRA_AUDIO_FORMAT)?.let { runCatching { AudioFormat.valueOf(it) }.getOrNull() }
            ?: AudioFormat.MP3
        val audioQuality = intent?.getStringExtra(EXTRA_AUDIO_QUALITY)?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
            ?: AudioQuality.BEST

        return DownloadConfiguration(
            mediaType = mediaType,
            videoFormat = videoFormat,
            videoQuality = videoQuality,
            audioFormat = audioFormat,
            audioQuality = audioQuality
        )
    }

    private fun handleStartSingleDownload(url: String, config: DownloadConfiguration, initialTitle: String) {
        val downloadId = "dl_" + UUID.randomUUID().toString().substring(0, 8)
        currentDownloadId = downloadId

        val notification = createNotification(
            title = initialTitle,
            contentText = getString(R.string.notification_starting),
            progress = 0,
            isIndeterminate = true
        )
        startServiceForeground(notification)

        _downloadState.value = DownloadUiState.Downloading(0f, "Initializing...", 0)

        downloadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val downloadResult = repository.downloadMediaDetailed(
                    url = url,
                    config = config,
                    downloadId = downloadId
                ) { progress, eta, statusText ->
                    _downloadState.value = DownloadUiState.Downloading(progress, statusText, eta)
                    updateNotificationProgress(initialTitle, statusText, progress.toInt())
                }

                if (downloadResult.isFailure) {
                    val error = downloadResult.exceptionOrNull()?.message ?: "Download failed"
                    _downloadState.value = DownloadUiState.Error(error)
                    return@launch
                }

                val detailedResult = downloadResult.getOrThrow()
                val tempFile = detailedResult.file
                val effectiveConfig = detailedResult.effectiveConfig

                _downloadState.value = DownloadUiState.SavingToGallery
                updateNotificationIndeterminate(initialTitle, getString(R.string.notification_saving))

                val savedUri = MediaStoreSaver.saveToGallery(
                    context = applicationContext,
                    tempFile = tempFile,
                    title = initialTitle,
                    config = effectiveConfig
                )

                if (savedUri != null) {
                    _downloadState.value = DownloadUiState.Success(
                        title = initialTitle,
                        uri = savedUri,
                        format = effectiveConfig.asDownloadFormat,
                        config = effectiveConfig,
                        fallbackNotice = detailedResult.fallbackNotice
                    )
                } else {
                    val errorMsg = getString(R.string.notification_failed)
                    _downloadState.value = DownloadUiState.Error(errorMsg)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download service crashed: ${e.message}", e)
                val errorMsg = e.message ?: "Unexpected error occurred"
                _downloadState.value = DownloadUiState.Error(errorMsg)
            } finally {
                dismissDownloadNotification()
                stopSelf()
            }
        }
    }

    private fun handleStartPlaylistDownload(playlist: PlaylistMetadata, config: DownloadConfiguration) {
        val totalCount = playlist.items.size
        val currentItems = playlist.items.toMutableList()

        val notification = createNotification(
            title = playlist.title,
            contentText = getString(R.string.notification_starting),
            progress = 0,
            isIndeterminate = false
        )
        startServiceForeground(notification)

        _downloadState.value = DownloadUiState.PlaylistDownloading(
            playlistTitle = playlist.title,
            currentIndex = 1,
            totalCount = totalCount,
            currentItemTitle = currentItems.firstOrNull()?.title ?: "",
            currentItemProgress = 0f,
            overallProgress = 0f,
            items = currentItems
        )

        downloadJob = serviceScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failedCount = 0
            var fallbackOccurredNotice: String? = null

            try {
                for (i in currentItems.indices) {
                    if (!isActive) break

                    val item = currentItems[i]
                    val downloadId = "pl_" + UUID.randomUUID().toString().substring(0, 8)
                    currentDownloadId = downloadId

                    currentItems[i] = item.copy(status = PlaylistItemStatus.DOWNLOADING, progress = 0f)
                    val overallProgress = (i.toFloat() / totalCount.toFloat()) * 100f

                    _downloadState.value = DownloadUiState.PlaylistDownloading(
                        playlistTitle = playlist.title,
                        currentIndex = i + 1,
                        totalCount = totalCount,
                        currentItemTitle = item.title,
                        currentItemProgress = 0f,
                        overallProgress = overallProgress,
                        items = currentItems.toList()
                    )

                    val statusMsg = getString(R.string.playlist_downloading_progress, i + 1, totalCount, item.title)
                    updateNotificationProgress(playlist.title, statusMsg, overallProgress.toInt())

                    val downloadResult = repository.downloadMediaDetailed(
                        url = item.sourceUrl,
                        config = config,
                        downloadId = downloadId
                    ) { itemProg, _, statusText ->
                        val currentOverall = (((i.toFloat() + (itemProg / 100f)) / totalCount.toFloat()) * 100f).coerceIn(0f, 100f)
                        currentItems[i] = currentItems[i].copy(progress = itemProg)
                        _downloadState.value = DownloadUiState.PlaylistDownloading(
                            playlistTitle = playlist.title,
                            currentIndex = i + 1,
                            totalCount = totalCount,
                            currentItemTitle = item.title,
                            currentItemProgress = itemProg,
                            overallProgress = currentOverall,
                            speed = statusText,
                            items = currentItems.toList()
                        )
                        updateNotificationProgress(playlist.title, "${(i + 1)}/$totalCount • $statusText", currentOverall.toInt())
                    }

                    if (downloadResult.isSuccess) {
                        val detailedResult = downloadResult.getOrThrow()
                        if (detailedResult.fallbackNotice != null) {
                            fallbackOccurredNotice = detailedResult.fallbackNotice
                        }
                        val tempFile = detailedResult.file
                        val savedUri = MediaStoreSaver.saveToGallery(
                            context = applicationContext,
                            tempFile = tempFile,
                            title = item.title,
                            config = detailedResult.effectiveConfig
                        )
                        if (savedUri != null) {
                            successCount++
                            currentItems[i] = currentItems[i].copy(status = PlaylistItemStatus.COMPLETED, progress = 100f)
                        } else {
                            failedCount++
                            currentItems[i] = currentItems[i].copy(status = PlaylistItemStatus.FAILED, error = "Failed to save")
                        }
                    } else {
                        failedCount++
                        val err = downloadResult.exceptionOrNull()?.message ?: "Failed"
                        currentItems[i] = currentItems[i].copy(status = PlaylistItemStatus.FAILED, error = err)
                    }

                    _downloadState.value = DownloadUiState.PlaylistDownloading(
                        playlistTitle = playlist.title,
                        currentIndex = (i + 1).coerceAtMost(totalCount),
                        totalCount = totalCount,
                        currentItemTitle = item.title,
                        currentItemProgress = 100f,
                        overallProgress = (((i + 1).toFloat() / totalCount.toFloat()) * 100f),
                        items = currentItems.toList()
                    )
                }

                if (isActive) {
                    _downloadState.value = DownloadUiState.PlaylistSuccess(
                        playlistTitle = playlist.title,
                        totalCount = totalCount,
                        successCount = successCount,
                        failedCount = failedCount,
                        items = currentItems.toList(),
                        fallbackNotice = fallbackOccurredNotice
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playlist download failed: ${e.message}", e)
                _downloadState.value = DownloadUiState.Error(e.message ?: "Playlist download interrupted")
            } finally {
                pendingPlaylistToDownload = null
                dismissDownloadNotification()
                stopSelf()
            }
        }
    }

    private fun startServiceForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                DownloaderApplication.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DownloaderApplication.NOTIFICATION_ID, notification)
        }
    }

    private fun handleCancelDownload() {
        downloadJob?.cancel()
        currentDownloadId?.let { id ->
            serviceScope.launch(Dispatchers.IO) {
                repository.cancelDownload(id)
            }
        }
        pendingPlaylistToDownload = null
        _downloadState.value = DownloadUiState.Idle
        dismissDownloadNotification()
        stopSelf()
    }

    private fun dismissDownloadNotification() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        try {
            notificationManager.cancel(DownloaderApplication.NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    private fun createNotification(
        title: String,
        contentText: String,
        progress: Int,
        isIndeterminate: Boolean
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DownloaderApplication.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpenApp)
            .setProgress(100, progress, isIndeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel), pendingCancel)
            .build()
    }

    private fun updateNotificationProgress(title: String, statusText: String, progress: Int) {
        val notification = createNotification(
            title = title,
            contentText = statusText,
            progress = progress.coerceIn(0, 100),
            isIndeterminate = false
        )
        notificationManager.notify(DownloaderApplication.NOTIFICATION_ID, notification)
    }

    private fun updateNotificationIndeterminate(title: String, statusText: String) {
        val notification = createNotification(
            title = title,
            contentText = statusText,
            progress = 0,
            isIndeterminate = true
        )
        notificationManager.notify(DownloaderApplication.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        dismissDownloadNotification()
        super.onDestroy()
    }
}
