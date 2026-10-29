package com.example.mediadownloader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SnapIndigo = Color(0xFF6366F1)
val SnapCyan = Color(0xFF06B6D4)
val SnapDarkBg = Color(0xFF0F172A)
val SnapNeonViolet = Color(0xFF818CF8)

@Composable
fun AppLogoIcon(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(SnapIndigo, SnapCyan)
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(size * 0.28f))
            .clip(RoundedCornerShape(size * 0.28f))
            .background(gradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.82f)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Subtle sound wave ripples on the sides
            val waveColor = Color.White.copy(alpha = 0.40f)
            val waveWidth = w * 0.055f

            // Left audio wave arc
            drawArc(
                color = waveColor,
                startAngle = 120f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(-w * 0.08f, h * 0.22f),
                size = Size(w * 0.45f, h * 0.56f),
                style = Stroke(width = waveWidth, cap = StrokeCap.Round)
            )

            // Right audio wave arc
            drawArc(
                color = waveColor,
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(w * 0.63f, h * 0.22f),
                size = Size(w * 0.45f, h * 0.56f),
                style = Stroke(width = waveWidth, cap = StrokeCap.Round)
            )

            // 2. Play Symbol (Right-pointing geometric triangle)
            val playPath = Path().apply {
                moveTo(w * 0.32f, h * 0.22f)
                lineTo(w * 0.32f, h * 0.78f)
                lineTo(w * 0.76f, h * 0.50f)
                close()
            }
            drawPath(
                path = playPath,
                color = Color.White.copy(alpha = 0.95f)
            )

            // 3. Central Downward Download Arrow Cutout
            val arrowPath = Path().apply {
                // Arrow stem
                moveTo(w * 0.44f, h * 0.32f)
                lineTo(w * 0.50f, h * 0.32f)
                lineTo(w * 0.50f, h * 0.46f)
                // Arrow head wings
                lineTo(w * 0.56f, h * 0.46f)
                lineTo(w * 0.47f, h * 0.60f)
                lineTo(w * 0.38f, h * 0.46f)
                lineTo(w * 0.44f, h * 0.46f)
                close()
            }
            drawPath(
                path = arrowPath,
                color = SnapDarkBg
            )

            // 4. Media baseline tray
            drawLine(
                color = SnapDarkBg,
                start = Offset(w * 0.37f, h * 0.66f),
                end = Offset(w * 0.57f, h * 0.66f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AppBrandHeader(
    tagline: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 42.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLogoIcon(size = iconSize)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "SnapLoad",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = tagline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
