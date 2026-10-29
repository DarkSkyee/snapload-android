package com.example.mediadownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.example.mediadownloader.data.YoutubeDLInitializer
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

        // Warm up YoutubeDL and FFmpeg in the background on startup
        CoroutineScope(Dispatchers.IO).launch {
            YoutubeDLInitializer.ensureInitialized(applicationContext)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
