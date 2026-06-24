package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PolishPrimaryDark,
    secondary = PolishOutlineDark,
    tertiary = PolishPrimaryContainerDark,
    background = PolishBackgroundDark,
    surface = PolishSurfaceDark,
    onPrimary = PolishOnPrimaryDark,
    onSecondary = PolishOnPrimaryContainerDark,
    onTertiary = PolishOnPrimaryContainerDark,
    onBackground = PolishOnBackgroundDark,
    onSurface = PolishOnSurfaceDark,
    outline = PolishOutlineDark,
    error = PolishError,
    errorContainer = PolishErrorContainer,
    onError = PolishOnError
)

private val LightColorScheme = lightColorScheme(
    primary = PolishPrimary,
    secondary = PolishSecondary,
    tertiary = PolishPrimaryContainer,
    background = PolishBackground,
    surface = PolishSurface,
    onPrimary = PolishOnPrimary,
    onSecondary = PolishOnBackground,
    onTertiary = PolishOnPrimaryContainer,
    onBackground = PolishOnBackground,
    onSurface = PolishOnSurface,
    outline = PolishOutline,
    error = PolishError,
    errorContainer = PolishErrorContainer,
    onError = PolishOnError
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
