package org.a2ui.compose.charts

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.a2ui.compose.theme.A2UIThemeConfig
import kotlin.math.*

/**
 * 仪表盘组件
 */
@Composable
fun GaugeChart(
    data: ChartData.GaugeData,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    themeConfig: A2UIThemeConfig
) {
    val animatedValue by animateFloatAsState(
        targetValue = data.value,
        animationSpec = if (config.animationEnabled && themeConfig.enableAnimations) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            tween(0)
        },
        label = "gauge_value_animation"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (config.animationEnabled && themeConfig.enableAnimations) {
            tween(1000, easing = FastOutSlowInEasing)
        } else {
            tween(0)
        },
        label = "gauge_progress_animation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawGauge(
                    data = data.copy(value = animatedValue),
                    progress = animatedProgress,
                    config = config,
                    size = size
                )
            }

            // 中心数值显示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = ChartUtils.formatValue(animatedValue, 1),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                if (data.unit.isNotEmpty()) {
                    Text(
                        text = data.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 范围标签
        if (data.ranges.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                data.ranges.forEach { range ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(range.color, CircleShape)
                        )
                        if (range.label.isNotEmpty()) {
                            Text(
                                text = range.label,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 绘制仪表盘
 */
private fun DrawScope.drawGauge(
    data: ChartData.GaugeData,
    progress: Float,
    config: ChartConfig,
    size: Size
) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = minOf(size.width, size.height) / 2 - 20.dp.toPx()
    val strokeWidth = 12.dp.toPx()

    // 仪表盘角度范围（-135° 到 135°，共270°）
    val startAngle = -135f
    val sweepAngle = 270f

    // 绘制背景弧
    drawArc(
        color = Color.Gray.copy(alpha = 0.2f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // 绘制范围弧（如果有定义）
    if (data.ranges.isNotEmpty()) {
        data.ranges.forEach { range ->
            val rangeStartAngle = startAngle + (range.start / data.maxValue) * sweepAngle
            val rangeSweepAngle = ((range.end - range.start) / data.maxValue) * sweepAngle * progress

            drawArc(
                color = range.color.copy(alpha = 0.3f),
                startAngle = rangeStartAngle,
                sweepAngle = rangeSweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }

    // 绘制数值弧
    val valueRatio = (data.value - data.minValue) / (data.maxValue - data.minValue)
    val valueSweepAngle = valueRatio * sweepAngle * progress

    // 根据数值选择颜色
    val valueColor = when {
        data.ranges.isNotEmpty() -> {
            data.ranges.find { range ->
                data.value >= range.start && data.value <= range.end
            }?.color ?: config.colors.first()
        }
        valueRatio < 0.3f -> Color(0xFF4CAF50) // 绿色
        valueRatio < 0.7f -> Color(0xFFFF9800) // 橙色
        else -> Color(0xFFF44336) // 红色
    }

    drawArc(
        color = valueColor,
        startAngle = startAngle,
        sweepAngle = valueSweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // 绘制指针
    val pointerAngle = startAngle + valueSweepAngle
    val pointerLength = radius * 0.8f
    val pointerEndX = center.x + cos(Math.toRadians(pointerAngle.toDouble())).toFloat() * pointerLength
    val pointerEndY = center.y + sin(Math.toRadians(pointerAngle.toDouble())).toFloat() * pointerLength

    drawLine(
        color = valueColor,
        start = center,
        end = Offset(pointerEndX, pointerEndY),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
    )

    // 绘制中心圆点
    drawCircle(
        color = valueColor,
        radius = 8.dp.toPx(),
        center = center
    )
    drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = center
    )

    // 绘制刻度标记
    drawGaugeMarks(
        center = center,
        radius = radius,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        minValue = data.minValue,
        maxValue = data.maxValue,
        progress = progress
    )
}

/**
 * 绘制仪表盘刻度
 */
private fun DrawScope.drawGaugeMarks(
    center: Offset,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    minValue: Float,
    maxValue: Float,
    progress: Float
) {
    val markCount = 11 // 主刻度数量
    val subMarkCount = 5 // 每个主刻度间的子刻度数量

    repeat(markCount) { i ->
        val angle = startAngle + (sweepAngle / (markCount - 1)) * i
        val isVisible = (i.toFloat() / (markCount - 1)) <= progress

        if (isVisible) {
            val markLength = 12.dp.toPx()
            val markStartRadius = radius - markLength
            val markEndRadius = radius

            val startX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * markStartRadius
            val startY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * markStartRadius
            val endX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * markEndRadius
            val endY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * markEndRadius

            drawLine(
                color = Color.Gray,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    // 绘制子刻度
    repeat((markCount - 1) * subMarkCount) { i ->
        val totalSubMarks = (markCount - 1) * subMarkCount
        val angle = startAngle + (sweepAngle / totalSubMarks) * i
        val isVisible = (i.toFloat() / totalSubMarks) <= progress

        if (isVisible && i % subMarkCount != 0) { // 跳过主刻度位置
            val markLength = 6.dp.toPx()
            val markStartRadius = radius - markLength
            val markEndRadius = radius

            val startX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * markStartRadius
            val startY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * markStartRadius
            val endX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * markEndRadius
            val endY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * markEndRadius

            drawLine(
                color = Color.Gray.copy(alpha = 0.5f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 迷你仪表盘组件（用于卡片内显示）
 */
@Composable
fun MiniGauge(
    value: Float,
    maxValue: Float = 100f,
    minValue: Float = 0f,
    modifier: Modifier = Modifier,
    color: Color = Color.Blue,
    themeConfig: A2UIThemeConfig
) {
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = if (themeConfig.enableAnimations) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        } else {
            tween(0)
        },
        label = "mini_gauge_animation"
    )

    Canvas(modifier = modifier.size(60.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2 - 4.dp.toPx()
        val strokeWidth = 6.dp.toPx()

        // 背景弧
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 数值弧
        val valueRatio = (animatedValue - minValue) / (maxValue - minValue)
        val valueSweepAngle = valueRatio * 180f

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = valueSweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}