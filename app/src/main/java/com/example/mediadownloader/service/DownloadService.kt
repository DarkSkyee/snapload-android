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
import com.example.mediadownloader.data.DefaultDownloadRepository
import com.example.mediadownloader.data.DownloadRepository
import com.example.mediadownloader.model.DownloadFormat
import com.example.mediadownloader.model.DownloadUiState
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

        private val _downloadState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
        val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

        fun startDownload(context: Context, url: String, format: DownloadFormat, title: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FORMAT, format.name)
                putExtra(EXTRA_TITLE, title)
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
                val formatName = intent.getStringExtra(EXTRA_FORMAT) ?: DownloadFormat.MP4.name
                val format = DownloadFormat.valueOf(formatName)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Pobieranie w toku"
                handleStartDownload(url, format, title)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                handleCancelDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStartDownload(url: String, format: DownloadFormat, initialTitle: String) {
        val downloadId = "dl_" + UUID.randomUUID().toString().substring(0, 8)
        currentDownloadId = downloadId

        val notification = createNotification(
            title = initialTitle,
            contentText = "Rozpoczynanie pobierania...",
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

        _downloadState.value = DownloadUiState.Downloading(0f, "Inicjalizacja...", 0)

        downloadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // 1. Download stream using repository
                val downloadResult = repository.downloadMedia(
                    url = url,
                    format = format,
                    downloadId = downloadId
                ) { progress, eta, statusText ->
                    _downloadState.value = DownloadUiState.Downloading(progress, statusText, eta)
                    updateNotificationProgress(initialTitle, statusText, progress.toInt())
                }

                if (downloadResult.isFailure) {
                    val error = downloadResult.exceptionOrNull()?.message ?: "Błąd pobierania"
                    _downloadState.value = DownloadUiState.Error(error)
                    showCompletionNotification(initialTitle, "Błąd: $error", false, null)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                    return@launch
                }

                val tempFile = downloadResult.getOrThrow()

                // 2. Save directly to Android Gallery via MediaStore
                _downloadState.value = DownloadUiState.SavingToGallery
                updateNotificationIndeterminate(initialTitle, "Zapisywanie do galerii...")

                val savedUri = MediaStoreSaver.saveToGallery(
                    context = applicationContext,
                    tempFile = tempFile,
                    title = initialTitle,
                    format = format
                )

                if (savedUri != null) {
                    _downloadState.value = DownloadUiState.Success(initialTitle, savedUri, format)
                    showCompletionNotification(
                        initialTitle,
                        "Zapisano w galerii (${format.displayName})",
                        true,
                        savedUri
                    )
                } else {
                    val errorMsg = "Nie udało się zapisać pliku w pamięci urządzenia"
                    _downloadState.value = DownloadUiState.Error(errorMsg)
                    showCompletionNotification(initialTitle, errorMsg, false, null)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download service crashed: ${e.message}", e)
                val errorMsg = e.message ?: "Wystąpił nieoczekiwany błąd"
                _downloadState.value = DownloadUiState.Error(errorMsg)
                showCompletionNotification(initialTitle, "Błąd: $errorMsg", false, null)
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
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Anuluj", pendingCancel)
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
        mediaUri: Uri?
    ) {
        val intent = if (isSuccess && mediaUri != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(mediaUri, if (mediaUri.toString().contains("video")) "video/*" else "audio/*")
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
