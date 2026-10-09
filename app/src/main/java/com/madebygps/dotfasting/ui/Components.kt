package com.madebygps.dotfasting.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.madebygps.dotfasting.domain.RingGeometry
import com.madebygps.dotfasting.domain.DotArt
import com.madebygps.dotfasting.domain.NavigationArt

@Composable
fun DotLabel(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text, modifier,
        color = DotColors.Muted,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (large) FontWeight.Medium else FontWeight.Normal,
        fontSize = if (large) 14.sp else 12.sp,
        letterSpacing = if (large) 0.5.sp else 1.sp,
    )
}

@Composable
fun DotCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier, color = DotColors.Muted, fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, letterSpacing = 1.sp,
    )
}

@Composable
fun NavigationIcon(calendar: Boolean) {
    val dots = if (calendar) NavigationArt.calendar() else NavigationArt.settings()
    Canvas(Modifier.size(22.dp)) {
        dots.forEach {
            drawCircle(DotColors.Text, size.minDimension * it.radius, Offset(size.width * it.x, size.height * it.y))
        }
    }
}

@Composable
fun ProgressNavigationIcon() {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(22.dp)) {
        val barWidth = size.width * 0.18f
        val gap = size.width * 0.12f
        val heights = listOf(0.42f, 0.7f, 1f)
        heights.forEachIndexed { index, height ->
            drawRoundRect(
                color = if (index == heights.lastIndex) accent else DotColors.Text,
                topLeft = Offset(
                    x = size.width * 0.1f + index * (barWidth + gap),
                    y = size.height * (1f - height),
                ),
                size = Size(barWidth, size.height * height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

@Composable
fun ProgressRing(progress: Float, description: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = description }) {
        val diameter = size.minDimension
        val stroke = diameter * RingGeometry.STROKE_FRACTION
        val inset = stroke / 2 + 1f
        val arcSize = Size(diameter - inset * 2, diameter - inset * 2)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
        drawArc(DotColors.Track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (progress > 0f) {
            drawArc(
                accent, RingGeometry.START_DEGREES, RingGeometry.sweep(progress),
                false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
            )
        }
    }
}

@Composable
fun DotTime(value: String, modifier: Modifier = Modifier, color: Color = DotColors.Text) {
    Canvas(modifier.semantics { text = AnnotatedString(value) }) {
        val columns = value.length * 4 - 1
        val cell = minOf(size.width / columns.coerceAtLeast(1), size.height / 5f)
        val origin = Offset((size.width - columns * cell) / 2, (size.height - 5 * cell) / 2)
        var column = 0
        value.forEach { character ->
            DotArt.digits[character]?.forEachIndexed { y, row ->
                row.forEachIndexed { x, pixel ->
                    if (pixel == '1') drawCircle(color, cell * 0.42f, origin + Offset((column + x + 0.5f) * cell, (y + 0.5f) * cell))
                }
            }
            column += 4
        }
    }
}

fun durationText(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1000
    return "%02d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
}

fun goalText(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes % 60 == 0L) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"
}
