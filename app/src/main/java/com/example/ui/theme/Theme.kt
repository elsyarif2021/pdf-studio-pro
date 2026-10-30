package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrightTeal,
    onPrimary = PrimaryNavy,
    primaryContainer = SecondaryNavy,
    onPrimaryContainer = TealSoft,
    secondary = PremiumTeal,
    onSecondary = PrimaryNavy,
    secondaryContainer = NavySurface,
    onSecondaryContainer = TealSoft,
    tertiary = RoyalBlue,
    background = DarkBackground,
    onBackground = PrimaryTextDark,
    surface = DarkSurface,
    onSurface = PrimaryTextDark,
    surfaceVariant = NavySurface,
    onSurfaceVariant = SecondaryTextDark,
    outline = BorderDark,
    error = StatusError
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryNavy,
    onPrimary = LightSurface,
    primaryContainer = TealSoft,
    onPrimaryContainer = PrimaryNavy,
    secondary = PremiumTeal,
    onSecondary = LightSurface,
    secondaryContainer = TealSoft,
    onSecondaryContainer = PrimaryNavy,
    tertiary = RoyalBlue,
    background = LightBackground,
    onBackground = PrimaryTextLight,
    surface = LightSurface,
    onSurface = PrimaryTextLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SecondaryTextLight,
    outline = BorderLight,
    error = StatusError
)

@Composable
fun PDFStudioProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
