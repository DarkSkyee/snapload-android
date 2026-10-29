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
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp

val SnapElectricCyan = Color(0xFF00F2FE)
val SnapIndigo = Color(0xFF6366F1)
val SnapNeonViolet = Color(0xFFA855F7)
val SnapDarkSurface = Color(0xFF0B0F19)
val SnapBorderGlow = Color(0x3300F2FE)

@Composable
fun AppLogoIcon(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val bgBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E143B)),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(size * 0.28f), spotColor = SnapIndigo)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(bgBrush)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color(0x6000F2FE), Color(0x306366F1))),
                shape = RoundedCornerShape(size * 0.28f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.85f)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Equalizer audio fins on sides
            val finWidth = w * 0.055f
            // Left outer fin
            drawLine(
                color = SnapElectricCyan.copy(alpha = 0.45f),
                start = Offset(w * 0.12f, h * 0.44f),
                end = Offset(w * 0.12f, h * 0.58f),
                strokeWidth = finWidth,
                cap = StrokeCap.Round
            )
            // Left inner fin
            drawLine(
                color = SnapIndigo.copy(alpha = 0.80f),
                start = Offset(w * 0.20f, h * 0.38f),
                end = Offset(w * 0.20f, h * 0.64f),
                strokeWidth = finWidth,
                cap = StrokeCap.Round
            )

            // Right inner fin
            drawLine(
                color = SnapIndigo.copy(alpha = 0.80f),
                start = Offset(w * 0.80f, h * 0.38f),
                end = Offset(w * 0.80f, h * 0.64f),
                strokeWidth = finWidth,
                cap = StrokeCap.Round
            )
            // Right outer fin
            drawLine(
                color = SnapElectricCyan.copy(alpha = 0.45f),
                start = Offset(w * 0.88f, h * 0.44f),
                end = Offset(w * 0.88f, h * 0.58f),
                strokeWidth = finWidth,
                cap = StrokeCap.Round
            )

            // 2. Stylized 'S' speed ribbon
            val sPath = Path().apply {
                moveTo(w * 0.68f, h * 0.26f)
                cubicTo(w * 0.56f, h * 0.21f, w * 0.38f, h * 0.24f, w * 0.35f, h * 0.34f)
                cubicTo(w * 0.31f, h * 0.42f, w * 0.36f, h * 0.49f, w * 0.46f, h * 0.53f)
                lineTo(w * 0.56f, h * 0.57f)
                cubicTo(w * 0.66f, h * 0.61f, w * 0.69f, h * 0.68f, w * 0.66f, h * 0.76f)
                cubicTo(w * 0.62f, h * 0.82f, w * 0.48f, h * 0.84f, w * 0.36f, h * 0.78f)
            }
            drawPath(
                path = sPath,
                brush = Brush.linearGradient(
                    colors = listOf(SnapElectricCyan, SnapIndigo, SnapNeonViolet),
                    start = Offset(w * 0.35f, h * 0.25f),
                    end = Offset(w * 0.68f, h * 0.80f)
                ),
                style = Stroke(
                    width = w * 0.09f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 3. Central Downward Download Arrow
            val arrowPath = Path().apply {
                // Shaft
                moveTo(w * 0.50f, h * 0.36f)
                lineTo(w * 0.50f, h * 0.62f)
                // Left wing
                moveTo(w * 0.38f, h * 0.52f)
                lineTo(w * 0.50f, h * 0.64f)
                // Right wing
                lineTo(w * 0.62f, h * 0.52f)
            }
            drawPath(
                path = arrowPath,
                color = Color.White,
                style = Stroke(
                    width = w * 0.08f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 4. Media speed baseline
            drawLine(
                color = SnapElectricCyan,
                start = Offset(w * 0.38f, h * 0.73f),
                end = Offset(w * 0.62f, h * 0.73f),
                strokeWidth = w * 0.05f,
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
