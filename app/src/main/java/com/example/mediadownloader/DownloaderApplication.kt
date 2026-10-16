package com.example.mediadownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloaderApplication : Application() {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "media_download_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "DownloaderApp"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize YoutubeDL and FFmpeg asynchronously on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Initializing YoutubeDL and FFmpeg...")
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                val version = YoutubeDL.getInstance().versionName(applicationContext)
                Log.i(TAG, "YoutubeDL initialized successfully with version: $version")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize YoutubeDL / FFmpeg: ${e.message}", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Pobieranie multimediów",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Informacje o bieżącym postępie pobierania i zapisywania plików"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
