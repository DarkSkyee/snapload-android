package com.example.mediadownloader

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediadownloader.data.DefaultDownloadRepository
import com.example.mediadownloader.theme.MediaDownloaderTheme
import com.example.mediadownloader.ui.main.MainScreenViewModel
import com.example.mediadownloader.util.LocaleHelper

class MainActivity : ComponentActivity() {

    private val viewModel: MainScreenViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = DefaultDownloadRepository(applicationContext)
                return MainScreenViewModel(repository, applicationContext) as T
            }
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission handled
        }

    override fun attachBaseContext(newBase: Context) {
        val localizedBase = try {
            val prefs = com.example.mediadownloader.data.AppPreferences(newBase)
            val lang = prefs.selectedLanguage
            LocaleHelper.createLocalizedContext(newBase, lang)
        } catch (_: Exception) {
            newBase
        }
        super.attachBaseContext(localizedBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askNotificationPermissionIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            androidx.compose.runtime.LaunchedEffect(uiState.currentLanguage) {
                LocaleHelper.applyLanguage(this@MainActivity, uiState.currentLanguage)
            }

            val localizedConfiguration = remember(uiState.currentLanguage) {
                LocaleHelper.getLocalizedConfiguration(resources.configuration, uiState.currentLanguage)
            }
            val localizedContext = remember(uiState.currentLanguage) {
                LocalizedActivityContextWrapper(this@MainActivity, localizedConfiguration)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration
            ) {
                MediaDownloaderTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainNavigation(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val extractedUrl = com.example.mediadownloader.util.ShareIntentExtractor.extractUrlFromIntent(intent)
        val rawText = com.example.mediadownloader.util.ShareIntentExtractor.extractRawSharedText(intent)
        val targetText = extractedUrl ?: rawText

        if (!targetText.isNullOrBlank()) {
            val prefs = com.example.mediadownloader.data.AppPreferences(this)
            if (prefs.quickDownloadOnShare && intent.action == Intent.ACTION_SEND && !extractedUrl.isNullOrBlank()) {
                val wasActive = com.example.mediadownloader.service.DownloadService.isDownloadingActive()
                val started = com.example.mediadownloader.service.DownloadService.startDownload(
                    context = this,
                    url = extractedUrl,
                    config = com.example.mediadownloader.model.DownloadConfiguration(),
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
                android.widget.Toast.makeText(
                    applicationContext,
                    toastMsg,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                finish()
                return
            }
            viewModel.handleSharedText(targetText)
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

class LocalizedActivityContextWrapper(
    val base: Context,
    private val localizedConfiguration: Configuration
) : ContextWrapper(base) {
    private val localizedResources: Resources by lazy {
        base.createConfigurationContext(localizedConfiguration).resources
    }

    override fun getResources(): Resources = localizedResources
}
