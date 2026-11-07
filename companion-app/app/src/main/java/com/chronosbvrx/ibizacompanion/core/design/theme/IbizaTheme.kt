package com.chronosbvrx.ibizacompanion.core.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IbizaColorScheme = darkColorScheme(
    primary = IbizaColors.Primary,
    secondary = IbizaColors.Secondary,
    background = IbizaColors.Background,
    surface = IbizaColors.Surface,
    error = IbizaColors.Error,
    onPrimary = IbizaColors.Background,
    onSecondary = IbizaColors.TextPrimary,
    onBackground = IbizaColors.TextPrimary,
    onSurface = IbizaColors.TextPrimary,
    onError = IbizaColors.TextPrimary
)

@Composable
fun IbizaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IbizaColorScheme,
        typography = IbizaTypography,
        shapes = IbizaShapes,
        content = content
    )
}