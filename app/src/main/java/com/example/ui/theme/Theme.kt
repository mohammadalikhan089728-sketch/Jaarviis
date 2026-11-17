package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = ObsidianBg,
    primaryContainer = ArcCyanContainer,
    onPrimaryContainer = ArcCyan,
    secondary = TelemetryAmber,
    onSecondary = ObsidianBg,
    secondaryContainer = TelemetryAmberContainer,
    onSecondaryContainer = TelemetryAmber,
    tertiary = EmeraldReady,
    onTertiary = ObsidianBg,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldReady,
    error = AlertRose,
    onError = ObsidianBg,
    errorContainer = AlertRoseContainer,
    onErrorContainer = AlertRose,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutline.copy(alpha = 0.5f)
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = DeepCyanLight,
    onPrimary = CrispLightSurface,
    primaryContainer = ArcCyan.copy(alpha = 0.16f),
    onPrimaryContainer = DeepCyanLight,
    secondary = DeepAmberLight,
    onSecondary = CrispLightSurface,
    secondaryContainer = TelemetryAmber.copy(alpha = 0.18f),
    onSecondaryContainer = DeepAmberLight,
    tertiary = EmeraldReady,
    onTertiary = TextPrimaryLight,
    background = CrispLightBg,
    onBackground = TextPrimaryLight,
    surface = CrispLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = CrispLightSurfaceHigh,
    onSurfaceVariant = TextSecondaryLight,
    outline = CrispLightOutline
)

val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun JarvisAssistantTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JarvisDarkColorScheme else JarvisLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
