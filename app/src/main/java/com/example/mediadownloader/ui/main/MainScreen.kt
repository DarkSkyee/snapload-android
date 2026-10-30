package com.example.mediadownloader.ui.main

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.mediadownloader.R
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.DownloadUiState
import com.example.mediadownloader.model.DownloadedItem
import com.example.mediadownloader.model.MediaMetadata
import com.example.mediadownloader.model.MediaType
import com.example.mediadownloader.model.PlaylistItemStatus
import com.example.mediadownloader.ui.components.AppBrandHeader
import com.example.mediadownloader.ui.components.AppMenuBottomSheet
import com.example.mediadownloader.ui.components.BouncyButton
import com.example.mediadownloader.ui.components.bouncyClickable
import com.example.mediadownloader.ui.components.MediaConfigSelector
import com.example.mediadownloader.util.LinkParser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSettingsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.engineUpdateMessage) {
        uiState.engineUpdateMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearEngineUpdateMessage()
        }
    }

    LaunchedEffect(uiState.appUpdateMessage) {
        uiState.appUpdateMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearAppUpdateMessage()
        }
    }

    val isDownloading = downloadState is DownloadUiState.Downloading ||
            downloadState is DownloadUiState.PlaylistDownloading ||
            downloadState is DownloadUiState.Processing ||
            downloadState is DownloadUiState.SavingToGallery

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppBrandHeader(
                        tagline = stringResource(R.string.app_tagline),
                        iconSize = 36.dp
                    )
                },
                actions = {
                    // Quick language toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val nextLang = if (uiState.currentLanguage == "en") "pl" else "en"
                                viewModel.setLanguage(nextLang)
                            }
                    ) {
                        Text(
                            text = if (uiState.currentLanguage == "en") "🇬🇧 EN" else "🇵🇱 PL",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = { showSettingsSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (uiState.availableRelease != null) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 0. Update Available Banner
            uiState.availableRelease?.let { release ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.update_banner_title, release.versionName),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.update_banner_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.downloadAndInstallUpdate(release) },
                                shape = RoundedCornerShape(10.dp),
                                enabled = !uiState.isDownloadingAppUpdate
                            ) {
                                Text(
                                    text = if (uiState.isDownloadingAppUpdate) "${uiState.appUpdateProgress}%" else stringResource(R.string.update_dialog_button_install),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 1. Input Section
            item {
                UrlInputCard(
                    url = uiState.urlInput,
                    platform = uiState.detectedPlatform,
                    onUrlChange = { viewModel.onUrlChanged(it) },
                    onPaste = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        viewModel.onPasteUrl(clipText)
                    },
                    onClear = { viewModel.onClearInput() },
                    enabled = !isDownloading
                )
            }

            // 2. Metadata Preview Card
            item {
                AnimatedVisibility(
                    visible = uiState.isFetchingMetadata || uiState.metadata != null || uiState.metadataError != null,
                    enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                ) {
                    MetadataPreviewCard(
                        isLoading = uiState.isFetchingMetadata,
                        metadata = uiState.metadata,
                        error = uiState.metadataError
                    )
                }
            }

            // 3. Format & Quality Configurator (Material 3 Expressive)
            item {
                MediaConfigSelector(
                    config = uiState.downloadConfig,
                    onConfigChange = { viewModel.onConfigChanged(it) },
                    enabled = !isDownloading
                )
            }

            // 4. Download Action Button with Bouncy Micro-interaction
            item {
                val isUrlValid = LinkParser.isSupported(uiState.urlInput)

                val isPlaylist = uiState.metadata?.isPlaylist == true
                val buttonLabel = if (isPlaylist) {
                    stringResource(
                        R.string.download_playlist_btn,
                        uiState.metadata?.playlistInfo?.itemCount ?: 0
                    )
                } else if (uiState.downloadConfig.mediaType == MediaType.VIDEO) {
                    stringResource(
                        R.string.download_video_btn,
                        uiState.downloadConfig.formatDisplayName,
                        uiState.downloadConfig.qualityDisplayName
                    )
                } else {
                    stringResource(
                        R.string.download_audio_btn,
                        uiState.downloadConfig.formatDisplayName,
                        uiState.downloadConfig.qualityDisplayName
                    )
                }

                BouncyButton(
                    onClick = { viewModel.startDownload(context) },
                    enabled = isUrlValid && !isDownloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaylist) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaylist) Icons.AutoMirrored.Filled.QueueMusic else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = buttonLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // 5. Live Download Progress & Status Card
            item {
                AnimatedVisibility(
                    visible = downloadState !is DownloadUiState.Idle,
                    enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                ) {
                    DownloadStatusCard(
                        state = downloadState,
                        onCancel = { viewModel.cancelDownload(context) },
                        onReset = { viewModel.resetDownloadState() },
                        onOpen = { uri, mimeType ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.open_file_prompt),
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
                        }
                    )
                }
            }

            // 6. Recent Downloads History
            if (uiState.recentDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recent_downloads_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(uiState.recentDownloads) { item ->
                    RecentDownloadItemRow(
                        item = item,
                        onOpen = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(item.uriString), item.config.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.open_file_prompt),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showSettingsSheet) {
        AppMenuBottomSheet(
            sheetState = sheetState,
            currentLanguage = uiState.currentLanguage,
            autoUpdateEngine = uiState.autoUpdateEngine,
            autoUpdateApp = uiState.autoUpdateApp,
            quickDownloadOnShare = uiState.quickDownloadOnShare,
            engineVersion = uiState.engineVersion,
            appVersion = uiState.appVersion,
            isUpdatingEngine = uiState.isUpdatingEngine,
            isCheckingAppUpdate = uiState.isCheckingAppUpdate,
            onLanguageChange = { lang ->
                viewModel.setLanguage(lang)
            },
            onAutoUpdateEngineChange = { viewModel.setAutoUpdateEngine(it) },
            onAutoUpdateAppChange = { viewModel.setAutoUpdateApp(it) },
            onQuickDownloadOnShareChange = { viewModel.setQuickDownloadOnShare(it) },
            onCheckAppUpdateClick = { viewModel.checkForAppUpdates(manual = true) },
            onUpdateEngineClick = { viewModel.updateEngine() },
            onDismissRequest = {
                coroutineScope.launch {
                    sheetState.hide()
                    showSettingsSheet = false
                }
            }
        )
    }

    // App Update Dialog
    val release = uiState.availableRelease
    if (release != null) {
        CompositionLocalProvider(
            LocalContext provides LocalContext.current,
            LocalConfiguration provides LocalConfiguration.current
        ) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateDialog() },
                title = {
                    Text(
                        text = stringResource(R.string.update_dialog_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.update_dialog_version, release.versionName),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.update_dialog_notes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = release.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (uiState.isDownloadingAppUpdate) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.update_downloading, uiState.appUpdateProgress),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { uiState.appUpdateProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                },
                confirmButton = {
                    val hasApk = !release.apkDownloadUrl.isNullOrBlank()
                    Button(
                        onClick = {
                            if (hasApk) {
                                viewModel.downloadAndInstallUpdate(release)
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        enabled = !uiState.isDownloadingAppUpdate
                    ) {
                        Text(
                            stringResource(
                                if (hasApk) R.string.update_dialog_button_install else R.string.update_dialog_button_view_github
                            )
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissUpdateDialog() },
                        enabled = !uiState.isDownloadingAppUpdate
                    ) {
                        Text(stringResource(R.string.update_dialog_button_later))
                    }
                }
            )
        }
    }
}

@Composable
fun UrlInputCard(
    url: String,
    platform: LinkParser.Platform,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.5f else 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.paste_link_label)) },
                placeholder = { Text(stringResource(R.string.paste_link_placeholder)) },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (url.isNotEmpty() && enabled) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_input))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPaste,
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.paste_from_clipboard))
                }

                AnimatedVisibility(
                    visible = platform != LinkParser.Platform.UNKNOWN,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f),
                    exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.88f)
                ) {
                    val (label, bgColor, textColor) = when (platform) {
                        LinkParser.Platform.YOUTUBE -> Triple(
                            stringResource(R.string.platform_youtube),
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        LinkParser.Platform.YOUTUBE_MUSIC -> Triple(
                            stringResource(R.string.platform_youtube_music),
                            Color(0xFFFFEBEE),
                            Color(0xFFC62828)
                        )
                        LinkParser.Platform.SPOTIFY -> Triple(
                            stringResource(R.string.platform_spotify),
                            Color(0xFFE8F5E9),
                            Color(0xFF1B5E20)
                        )
                        LinkParser.Platform.TIKTOK -> Triple(
                            stringResource(R.string.platform_tiktok),
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Triple(
                            stringResource(R.string.platform_supported),
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bgColor
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataPreviewCard(
    isLoading: Boolean,
    metadata: MediaMetadata?,
    error: String?
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.fetching_metadata),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (metadata != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!metadata.thumbnailUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    ) {
                        AsyncImage(
                            model = metadata.thumbnailUrl,
                            contentDescription = stringResource(R.string.thumbnail_desc),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (metadata.durationSeconds > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = metadata.formattedDuration,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(14.dp)) {
                    if (metadata.isPlaylist && metadata.playlistInfo != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_label, metadata.playlistInfo.itemCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = metadata.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (metadata.author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = metadata.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (metadata.isPlaylist && metadata.playlistInfo != null && metadata.playlistInfo.items.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            metadata.playlistInfo.items.take(4).forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.index}.",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = "${item.title} ${if (item.author.isNotBlank()) "• " + item.author else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (metadata.playlistInfo.items.size > 4) {
                                Text(
                                    text = "+ ${metadata.playlistInfo.items.size - 4} more tracks...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun DownloadStatusCard(
    state: DownloadUiState,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onOpen: (Uri, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is DownloadUiState.Success, is DownloadUiState.PlaylistSuccess -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                is DownloadUiState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (state) {
                is DownloadUiState.Idle,
                is DownloadUiState.LoadingMetadata -> {}

                is DownloadUiState.Downloading -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.progress / 100f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "single_dl_progress"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.status_downloading),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.speed,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.etaSeconds > 0) {
                            val mins = state.etaSeconds / 60
                            val secs = state.etaSeconds % 60
                            Text(
                                text = String.format("ETA %02d:%02d", mins, secs),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                is DownloadUiState.PlaylistDownloading -> {
                    val animatedOverall by animateFloatAsState(
                        targetValue = (state.overallProgress / 100f).coerceIn(0f, 1f),
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "pl_overall_progress"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.playlistTitle,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    R.string.playlist_downloading_progress,
                                    state.currentIndex,
                                    state.totalCount,
                                    state.currentItemTitle
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { animatedOverall },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.speed.ifBlank { "${state.overallProgress.toInt()}%" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${state.currentIndex}/${state.totalCount}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimalist track list
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp)
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.items) { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${track.index}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (track.status == PlaylistItemStatus.DOWNLOADING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    when (track.status) {
                                        PlaylistItemStatus.COMPLETED -> Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        PlaylistItemStatus.DOWNLOADING -> CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        PlaylistItemStatus.FAILED -> Icon(
                                            Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        PlaylistItemStatus.PENDING -> Icon(
                                            Icons.Default.HourglassEmpty,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is DownloadUiState.Processing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.status_processing, state.stage),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is DownloadUiState.SavingToGallery -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.status_saving_gallery),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is DownloadUiState.Success -> {
                    if (state.fallbackNotice != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF3E0),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.fallbackNotice,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.config.mediaType == MediaType.VIDEO) {
                                    stringResource(R.string.status_success_gallery)
                                } else {
                                    stringResource(R.string.status_success_music)
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onOpen(state.uri, state.config.mimeType) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.open_in_player))
                        }

                        OutlinedButton(
                            onClick = onReset,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }

                is DownloadUiState.PlaylistSuccess -> {
                    if (state.fallbackNotice != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF3E0),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.fallbackNotice,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.playlist_success_summary,
                                    state.successCount,
                                    state.totalCount
                                ),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                            Text(
                                text = state.playlistTitle,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onReset,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }

                is DownloadUiState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.status_error),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onReset) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentDownloadItemRow(
    item: DownloadedItem,
    onOpen: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable(scaleDown = 0.98f) { onOpen() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (item.config.mediaType == MediaType.VIDEO) Icons.Default.Movie else Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.config.formatDisplayName} • ${item.config.qualityDisplayName} • " +
                            stringResource(if (item.config.mediaType == MediaType.VIDEO) R.string.recent_saved_in_gallery else R.string.recent_saved_in_music),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
