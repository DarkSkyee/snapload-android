package com.example.mediadownloader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediadownloader.R
import com.example.mediadownloader.model.AudioFormat
import com.example.mediadownloader.model.AudioQuality
import com.example.mediadownloader.model.DownloadConfiguration
import com.example.mediadownloader.model.MediaType
import com.example.mediadownloader.model.VideoFormat
import com.example.mediadownloader.model.VideoQuality

@Composable
fun MediaConfigSelector(
    config: DownloadConfiguration,
    onConfigChange: (DownloadConfiguration) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // 1. Animated MediaType Tabs (Video vs Audio)
            MediaTypeTabs(
                selectedType = config.mediaType,
                onSelectType = { onConfigChange(config.copy(mediaType = it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Animated Format & Quality Options
            AnimatedContent(
                targetState = config.mediaType,
                transitionSpec = {
                    if (targetState == MediaType.AUDIO) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                label = "media_config_switch"
            ) { type ->
                if (type == MediaType.VIDEO) {
                    VideoOptionsContent(
                        selectedFormat = config.videoFormat,
                        selectedQuality = config.videoQuality,
                        onFormatChange = { onConfigChange(config.copy(videoFormat = it)) },
                        onQualityChange = { onConfigChange(config.copy(videoQuality = it)) }
                    )
                } else {
                    AudioOptionsContent(
                        selectedFormat = config.audioFormat,
                        selectedQuality = config.audioQuality,
                        onFormatChange = { onConfigChange(config.copy(audioFormat = it)) },
                        onQualityChange = { onConfigChange(config.copy(audioQuality = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaTypeTabs(
    selectedType: MediaType,
    onSelectType: (MediaType) -> Unit
) {
    val selectedIndex = if (selectedType == MediaType.VIDEO) 0 else 1

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MediaTypeTabItem(
                title = stringResource(R.string.media_type_video),
                icon = Icons.Default.Movie,
                isSelected = selectedIndex == 0,
                badge = "MP4 ★",
                onClick = { onSelectType(MediaType.VIDEO) },
                modifier = Modifier.weight(1f)
            )

            MediaTypeTabItem(
                title = stringResource(R.string.media_type_audio),
                icon = Icons.Default.Audiotrack,
                isSelected = selectedIndex == 1,
                badge = "MP3",
                onClick = { onSelectType(MediaType.AUDIO) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MediaTypeTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun VideoOptionsContent(
    selectedFormat: VideoFormat,
    selectedQuality: VideoQuality,
    onFormatChange: (VideoFormat) -> Unit,
    onQualityChange: (VideoQuality) -> Unit
) {
    Column {
        // Format Row
        Text(
            text = stringResource(R.string.media_format_label),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoFormat.values().forEach { format ->
                val isSelected = selectedFormat == format
                FilterChip(
                    selected = isSelected,
                    onClick = { onFormatChange(format) },
                    label = {
                        Text(
                            text = if (format == VideoFormat.MP4) "${format.displayName} ★" else format.displayName,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quality Row
        Text(
            text = stringResource(R.string.media_quality_label),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoQuality.values().forEach { quality ->
                val isSelected = selectedQuality == quality
                val label = when (quality) {
                    VideoQuality.BEST -> stringResource(R.string.quality_best_video)
                    VideoQuality.P1080 -> stringResource(R.string.quality_1080p)
                    VideoQuality.P720 -> stringResource(R.string.quality_720p)
                    VideoQuality.P480 -> stringResource(R.string.quality_480p)
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onQualityChange(quality) },
                    label = {
                        Text(
                            text = if (quality == VideoQuality.BEST) "$label ★" else label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun AudioOptionsContent(
    selectedFormat: AudioFormat,
    selectedQuality: AudioQuality,
    onFormatChange: (AudioFormat) -> Unit,
    onQualityChange: (AudioQuality) -> Unit
) {
    Column {
        // Format Row (Scrollable chips)
        Text(
            text = stringResource(R.string.media_format_label),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioFormat.values().forEach { format ->
                val isSelected = selectedFormat == format
                FilterChip(
                    selected = isSelected,
                    onClick = { onFormatChange(format) },
                    label = {
                        Text(
                            text = if (format == AudioFormat.MP3) "${format.displayName} ★" else format.displayName,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quality Row
        Text(
            text = stringResource(R.string.media_quality_label),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioQuality.values().forEach { quality ->
                val isSelected = selectedQuality == quality
                val label = when (quality) {
                    AudioQuality.BEST -> stringResource(R.string.quality_best_audio)
                    AudioQuality.HIGH -> stringResource(R.string.quality_256k)
                    AudioQuality.MEDIUM -> stringResource(R.string.quality_192k)
                    AudioQuality.LOW -> stringResource(R.string.quality_128k)
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onQualityChange(quality) },
                    label = {
                        Text(
                            text = if (quality == AudioQuality.BEST) "$label ★" else label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}
