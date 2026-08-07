package com.wzl.loudnessplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.wzl.loudnessplayer.data.AppTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF555F71),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2F9),
    onPrimaryContainer = Color(0xFF121C2B),
    secondary = Color(0xFF5B5F69),
    secondaryContainer = Color(0xFFE0E2EC),
    surface = Color(0xFFFAF8FC),
    surfaceVariant = Color(0xFFE2E2E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C7E8),
    onPrimary = Color(0xFF233148),
    primaryContainer = Color(0xFF394762),
    onPrimaryContainer = Color(0xFFD9E2F9),
    secondary = Color(0xFFC5C6D0),
    secondaryContainer = Color(0xFF44474F),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF45464D),
)

private val GreenColors = lightColorScheme(
    primary = Color(0xFF446A29),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F1A2),
    onPrimaryContainer = Color(0xFF0C2000),
    secondary = Color(0xFF55624C),
    secondaryContainer = Color(0xFFD8E7CB),
    tertiary = Color(0xFF386666),
    surface = Color(0xFFF8FAF2),
    surfaceVariant = Color(0xFFDFE4D8),
)

private val BlueColors = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    secondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFF6B5778),
    surface = Color(0xFFF8F9FF),
    surfaceVariant = Color(0xFFDFE2EB),
)

private val BrownColors = darkColorScheme(
    primary = Color(0xFFD6A05E),
    onPrimary = Color(0xFF3A260F),
    primaryContainer = Color(0xFF5A4124),
    onPrimaryContainer = Color(0xFFFFDDB6),
    secondary = Color(0xFFE1C4A4),
    secondaryContainer = Color(0xFF59432E),
    surface = Color(0xFF261B15),
    surfaceVariant = Color(0xFF51443A),
)

@Composable
fun LoudnessPlayerTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val colors = when (theme) {
        AppTheme.LIGHT -> LightColors
        AppTheme.DARK -> DarkColors
        AppTheme.GREEN -> GreenColors
        AppTheme.BLUE -> BlueColors
        AppTheme.BROWN -> BrownColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
