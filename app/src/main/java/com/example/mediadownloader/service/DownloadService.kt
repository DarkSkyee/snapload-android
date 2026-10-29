package com.example.mediadownloader.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
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
import com.example.mediadownloader.model.VideoFormat
import com.example.mediadownloader.model.VideoQuality
import com.example.mediadownloader.util.MediaStoreSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"

        const val ACTION_START_DOWNLOAD = "com.example.mediadownloader.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.mediadownloader.action.CANCEL_DOWNLOAD"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_VIDEO_FORMAT = "extra_video_format"
        const val EXTRA_VIDEO_QUALITY = "extra_video_quality"
        const val EXTRA_AUDIO_FORMAT = "extra_audio_format"
        const val EXTRA_AUDIO_QUALITY = "extra_audio_quality"

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

    override fun onCreate() {
        super.onCreate()
        repository = DefaultDownloadRepository(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "SnapLoad Download"

                val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
                    ?: MediaType.VIDEO
                val videoFormat = intent.getStringExtra(EXTRA_VIDEO_FORMAT)?.let { runCatching { VideoFormat.valueOf(it) }.getOrNull() }
                    ?: VideoFormat.MP4
                val videoQuality = intent.getStringExtra(EXTRA_VIDEO_QUALITY)?.let { runCatching { VideoQuality.valueOf(it) }.getOrNull() }
                    ?: VideoQuality.BEST
                val audioFormat = intent.getStringExtra(EXTRA_AUDIO_FORMAT)?.let { runCatching { AudioFormat.valueOf(it) }.getOrNull() }
                    ?: AudioFormat.MP3
                val audioQuality = intent.getStringExtra(EXTRA_AUDIO_QUALITY)?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
                    ?: AudioQuality.BEST

                val config = DownloadConfiguration(
                    mediaType = mediaType,
                    videoFormat = videoFormat,
                    videoQuality = videoQuality,
                    audioFormat = audioFormat,
                    audioQuality = audioQuality
                )
                handleStartDownload(url, config, title)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                handleCancelDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStartDownload(url: String, config: DownloadConfiguration, initialTitle: String) {
        val downloadId = "dl_" + UUID.randomUUID().toString().substring(0, 8)
        currentDownloadId = downloadId

        val notification = createNotification(
            title = initialTitle,
            contentText = getString(R.string.notification_starting),
            progress = 0,
            isIndeterminate = true
        )

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

        _downloadState.value = DownloadUiState.Downloading(0f, "Initializing...", 0)

        downloadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // 1. Download stream using repository
                val downloadResult = repository.downloadMedia(
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
                    showCompletionNotification(initialTitle, "Error: $error", false, null, config.mimeType)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                    return@launch
                }

                val tempFile = downloadResult.getOrThrow()

                // 2. Save directly to Android MediaStore
                _downloadState.value = DownloadUiState.SavingToGallery
                updateNotificationIndeterminate(initialTitle, getString(R.string.notification_saving))

                val savedUri = MediaStoreSaver.saveToGallery(
                    context = applicationContext,
                    tempFile = tempFile,
                    title = initialTitle,
                    config = config
                )

                if (savedUri != null) {
                    val message = getString(
                        if (config.mediaType == MediaType.VIDEO) R.string.notification_saved_gallery else R.string.notification_saved_music,
                        config.formatDisplayName
                    )
                    _downloadState.value = DownloadUiState.Success(initialTitle, savedUri, config.asDownloadFormat, config)
                    showCompletionNotification(
                        initialTitle,
                        message,
                        true,
                        savedUri,
                        config.mimeType
                    )
                } else {
                    val errorMsg = getString(R.string.notification_failed)
                    _downloadState.value = DownloadUiState.Error(errorMsg)
                    showCompletionNotification(initialTitle, errorMsg, false, null, config.mimeType)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download service crashed: ${e.message}", e)
                val errorMsg = e.message ?: "Unexpected error occurred"
                _downloadState.value = DownloadUiState.Error(errorMsg)
                showCompletionNotification(initialTitle, "Error: $errorMsg", false, null, config.mimeType)
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun handleCancelDownload() {
        downloadJob?.cancel()
        currentDownloadId?.let { id ->
            serviceScope.launch(Dispatchers.IO) {
                repository.cancelDownload(id)
            }
        }
        _downloadState.value = DownloadUiState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(com.example.mediadownloader.R.string.cancel), pendingCancel)
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

    private fun showCompletionNotification(
        title: String,
        message: String,
        isSuccess: Boolean,
        mediaUri: Uri?,
        mimeType: String
    ) {
        val intent = if (isSuccess && mediaUri != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(mediaUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(this, MainActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = if (isSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error

        val notification = NotificationCompat.Builder(this, DownloaderApplication.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(icon)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(DownloaderApplication.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        super.onDestroy()
    }
}
