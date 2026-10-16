package com.example.mediadownloader.data

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe initializer for YoutubeDL and FFmpeg native libraries.
 * Eliminates race conditions when the app is launched via Android Share Sheet (ACTION_SEND).
 */
object YoutubeDLInitializer {
    private const val TAG = "YoutubeDLInit"
    private val mutex = Mutex()

    @Volatile
    private var isInitialized = false

    suspend fun ensureInitialized(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        mutex.withLock {
            if (isInitialized) return@withLock true
            try {
                Log.d(TAG, "Initializing YoutubeDL and FFmpeg...")
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                isInitialized = true
                val version = YoutubeDL.getInstance().versionName(context.applicationContext)
                Log.i(TAG, "YoutubeDL and FFmpeg initialized successfully. Engine version: $version")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize YoutubeDL / FFmpeg: ${e.message}", e)
                false
            }
        }
    }

    fun isReady(): Boolean = isInitialized
}
