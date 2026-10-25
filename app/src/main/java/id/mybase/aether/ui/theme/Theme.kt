package id.mybase.aether.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = ElectricCyan,
        onPrimary = Graphite900,
        primaryContainer = Graphite700,
        onPrimaryContainer = ElectricCyan,
        secondary = Color(0xFF2E65DC),
        onSecondary = Color.White,
        secondaryContainer = Graphite700,
        onSecondaryContainer = Color(0xFF83AAFF),
        tertiary = Success,
        onTertiary = Graphite900,
        background = Graphite900,
        onBackground = TextPrimary,
        surface = Graphite800,
        onSurface = TextPrimary,
        surfaceVariant = Graphite700,
        onSurfaceVariant = TextSecondary,
        error = Error,
        onError = Graphite900,
        errorContainer = Graphite700,
        onErrorContainer = Error,
        outline = TextSecondary,
        outlineVariant = Graphite800,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF007D69),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF9CF2E2),
        onPrimaryContainer = Color(0xFF00201A),
        secondary = Color(0xFF2E65DC),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCE6FF),
        onSecondaryContainer = Color(0xFF001849),
        tertiary = Color(0xFF0E7C5B),
        onTertiary = Color.White,
        background = Color(0xFFF4F6F8),
        onBackground = Color(0xFF0E1218),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0E1218),
        surfaceVariant = Color(0xFFE4E9EF),
        onSurfaceVariant = Color(0xFF4A5560),
        error = Color(0xFFC2332E),
        onError = Color.White,
    )

/** AETHER Material3 theme. Dark graphite is the default brand look. */
@Composable
fun AetherTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
