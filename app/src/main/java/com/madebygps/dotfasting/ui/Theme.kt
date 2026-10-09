package com.madebygps.dotfasting.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object DotColors {
    val Background = Color(0xFF071F1D)
    val Surface = Color(0xFF0D2B28)
    val Container = Color(0xFF123733)
    val Raised = Color(0xFF17423D)
    val Track = Color(0xFF1B4A45)
    val Line = Color(0xFF1A3D39)
    val Text = Color(0xFFF7FFFD)
    val Muted = Color(0xFFA4C1BD)
    val Dim = Color(0xFF63817D)
    val Highlights = listOf(
        "Signal red" to 0xFFE8343AL, "Amber" to 0xFFF2A33AL,
        "Acid" to 0xFFC8F03CL, "Mint" to 0xFF4BD6A0L,
        "Ice" to 0xFF5AB8F5L, "Violet" to 0xFFA78BFAL, "Paper" to 0xFFEDEDEDL,
    )
}

@Composable
fun DotFastingTheme(highlightArgb: Long = 0xFFE8343AL, content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(highlightArgb.toInt()),
            secondary = DotColors.Text,
            onSecondary = DotColors.Background,
            secondaryContainer = DotColors.Raised,
            onSecondaryContainer = DotColors.Text,
            onPrimary = DotColors.Background,
            background = DotColors.Background,
            onBackground = DotColors.Text,
            surface = DotColors.Surface,
            onSurface = DotColors.Text,
            surfaceVariant = DotColors.Surface,
            surfaceContainer = DotColors.Surface,
            surfaceContainerHigh = DotColors.Container,
            surfaceContainerHighest = DotColors.Raised,
            onSurfaceVariant = DotColors.Muted,
            outline = DotColors.Dim,
            outlineVariant = DotColors.Line,
            error = Color(0xFFFFB4AB),
        ),
        typography = base.copy(
            labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp, color = DotColors.Muted),
            labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
