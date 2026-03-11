package org.a2ui.compose.effects

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/**
 * 渐变效果配置
 */
data class GradientConfig(
    val type: GradientType = GradientType.LINEAR,
    val colors: List<Color>,
    val stops: List<Float>? = null,
    val angle: Float = 0f, // 线性渐变角度（度）
    val centerX: Float = 0.5f, // 径向渐变中心X (0-1)
    val centerY: Float = 0.5f, // 径向渐变中心Y (0-1)
    val radius: Float = 0.5f // 径向渐变半径 (0-1)
)

enum class GradientType {
    LINEAR, RADIAL, SWEEP, CONIC
}

/**
 * 阴影效果配置
 */
data class ShadowConfig(
    val elevation: Dp = 8.dp,
    val shadowColor: Color = Color.Black.copy(alpha = 0.1f),
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 4.dp,
    val blurRadius: Dp = 8.dp,
    val spreadRadius: Dp = 0.dp,
    val inset: Boolean = false // 内阴影
)

/**
 * 预定义渐变样式
 */
object GradientPresets {
    val SUNSET = GradientConfig(
        type = GradientType.LINEAR,
        colors = listOf(
            Color(0xFFFF6B6B),
            Color(0xFFFFE66D),
            Color(0xFF4ECDC4)
        ),
        angle = 45f
    )

    val OCEAN = GradientConfig(
        type = GradientType.LINEAR,
        colors = listOf(
            Color(0xFF667eea),
            Color(0xFF764ba2)
        ),
        angle = 135f
    )

    val FOREST = GradientConfig(
        type = GradientType.RADIAL,
        colors = listOf(
            Color(0xFF56ab2f),
            Color(0xFFa8e6cf)
        ),
        centerX = 0.3f,
        centerY = 0.3f
    )

    val FINANCIAL_POSITIVE = GradientConfig(
        type = GradientType.LINEAR,
        colors = listOf(
            Color(0xFF4CAF50).copy(alpha = 0.8f),
            Color(0xFF8BC34A).copy(alpha = 0.6f)
        ),
        angle = 90f
    )

    val FINANCIAL_NEGATIVE = GradientConfig(
        type = GradientType.LINEAR,
        colors = listOf(
            Color(0xFFF44336).copy(alpha = 0.8f),
            Color(0xFFFF5722).copy(alpha = 0.6f)
        ),
        angle = 90f
    )
}

/**
 * 预定义阴影样式
 */
object ShadowPresets {
    val SOFT = ShadowConfig(
        elevation = 4.dp,
        shadowColor = Color.Black.copy(alpha = 0.08f),
        offsetY = 2.dp,
        blurRadius = 8.dp
    )

    val MEDIUM = ShadowConfig(
        elevation = 8.dp,
        shadowColor = Color.Black.copy(alpha = 0.12f),
        offsetY = 4.dp,
        blurRadius = 16.dp
    )

    val STRONG = ShadowConfig(
        elevation = 16.dp,
        shadowColor = Color.Black.copy(alpha = 0.16f),
        offsetY = 8.dp,
        blurRadius = 24.dp
    )

    val INSET_SOFT = ShadowConfig(
        elevation = 2.dp,
        shadowColor = Color.Black.copy(alpha = 0.1f),
        offsetY = 1.dp,
        blurRadius = 4.dp,
        inset = true
    )
}

/**
 * 创建渐变画刷
 */
fun createGradientBrush(config: GradientConfig, width: Float = 1f, height: Float = 1f): Brush {
    return when (config.type) {
        GradientType.LINEAR -> {
            val angleRad = config.angle * PI / 180
            val endX = cos(angleRad).toFloat() * width
            val endY = sin(angleRad).toFloat() * height

            Brush.linearGradient(
                colors = config.colors,
                stops = config.stops,
                start = Offset.Zero,
                end = Offset(endX, endY)
            )
        }
        GradientType.RADIAL -> Brush.radialGradient(
            colors = config.colors,
            stops = config.stops,
            center = Offset(config.centerX * width, config.centerY * height),
            radius = config.radius * minOf(width, height)
        )
        GradientType.SWEEP -> Brush.sweepGradient(
            colors = config.colors,
            stops = config.stops,
            center = Offset(config.centerX * width, config.centerY * height)
        )
        GradientType.CONIC -> {
            // Conic gradient implementation (fallback to sweep for now)
            Brush.sweepGradient(
                colors = config.colors,
                stops = config.stops,
                center = Offset(config.centerX * width, config.centerY * height)
            )
        }
    }
}

/**
 * 根据场景获取推荐渐变
 */
fun getSceneGradient(scene: String, isPositive: Boolean = true): GradientConfig {
    return when (scene.lowercase()) {
        "stock", "financial" -> {
            if (isPositive) GradientPresets.FINANCIAL_POSITIVE
            else GradientPresets.FINANCIAL_NEGATIVE
        }
        "weather" -> GradientPresets.OCEAN
        "nature", "environment" -> GradientPresets.FOREST
        "sunset", "evening" -> GradientPresets.SUNSET
        else -> GradientPresets.OCEAN
    }
}

/**
 * 根据重要性获取推荐阴影
 */
fun getSceneShadow(importance: String): ShadowConfig {
    return when (importance.lowercase()) {
        "low", "subtle" -> ShadowPresets.SOFT
        "medium", "normal" -> ShadowPresets.MEDIUM
        "high", "prominent" -> ShadowPresets.STRONG
        "inset" -> ShadowPresets.INSET_SOFT
        else -> ShadowPresets.MEDIUM
    }
}