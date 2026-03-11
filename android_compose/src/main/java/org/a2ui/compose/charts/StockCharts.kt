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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.a2ui.compose.theme.A2UIThemeConfig
import kotlin.math.*

/**
 * 股票K线图组件
 */
@Composable
fun StockCandlestickChart(
    data: ChartData.CandlestickData,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    themeConfig: A2UIThemeConfig
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (config.animationEnabled && themeConfig.enableAnimations) {
            tween(config.animationDuration, easing = FastOutSlowInEasing)
        } else {
            tween(0)
        },
        label = "candlestick_animation"
    )

    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodySmall

    Canvas(modifier = modifier.fillMaxSize()) {
        if (data.candles.isEmpty()) return@Canvas

        drawCandlestickChart(
            candles = data.candles,
            timeLabels = data.timeLabels,
            progress = animatedProgress,
            config = config,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            size = size
        )
    }
}

/**
 * 绘制K线图
 */
private fun DrawScope.drawCandlestickChart(
    candles: List<CandleData>,
    timeLabels: List<String>,
    progress: Float,
    config: ChartConfig,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle,
    size: Size
) {
    val padding = 40.dp.toPx()
    val chartWidth = size.width - padding * 2
    val chartHeight = size.height - padding * 2

    // 计算价格范围
    val allPrices = candles.flatMap { listOf(it.high, it.low) }
    val minPrice = allPrices.minOrNull() ?: 0f
    val maxPrice = allPrices.maxOrNull() ?: 100f
    val priceRange = maxPrice - minPrice

    // 绘制网格
    if (config.showGrid) {
        drawGrid(
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            padding = padding,
            minPrice = minPrice,
            maxPrice = maxPrice,
            candleCount = candles.size
        )
    }

    // 计算每根K线的宽度
    val candleWidth = chartWidth / candles.size
    val candleBodyWidth = candleWidth * 0.6f

    // 绘制K线
    candles.forEachIndexed { index, candle ->
        val animatedIndex = (progress * candles.size).coerceAtMost(index + 1f) - index
        if (animatedIndex > 0) {
            val x = padding + index * candleWidth + candleWidth / 2

            drawCandlestick(
                candle = candle,
                x = x,
                candleBodyWidth = candleBodyWidth,
                chartHeight = chartHeight,
                padding = padding,
                minPrice = minPrice,
                priceRange = priceRange,
                progress = animatedIndex,
                config = config
            )
        }
    }

    // 绘制标签
    if (config.showLabels && timeLabels.isNotEmpty()) {
        drawTimeLabels(
            timeLabels = timeLabels,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            padding = padding,
            textMeasurer = textMeasurer,
            textStyle = textStyle
        )
    }
}

/**
 * 绘制单根K线
 */
private fun DrawScope.drawCandlestick(
    candle: CandleData,
    x: Float,
    candleBodyWidth: Float,
    chartHeight: Float,
    padding: Float,
    minPrice: Float,
    priceRange: Float,
    progress: Float,
    config: ChartConfig
) {
    // 计算Y坐标
    fun priceToY(price: Float): Float {
        return padding + chartHeight - ((price - minPrice) / priceRange) * chartHeight
    }

    val openY = priceToY(candle.open)
    val closeY = priceToY(candle.close)
    val highY = priceToY(candle.high)
    val lowY = priceToY(candle.low)

    val color = if (candle.isPositive) {
        Color(0xFF4CAF50) // 绿色（上涨）
    } else {
        Color(0xFFF44336) // 红色（下跌）
    }

    // 绘制影线（上下影线）
    drawLine(
        color = color,
        start = Offset(x, highY),
        end = Offset(x, lowY),
        strokeWidth = config.strokeWidth.toPx() * progress
    )

    // 绘制实体
    val bodyTop = minOf(openY, closeY)
    val bodyBottom = maxOf(openY, closeY)
    val bodyHeight = (bodyBottom - bodyTop) * progress

    if (candle.isPositive) {
        // 阳线：空心矩形
        drawRect(
            color = color,
            topLeft = Offset(x - candleBodyWidth / 2, bodyTop),
            size = Size(candleBodyWidth, bodyHeight),
            style = Stroke(width = config.strokeWidth.toPx())
        )
    } else {
        // 阴线：实心矩形
        drawRect(
            color = color,
            topLeft = Offset(x - candleBodyWidth / 2, bodyTop),
            size = Size(candleBodyWidth, bodyHeight)
        )
    }
}

/**
 * 绘制网格
 */
private fun DrawScope.drawGrid(
    chartWidth: Float,
    chartHeight: Float,
    padding: Float,
    minPrice: Float,
    maxPrice: Float,
    candleCount: Int
) {
    val gridColor = Color.Gray.copy(alpha = 0.3f)
    val gridLines = 5

    // 水平网格线
    repeat(gridLines + 1) { i ->
        val y = padding + (chartHeight / gridLines) * i
        drawLine(
            color = gridColor,
            start = Offset(padding, y),
            end = Offset(padding + chartWidth, y),
            strokeWidth = 1.dp.toPx()
        )
    }

    // 垂直网格线
    val verticalLines = minOf(candleCount, 10)
    repeat(verticalLines + 1) { i ->
        val x = padding + (chartWidth / verticalLines) * i
        drawLine(
            color = gridColor,
            start = Offset(x, padding),
            end = Offset(x, padding + chartHeight),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * 绘制时间标签
 */
private fun DrawScope.drawTimeLabels(
    timeLabels: List<String>,
    chartWidth: Float,
    chartHeight: Float,
    padding: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle
) {
    val labelCount = minOf(timeLabels.size, 5)
    val step = if (timeLabels.size > labelCount) timeLabels.size / labelCount else 1

    repeat(labelCount) { i ->
        val labelIndex = i * step
        if (labelIndex < timeLabels.size) {
            val label = timeLabels[labelIndex]
            val x = padding + (chartWidth / (labelCount - 1)) * i
            val y = padding + chartHeight + 20.dp.toPx()

            val textLayoutResult = textMeasurer.measure(label, textStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(x - textLayoutResult.size.width / 2, y)
            )
        }
    }
}

/**
 * 实时折线图组件
 */
@Composable
fun RealTimeLineChart(
    data: ChartData.LineData,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    themeConfig: A2UIThemeConfig
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (config.animationEnabled && themeConfig.enableDataTransitions) {
            tween(config.animationDuration, easing = FastOutSlowInEasing)
        } else {
            tween(0)
        },
        label = "line_chart_animation"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (data.series.isEmpty()) return@Canvas

        drawLineChart(
            series = data.series,
            xLabels = data.xLabels,
            yRange = data.yRange,
            progress = animatedProgress,
            config = config,
            size = size
        )
    }
}

/**
 * 绘制折线图
 */
private fun DrawScope.drawLineChart(
    series: List<DataSeries>,
    xLabels: List<String>,
    yRange: Pair<Float, Float>?,
    progress: Float,
    config: ChartConfig,
    size: Size
) {
    val padding = 40.dp.toPx()
    val chartWidth = size.width - padding * 2
    val chartHeight = size.height - padding * 2

    // 计算Y轴范围
    val allValues = series.flatMap { it.values }
    val (minY, maxY) = yRange ?: ChartUtils.calculateYRange(allValues)
    val yRangeValue = maxY - minY

    series.forEach { dataSeries ->
        if (dataSeries.values.isEmpty()) return@forEach

        val path = Path()
        val points = mutableListOf<Offset>()

        // 计算点的坐标
        dataSeries.values.forEachIndexed { index, value ->
            val x = padding + (index.toFloat() / (dataSeries.values.size - 1)) * chartWidth
            val y = padding + chartHeight - ((value - minY) / yRangeValue) * chartHeight
            points.add(Offset(x, y))

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // 绘制路径（带动画）
        val animatedPath = Path()
        val pathMeasure = PathMeasure()
        pathMeasure.setPath(path, false)
        val pathLength = pathMeasure.length
        val animatedLength = pathLength * progress

        pathMeasure.getSegment(0f, animatedLength, animatedPath, true)

        // 绘制填充区域
        if (dataSeries.fillArea) {
            val fillPath = Path().apply {
                addPath(animatedPath)
                lineTo(points.last().x, padding + chartHeight)
                lineTo(points.first().x, padding + chartHeight)
                close()
            }

            drawPath(
                path = fillPath,
                color = dataSeries.color.copy(alpha = 0.3f)
            )
        }

        // 绘制线条
        drawPath(
            path = animatedPath,
            color = dataSeries.color,
            style = Stroke(
                width = dataSeries.strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 绘制数据点
        if (dataSeries.showPoints) {
            val visiblePointCount = (points.size * progress).toInt()
            points.take(visiblePointCount).forEach { point ->
                drawCircle(
                    color = dataSeries.color,
                    radius = 4.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = point
                )
            }
        }
    }
}