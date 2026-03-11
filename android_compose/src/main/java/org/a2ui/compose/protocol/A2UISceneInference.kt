package org.a2ui.compose.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

enum class A2UIDynamicScene(
    val displayName: String,
    val recommendedLayout: String,
) {
    WEATHER("天气卡片", "单卡 + 指标列"),
    ROUTE("路线卡片", "路线摘要卡或步骤卡"),
    POI_LIST("地点列表卡片", "列表卡或分组列表"),
    VEHICLE_STATUS("车辆状态卡片", "状态卡或组合面板"),
    DIAGNOSTIC("诊断告警卡片", "告警卡或检查列表"),
    // 新增股票和金融场景
    STOCK("股票信息卡片", "毛玻璃卡片 + 实时数据指标"),
    FINANCIAL("金融数据面板", "立体卡片组合 + 趋势图表"),
    // 新增图表和分析场景
    CHART("数据图表", "动态图表组件 + 实时动画"),
    GAUGE("仪表盘指标", "圆形仪表盘 + 范围指示"),
    ANALYTICS("数据分析面板", "多图表组合 + 交互式展示"),
    CANDLESTICK("K线图表", "股票K线图 + 技术指标"),
}

data class A2UISceneHint(
    val scene: A2UIDynamicScene,
    val reason: String,
)

object A2UISceneInference {

    @JvmStatic
    fun inferScenes(
        toolCalls: List<A2UIToolCall>,
        toolResults: Map<String, String>,
    ): List<A2UISceneHint> {
        val hints = LinkedHashMap<A2UIDynamicScene, A2UISceneHint>()
        val handledToolCallIds = mutableSetOf<String>()

        toolCalls.forEach { toolCall ->
            handledToolCallIds += toolCall.id
            inferFromPayload(
                hints = hints,
                toolName = toolCall.name,
                payload = toolResults[toolCall.id].orEmpty(),
            )
        }

        toolResults
            .filterKeys { it !in handledToolCallIds }
            .forEach { (_, payload) ->
                inferFromPayload(
                    hints = hints,
                    toolName = null,
                    payload = payload,
                )
            }

        return hints.values.toList()
    }

    private fun inferFromPayload(
        hints: LinkedHashMap<A2UIDynamicScene, A2UISceneHint>,
        toolName: String?,
        payload: String,
    ) {
        val normalizedToolName = toolName?.lowercase().orEmpty()
        val keySet = collectJsonKeys(payload)

        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.WEATHER,
            matched = normalizedToolName.contains("weather") ||
                keySet.intersects(
                    "current_weather",
                    "weather_code",
                    "temperature",
                    "temperature_2m",
                    "temperature_2m_max",
                    "temperature_2m_min",
                    "humidity",
                    "wind_speed",
                    "wind_speed_10m",
                    "precipitation",
                    "hourly",
                    "daily",
                ),
            reason = "工具名或结果字段呈现天气概况、温度、风速或逐时/逐日气象数据。",
        )

        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.ROUTE,
            matched = normalizedToolName.contains("route") ||
                normalizedToolName.contains("navigation") ||
                keySet.intersects(
                    "route_id",
                    "eta_minutes",
                    "total_time_minutes",
                    "resume_in_minutes",
                    "distance_km",
                    "total_distance_km",
                    "destination",
                    "origin",
                    "congestion_level",
                    "waypoint_count",
                    "route_preference",
                ),
            reason = "工具结果包含起终点、ETA、距离、拥堵等级或路线标识。",
        )

        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.POI_LIST,
            matched = normalizedToolName.contains("poi") ||
                normalizedToolName.contains("restaurant") ||
                normalizedToolName.contains("attraction") ||
                keySet.intersects(
                    "results",
                    "pois",
                    "restaurants",
                    "attractions",
                    "address",
                    "distance_m",
                    "distance_km",
                    "rating",
                    "category",
                    "count",
                ),
            reason = "工具结果更像地点、餐厅或候选项列表，需要列表化展示。",
        )

        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.VEHICLE_STATUS,
            matched = normalizedToolName.contains("vehicle") ||
                normalizedToolName.contains("battery") ||
                normalizedToolName.contains("fuel") ||
                normalizedToolName.contains("maintenance") ||
                keySet.intersects(
                    "systems",
                    "overall_score",
                    "fuel_level_percent",
                    "fuel_liters",
                    "health_percent",
                    "voltage",
                    "oil_life_percent",
                    "brake_pad_percent",
                    "estimated_range_km",
                    "next_maintenance_km",
                    "next_maintenance_date",
                ),
            reason = "工具结果包含车辆健康、续航、油量、电池或保养状态。",
        )

        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.DIAGNOSTIC,
            matched = normalizedToolName.contains("obd") ||
                normalizedToolName.contains("diagnostic") ||
                keySet.intersects(
                    "codes",
                    "severity",
                    "fault_code",
                    "warning",
                    "error",
                    "code",
                    "code_type",
                    "description",
                    "cleared",
                ),
            reason = "工具结果包含故障码、严重等级、清除结果或诊断告警信息。",
        )

        // 新增股票场景推理
        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.STOCK,
            matched = normalizedToolName.contains("stock") ||
                normalizedToolName.contains("share") ||
                normalizedToolName.contains("equity") ||
                keySet.intersects(
                    "price",
                    "stock_price",
                    "current_price",
                    "open_price",
                    "close_price",
                    "high_price",
                    "low_price",
                    "change",
                    "change_percent",
                    "volume",
                    "market_cap",
                    "pe_ratio",
                    "dividend",
                    "symbol",
                    "ticker",
                    "exchange",
                ),
            reason = "工具结果包含股票价格、涨跌幅、成交量、市值等股票相关数据。",
        )

        // 新增金融场景推理
        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.FINANCIAL,
            matched = normalizedToolName.contains("financial") ||
                normalizedToolName.contains("finance") ||
                normalizedToolName.contains("market") ||
                normalizedToolName.contains("investment") ||
                keySet.intersects(
                    "portfolio",
                    "assets",
                    "balance",
                    "profit",
                    "loss",
                    "revenue",
                    "earnings",
                    "financial_data",
                    "market_data",
                    "investment",
                    "fund",
                    "bond",
                    "currency",
                    "exchange_rate",
                    "interest_rate",
                ),
            reason = "工具结果包含投资组合、资产负债、收益损失等综合金融数据。",
        )

        // 新增图表场景推理
        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.CHART,
            matched = normalizedToolName.contains("chart") ||
                normalizedToolName.contains("graph") ||
                normalizedToolName.contains("plot") ||
                normalizedToolName.contains("trend") ||
                keySet.intersects(
                    "data_points",
                    "series",
                    "values",
                    "x_axis",
                    "y_axis",
                    "labels",
                    "dataset",
                    "time_series",
                    "trend_data",
                    "line_data",
                    "bar_data",
                    "chart_data",
                ),
            reason = "工具结果包含数据点、系列、轴标签等图表相关数据。",
        )

        // 新增仪表盘场景推理
        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.GAUGE,
            matched = normalizedToolName.contains("gauge") ||
                normalizedToolName.contains("meter") ||
                normalizedToolName.contains("indicator") ||
                normalizedToolName.contains("progress") ||
                keySet.intersects(
                    "percentage",
                    "progress",
                    "completion",
                    "score",
                    "rating",
                    "level",
                    "status",
                    "health",
                    "performance",
                    "efficiency",
                    "utilization",
                    "capacity",
                ),
            reason = "工具结果包含百分比、进度、评分等适合仪表盘显示的指标数据。",
        )

        // 新增K线图场景推理
        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.CANDLESTICK,
            matched = normalizedToolName.contains("candlestick") ||
                normalizedToolName.contains("ohlc") ||
                normalizedToolName.contains("kline") ||
                keySet.intersects(
                    "open",
                    "high",
                    "low",
                    "close",
                    "ohlc",
                    "candle",
                    "kline_data",
                    "trading_data",
                    "price_history",
                ),
            reason = "工具结果包含开高低收价格数据，适合K线图展示。",
        )

        // 新增数据分析场景推理
        maybeAddHint(
            hints = hints,
            scene = A2UIDynamicScene.ANALYTICS,
            matched = normalizedToolName.contains("analytics") ||
                normalizedToolName.contains("analysis") ||
                normalizedToolName.contains("statistics") ||
                normalizedToolName.contains("metrics") ||
                keySet.intersects(
                    "metrics",
                    "statistics",
                    "analysis",
                    "insights",
                    "summary",
                    "breakdown",
                    "distribution",
                    "correlation",
                    "comparison",
                    "performance_metrics",
                    "kpi",
                    "dashboard_data",
                ),
            reason = "工具结果包含分析指标、统计数据等适合综合分析面板展示。",
        )
    }

    private fun maybeAddHint(
        hints: LinkedHashMap<A2UIDynamicScene, A2UISceneHint>,
        scene: A2UIDynamicScene,
        matched: Boolean,
        reason: String,
    ) {
        if (matched && !hints.containsKey(scene)) {
            hints[scene] = A2UISceneHint(scene = scene, reason = reason)
        }
    }

    private fun collectJsonKeys(payload: String): Set<String> {
        val trimmedPayload = payload.trim()
        if (trimmedPayload.isBlank() || (!trimmedPayload.startsWith("{") && !trimmedPayload.startsWith("["))) {
            return emptySet()
        }

        return runCatching {
            val root = JsonParser.parseString(trimmedPayload)
            linkedSetOf<String>().apply { collectKeys(root, this) }
        }.getOrDefault(emptySet())
    }

    private fun collectKeys(element: JsonElement, keys: MutableSet<String>) {
        when {
            element.isJsonObject -> collectObjectKeys(element.asJsonObject, keys)
            element.isJsonArray -> collectArrayKeys(element.asJsonArray, keys)
        }
    }

    private fun collectObjectKeys(obj: JsonObject, keys: MutableSet<String>) {
        obj.entrySet().forEach { (key, value) ->
            keys += key
            collectKeys(value, keys)
        }
    }

    private fun collectArrayKeys(array: JsonArray, keys: MutableSet<String>) {
        array.forEach { collectKeys(it, keys) }
    }

    private fun Set<String>.intersects(vararg candidates: String): Boolean {
        return candidates.any { contains(it) }
    }
}
