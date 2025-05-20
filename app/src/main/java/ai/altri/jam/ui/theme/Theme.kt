package ai.altri.jam.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ColorScheme = lightColorScheme(
    // App bar colors
    primary = Primary,           // Brown app bar
    onPrimary = White,          // White text on app bar
    primaryContainer = Primary,  // Brown containers
    onPrimaryContainer = White,  // White text on brown containers

    // Bot message colors (left side)
    secondary = Secondary,       // Wheat color
    onSecondary = Black,        // Black text
    secondaryContainer = Secondary,
    onSecondaryContainer = Black,

    // User message colors (right side)
    tertiary = Tertiary,        // Brown
    onTertiary = White,         // White text
    tertiaryContainer = Tertiary,
    onTertiaryContainer = White,

    // Background and surface colors
    background = White,         // White background
    onBackground = Black,       // Black text
    surface = White,            // White input box
    onSurface = Black,          // Black text in input

    // Other colors
    surfaceVariant = White,     // White surface variants
    onSurfaceVariant = Black,   // Black text on variants
    outline = Primary           // Brown outlines
)

@Composable
fun LLMInferenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = ColorScheme  // Always use light theme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
