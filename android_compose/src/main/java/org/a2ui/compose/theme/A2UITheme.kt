package org.a2ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import org.a2ui.compose.effects.*

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
    // 毛玻璃和立体效果配置
    val enableGlassmorphism: Boolean = false,
    val blurRadius: Int = 10,
    val cardElevation: Int = 8,
    val gradientColors: List<String>? = null,
    val shadowColor: String? = null,
    val glassmorphismAlpha: Float = 0.2f,
    val borderWidth: Int = 1,
    // 动画配置
    val enableAnimations: Boolean = true,
    val animationDuration: Int = 300,
    val animationEasing: AnimationEasing = AnimationEasing.EASE_IN_OUT,
    val enableMicroInteractions: Boolean = true,
    val enableDataTransitions: Boolean = true,
    // 过渡效果配置
    val cardEnterAnimation: CardAnimation = CardAnimation.SLIDE_UP,
    val cardExitAnimation: CardAnimation = CardAnimation.FADE_OUT,
    val listItemAnimation: ListAnimation = ListAnimation.STAGGER,
    // 高级视觉效果配置
    val enableAdvancedEffects: Boolean = false,
    val customGradients: Map<String, GradientConfig> = emptyMap(),
    val customShadows: Map<String, ShadowConfig> = emptyMap(),
    val sceneBasedEffects: Boolean = true,
)

enum class AnimationEasing {
    LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BOUNCE, SPRING
}

enum class CardAnimation {
    FADE_IN, SLIDE_UP, SLIDE_DOWN, SCALE_IN, FLIP_IN
}

enum class ListAnimation {
    NONE, STAGGER, WAVE, CASCADE
}

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

/**
 * 创建毛玻璃效果的Modifier
 */
fun Modifier.glassmorphism(
    config: A2UIThemeConfig,
    shape: Shape,
    darkTheme: Boolean = false
): Modifier {
    return if (config.enableGlassmorphism) {
        val baseColor = if (darkTheme) Color.White else Color.Black
        val gradientColors = config.gradientColors?.mapNotNull { parseColor(it) }
            ?: listOf(
                baseColor.copy(alpha = config.glassmorphismAlpha),
                baseColor.copy(alpha = config.glassmorphismAlpha * 0.5f)
            )

        this
            .background(
                brush = Brush.verticalGradient(colors = gradientColors),
                shape = shape
            )
            .blur(radius = config.blurRadius.dp)
            .border(
                width = config.borderWidth.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 0.3f),
                        baseColor.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
    } else {
        this
    }
}

/**
 * 获取增强的卡片颜色
 */
@Composable
fun getEnhancedCardColors(
    config: A2UIThemeConfig,
    darkTheme: Boolean = isSystemInDarkTheme()
): CardColors {
    return if (config.enableGlassmorphism) {
        CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    } else {
        CardDefaults.cardColors()
    }
}

/**
 * 获取增强的卡片阴影
 */
@Composable
fun getEnhancedCardElevation(config: A2UIThemeConfig): CardElevation {
    return CardDefaults.cardElevation(
        defaultElevation = config.cardElevation.dp
    )
}

/**
 * 根据配置创建动画规格
 */
fun createAnimationSpec(config: A2UIThemeConfig): AnimationSpec<Float> {
    return when (config.animationEasing) {
        AnimationEasing.LINEAR -> tween(
            durationMillis = config.animationDuration,
            easing = LinearEasing
        )
        AnimationEasing.EASE_IN -> tween(
            durationMillis = config.animationDuration,
            easing = FastOutLinearInEasing
        )
        AnimationEasing.EASE_OUT -> tween(
            durationMillis = config.animationDuration,
            easing = LinearOutSlowInEasing
        )
        AnimationEasing.EASE_IN_OUT -> tween(
            durationMillis = config.animationDuration,
            easing = FastOutSlowInEasing
        )
        AnimationEasing.BOUNCE -> keyframes {
            durationMillis = config.animationDuration
            0.0f at 0 with LinearOutSlowInEasing
            0.2f at (config.animationDuration * 0.15).toInt()
            0.0f at (config.animationDuration * 0.3).toInt()
            0.1f at (config.animationDuration * 0.45).toInt()
            0.0f at (config.animationDuration * 0.6).toInt()
            1.0f at config.animationDuration
        }
        AnimationEasing.SPRING -> spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }
}

/**
 * 创建卡片进入动画
 */
fun createCardEnterTransition(config: A2UIThemeConfig): EnterTransition {
    val animationSpec = createAnimationSpec(config)
    return when (config.cardEnterAnimation) {
        CardAnimation.FADE_IN -> fadeIn(animationSpec)
        CardAnimation.SLIDE_UP -> slideInVertically(animationSpec) { it / 2 } + fadeIn(animationSpec)
        CardAnimation.SLIDE_DOWN -> slideInVertically(animationSpec) { -it / 2 } + fadeIn(animationSpec)
        CardAnimation.SCALE_IN -> scaleIn(animationSpec, initialScale = 0.8f) + fadeIn(animationSpec)
        CardAnimation.FLIP_IN -> scaleIn(animationSpec, initialScale = 0.0f) + fadeIn(animationSpec)
    }
}

/**
 * 创建卡片退出动画
 */
fun createCardExitTransition(config: A2UIThemeConfig): ExitTransition {
    val animationSpec = createAnimationSpec(config)
    return when (config.cardExitAnimation) {
        CardAnimation.FADE_OUT -> fadeOut(animationSpec)
        CardAnimation.SLIDE_DOWN -> slideOutVertically(animationSpec) { it / 2 } + fadeOut(animationSpec)
        else -> fadeOut(animationSpec)
    }
}

/**
 * 增强的毛玻璃效果修饰符，支持自定义渐变
 */
fun Modifier.enhancedGlassmorphism(
    config: A2UIThemeConfig,
    shape: Shape,
    darkTheme: Boolean = false,
    gradientKey: String? = null
): Modifier {
    return if (config.enableGlassmorphism || config.enableAdvancedEffects) {
        val gradient = gradientKey?.let { key ->
            config.customGradients[key]
        } ?: run {
            val baseColor = if (darkTheme) Color.White else Color.Black
            val gradientColors = config.gradientColors?.mapNotNull { parseColor(it) }
                ?: listOf(
                    baseColor.copy(alpha = config.glassmorphismAlpha),
                    baseColor.copy(alpha = config.glassmorphismAlpha * 0.5f)
                )
            GradientConfig(
                type = GradientType.LINEAR,
                colors = gradientColors,
                angle = 135f
            )
        }

        this
            .background(
                brush = createGradientBrush(gradient),
                shape = shape
            )
            .blur(radius = config.blurRadius.dp)
            .border(
                width = config.borderWidth.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
    } else {
        this
    }
}

/**
 * 高级阴影修饰符
 */
fun Modifier.advancedShadow(
    config: A2UIThemeConfig,
    shape: Shape,
    shadowKey: String? = null
): Modifier {
    return if (config.enableAdvancedEffects) {
        val shadowConfig = shadowKey?.let { key ->
            config.customShadows[key]
        } ?: ShadowPresets.MEDIUM

        this.shadow(
            elevation = shadowConfig.elevation,
            shape = shape,
            clip = false,
            ambientColor = shadowConfig.shadowColor,
            spotColor = shadowConfig.shadowColor
        )
    } else {
        this.shadow(
            elevation = config.cardElevation.dp,
            shape = shape
        )
    }
}

/**
 * 场景感知的视觉效果
 */
fun Modifier.sceneAwareEffects(
    config: A2UIThemeConfig,
    shape: Shape,
    scene: String,
    isPositive: Boolean = true,
    importance: String = "medium"
): Modifier {
    return if (config.sceneBasedEffects && config.enableAdvancedEffects) {
        val gradient = getSceneGradient(scene, isPositive)
        val shadowConfig = getSceneShadow(importance)

        this
            .background(
                brush = createGradientBrush(gradient),
                shape = shape
            )
            .shadow(
                elevation = shadowConfig.elevation,
                shape = shape,
                ambientColor = shadowConfig.shadowColor,
                spotColor = shadowConfig.shadowColor
            )
    } else {
        this.enhancedGlassmorphism(config, shape)
    }
}
