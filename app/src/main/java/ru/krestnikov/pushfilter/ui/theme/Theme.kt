// @file Theme.kt
// @author: ForUltraplayer
// @brief Accessible light and dark palettes for the simplified keyword filter.
package ru.krestnikov.pushfilter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF275DCE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5EDFF),
    onPrimaryContainer = Color(0xFF14356C),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFF6F7FB),
    surfaceContainerLow = Color.White,
    secondaryContainer = Color(0xFFEBEEF5),
    onSecondaryContainer = Color(0xFF29364C),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF12316A),
    primaryContainer = Color(0xFF20395F),
    onPrimaryContainer = Color(0xFFDCE7FF),
    background = Color(0xFF11151D),
    surface = Color(0xFF11151D),
    surfaceContainerLow = Color(0xFF1C2230),
    secondaryContainer = Color(0xFF2B3343),
    onSecondaryContainer = Color(0xFFDCE3F2),
)

@Composable
fun PushFilterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
