package ru.krestnikov.pushfilter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F5C9A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001944),
    secondary = Color(0xFF585E71),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF745470),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF072C6D),
    primaryContainer = Color(0xFF264382),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFC0C6DC),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFE2BBDC),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Russian labels run roughly a third longer than the English ones they were laid out for, and at
 * the stock Material size the segmented buttons, chips and captions break onto a second line.
 * Scaling every style by the same factor keeps the proportions of the type scale intact, and
 * scales along with the font size the user chose in the system settings.
 */
private const val TYPE_SCALE = 0.88f

private fun TextStyle.shrunk(): TextStyle =
    copy(fontSize = fontSize * TYPE_SCALE, lineHeight = lineHeight * TYPE_SCALE)

private val CompactTypography = Typography().run {
    copy(
        displayLarge = displayLarge.shrunk(),
        displayMedium = displayMedium.shrunk(),
        displaySmall = displaySmall.shrunk(),
        headlineLarge = headlineLarge.shrunk(),
        headlineMedium = headlineMedium.shrunk(),
        headlineSmall = headlineSmall.shrunk(),
        titleLarge = titleLarge.shrunk(),
        titleMedium = titleMedium.shrunk(),
        titleSmall = titleSmall.shrunk(),
        bodyLarge = bodyLarge.shrunk(),
        bodyMedium = bodyMedium.shrunk(),
        bodySmall = bodySmall.shrunk(),
        labelLarge = labelLarge.shrunk(),
        labelMedium = labelMedium.shrunk(),
        labelSmall = labelSmall.shrunk(),
    )
}

@Composable
fun PushFilterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = CompactTypography, content = content)
}
