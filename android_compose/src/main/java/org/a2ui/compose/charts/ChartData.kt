package org.a2ui.compose.charts

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图表类型枚举
 */
enum class ChartType {
    LINE, BAR, PIE, DONUT, AREA, SCATTER, CANDLESTICK, GAUGE
}

/**
 * 图表配置
 */
data class ChartConfig(
    val showGrid: Boolean = true,
    val showLabels: Boolean = true,
    val showLegend: Boolean = true,
    val animationEnabled: Boolean = true,
    val animationDuration: Int = 1000,
    val strokeWidth: Dp = 2.dp,
    val cornerRadius: Dp = 4.dp,
    val colors: List<Color> = defaultColors
) {
    companion object {
        val defaultColors = listOf(
            Color(0xFF2196F3), // Blue
            Color(0xFF4CAF50), // Green
            Color(0xFFF44336), // Red
            Color(0xFFFF9800), // Orange
            Color(0xFF9C27B0), // Purple
            Color(0xFF00BCD4), // Cyan
            Color(0xFFFFEB3B), // Yellow
            Color(0xFF795548)  // Brown
        )
    }
}

/**
 * 图表数据基类
 */
sealed class ChartData {
    /**
     * 折线图数据
     */
    data class LineData(
        val series: List<DataSeries>,
        val xLabels: List<String> = emptyList(),
        val yRange: Pair<Float, Float>? = null
    ) : ChartData()

    /**
     * 柱状图数据
     */
    data class BarData(
        val categories: List<String>,
        val values: List<Float>,
        val colors: List<Color> = emptyList(),
        val maxValue: Float? = null
    ) : ChartData()

    /**
     * 饼图数据
     */
    data class PieData(
        val slices: List<PieSlice>,
        val showPercentages: Boolean = true
    ) : ChartData()

    /**
     * K线图数据
     */
    data class CandlestickData(
        val candles: List<CandleData>,
        val timeLabels: List<String> = emptyList()
    ) : ChartData()

    /**
     * 仪表盘数据
     */
    data class GaugeData(
        val value: Float,
        val maxValue: Float = 100f,
        val minValue: Float = 0f,
        val ranges: List<GaugeRange> = emptyList(),
        val unit: String = ""
    ) : ChartData()
}

/**
 * 数据系列
 */
data class DataSeries(
    val name: String,
    val values: List<Float>,
    val color: Color = Color.Blue,
    val strokeWidth: Dp = 2.dp,
    val fillArea: Boolean = false,
    val showPoints: Boolean = false
)

/**
 * 饼图切片
 */
data class PieSlice(
    val label: String,
    val value: Float,
    val color: Color,
    val percentage: Float = 0f
)

/**
 * K线数据
 */
data class CandleData(
    val timestamp: Long,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Float = 0f
) {
    val isPositive: Boolean get() = close >= open
    val bodyHeight: Float get() = kotlin.math.abs(close - open)
    val shadowTop: Float get() = high
    val shadowBottom: Float get() = low
}

/**
 * 仪表盘范围
 */
data class GaugeRange(
    val start: Float,
    val end: Float,
    val color: Color,
    val label: String = ""
)

/**
 * 图表工具函数
 */
object ChartUtils {
    /**
     * 计算饼图切片百分比
     */
    fun calculatePiePercentages(slices: List<PieSlice>): List<PieSlice> {
        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        return slices.map { slice ->
            slice.copy(percentage = if (total > 0) (slice.value / total) * 100f else 0f)
        }
    }

    /**
     * 自动计算Y轴范围
     */
    fun calculateYRange(values: List<Float>, padding: Float = 0.1f): Pair<Float, Float> {
        if (values.isEmpty()) return 0f to 100f

        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 100f
        val range = max - min
        val paddingValue = range * padding

        return (min - paddingValue) to (max + paddingValue)
    }

    /**
     * 生成默认颜色
     */
    fun generateColors(count: Int): List<Color> {
        val baseColors = ChartConfig.defaultColors
        return (0 until count).map { index ->
            baseColors[index % baseColors.size]
        }
    }

    /**
     * 格式化数值
     */
    fun formatValue(value: Float, decimals: Int = 2): String {
        return "%.${decimals}f".format(value)
    }

    /**
     * 格式化百分比
     */
    fun formatPercentage(value: Float, decimals: Int = 1): String {
        return "${formatValue(value, decimals)}%"
    }
}