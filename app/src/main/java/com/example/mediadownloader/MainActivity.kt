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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askNotificationPermissionIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        val sharedText = when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.toString()
            }
            Intent.ACTION_VIEW -> {
                intent.dataString
            }
            else -> null
        }
        if (!sharedText.isNullOrBlank()) {
            viewModel.handleSharedText(sharedText)
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

private class LocalizedActivityContextWrapper(
    val activity: ComponentActivity,
    private val localizedConfiguration: Configuration
) : ContextWrapper(activity) {
    private val localizedResources: Resources by lazy {
        activity.createConfigurationContext(localizedConfiguration).resources
    }

    override fun getResources(): Resources = localizedResources
}
