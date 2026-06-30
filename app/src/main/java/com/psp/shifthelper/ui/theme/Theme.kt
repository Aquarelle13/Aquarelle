package com.psp.shifthelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
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
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
