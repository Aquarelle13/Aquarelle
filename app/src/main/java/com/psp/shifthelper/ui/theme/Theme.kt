package com.psp.shifthelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = Background,
    surface = Surface,
    onBackground = Foreground,
    onSurface = Foreground,
    onSurfaceVariant = MutedForeground,
    outline = Border,
    primary = AccentBlue,
    error = StatusError
)

@Composable
fun PSPShiftHelperTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}