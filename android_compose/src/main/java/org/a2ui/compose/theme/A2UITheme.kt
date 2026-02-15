package org.a2ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class A2UIColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val surfaceTint: Color,
)

data class A2UIThemeConfig(
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val backgroundColor: String? = null,
    val surfaceColor: String? = null,
    val textColor: String? = null,
    val errorColor: String? = null,
    val darkMode: Boolean? = null,
    val borderRadius: Int = 8,
    val fontFamily: String? = null,
)

val LocalA2UIThemeConfig = staticCompositionLocalOf { A2UIThemeConfig() }

fun parseColor(colorHex: String?): Color? {
    if (colorHex.isNullOrBlank()) return null
    return try {
        val hex = colorHex.removePrefix("#")
        val color = when (hex.length) {
            6 -> {
                val r = hex.substring(0, 2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                Color(r, g, b)
            }
            8 -> {
                val a = hex.substring(0, 2).toInt(16)
                val r = hex.substring(2, 4).toInt(16)
                val g = hex.substring(4, 6).toInt(16)
                val b = hex.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            }
            else -> null
        }
        color
    } catch (e: Exception) {
        null
    }
}

fun createColorScheme(
    config: A2UIThemeConfig,
    darkTheme: Boolean
): ColorScheme {
    val primaryColor = parseColor(config.primaryColor) ?: if (darkTheme) {
        Color(0xFFD0BCFF)
    } else {
        Color(0xFF6750A4)
    }

    val secondaryColor = parseColor(config.secondaryColor) ?: if (darkTheme) {
        Color(0xFFCCC2DC)
    } else {
        Color(0xFF625B71)
    }

    val backgroundColor = parseColor(config.backgroundColor) ?: if (darkTheme) {
        Color(0xFF1C1B1F)
    } else {
        Color(0xFFFFFBFE)
    }

    val surfaceColor = parseColor(config.surfaceColor) ?: if (darkTheme) {
        Color(0xFF1C1B1F)
    } else {
        Color(0xFFFFFBFE)
    }

    val errorColor = parseColor(config.errorColor) ?: if (darkTheme) {
        Color(0xFFF2B8B5)
    } else {
        Color(0xFFB3261E)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = backgroundColor,
            surface = surfaceColor,
            error = errorColor,
            primaryContainer = primaryColor.copy(alpha = 0.3f),
            secondaryContainer = secondaryColor.copy(alpha = 0.3f),
            surfaceVariant = surfaceColor.copy(alpha = 0.8f),
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = backgroundColor,
            surface = surfaceColor,
            error = errorColor,
            primaryContainer = primaryColor.copy(alpha = 0.1f),
            secondaryContainer = secondaryColor.copy(alpha = 0.1f),
            surfaceVariant = surfaceColor.copy(alpha = 0.95f),
        )
    }
}

@Composable
fun A2UITheme(
    config: A2UIThemeConfig = A2UIThemeConfig(),
    darkTheme: Boolean = config.darkMode ?: isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = createColorScheme(config, darkTheme)

    val typography = Typography(
        headlineLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
    )

    CompositionLocalProvider(
        LocalA2UIThemeConfig provides config
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

@Composable
fun a2uiThemeConfig(): A2UIThemeConfig {
    return LocalA2UIThemeConfig.current
}
