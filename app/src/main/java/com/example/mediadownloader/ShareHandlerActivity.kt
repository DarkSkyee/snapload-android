package com.example.mediadownloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.example.mediadownloader.data.AppPreferences
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.service.DownloadService
import com.example.mediadownloader.util.LocaleHelper
import com.example.mediadownloader.util.ShareIntentExtractor

class ShareHandlerActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        val localizedBase = try {
            val prefs = AppPreferences(newBase)
            LocaleHelper.createLocalizedContext(newBase, prefs.selectedLanguage)
        } catch (_: Exception) {
            newBase
        }
        super.attachBaseContext(localizedBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = AppPreferences(this)
        val isQuickDownloadEnabled = prefs.quickDownloadOnShare

        if (isQuickDownloadEnabled && intent?.action == Intent.ACTION_SEND) {
            val extractedUrl = ShareIntentExtractor.extractUrlFromIntent(intent)

            if (!extractedUrl.isNullOrBlank()) {
                val wasActive = DownloadService.isDownloadingActive()
                val started = DownloadService.startDownload(
                    context = this,
                    url = extractedUrl,
                    config = DownloadConfiguration(),
                    title = getString(R.string.quick_download_default_title)
                )

                val toastMsg = if (started) {
                    if (wasActive) {
                        getString(R.string.quick_download_queued)
                    } else {
                        getString(R.string.quick_download_started)
                    }
                } else {
                    getString(R.string.quick_download_failed_to_start)
                }

                Toast.makeText(
                    applicationContext,
                    toastMsg,
                    Toast.LENGTH_SHORT
                ).show()

                finishActivityQuietly()
                return
            } else {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.quick_download_no_link),
                    Toast.LENGTH_SHORT
                ).show()

                finishActivityQuietly()
                return
            }
        }

        // Feature is disabled (default) or action is not SEND: forward to MainActivity
        val mainIntent = Intent(intent).apply {
            setClass(this@ShareHandlerActivity, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(mainIntent)
        finishActivityQuietly()
    }

    private fun finishActivityQuietly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
        disablePendingTransitions()
    }

    private fun disablePendingTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
