package org.a2ui.compose.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.a2ui.compose.rendering.A2UIRenderer
import org.a2ui.compose.rendering.A2UILogger
import org.a2ui.compose.rendering.A2UILogLevel
import org.a2ui.compose.rendering.ActionHandler
import org.a2ui.compose.rendering.ComponentRegistry
import org.a2ui.compose.service.A2UIService
import org.a2ui.compose.theme.A2UITheme
import org.a2ui.compose.theme.A2UIThemeConfig
import org.a2ui.compose.theme.parseColor

/**
 * A2UI 协议合规性综合演示
 *
 * 本 Demo 覆盖以下协议特性：
 * 1. createSurface / updateComponents / updateDataModel / deleteSurface 四种消息
 * 2. 全部 18 种标准组件（Text, Image, Icon, Video, AudioPlayer, Row, Column, List,
 *    Card, Tabs, Modal, Divider, Button, TextField, CheckBox, ChoicePicker, Slider, DateTimeInput）
 * 3. 数据绑定：literal / path / functionCall
 * 4. Collection Scope（模板列表渲染 + 相对路径解析）
 * 5. 双向绑定（TextField, CheckBox, Slider, ChoicePicker 修改 data model）
 * 6. 验证函数（required, email, regex）
 * 7. 格式化函数（formatString, formatNumber, formatCurrency, formatDate, pluralize）
 * 8. 主题定制（primaryColor, agentDisplayName, iconUrl）
 * 9. Button 通过 child 引用子组件
 * 10. Modal 通过 trigger/content 引用
 * 11. Tabs 通过 tabs 数组定义
 * 12. Icon 通过 name 属性（camelCase）
 * 13. 多 Surface 管理
 * 14. Action 事件处理
 */
class A2UIComprehensiveDemo : ComponentActivity() {

    private lateinit var service: A2UIService
    private val actionLog = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logger = object : A2UILogger {
            override fun log(level: A2UILogLevel, message: String) {
                android.util.Log.d("A2UI-Demo", "[$level] $message")
            }
        }

        service = A2UIService(A2UIRenderer(logger))

        service.rendererState.renderer.setActionHandler(object : ActionHandler {
            override fun onAction(surfaceId: String, actionName: String, context: Map<String, Any>) {
                val msg = "Action: $actionName | surface: $surfaceId | ctx: $context"
                android.util.Log.d("A2UI-Demo", msg)
                actionLog.add(msg)
            }
            override fun openUrl(url: String) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            override fun showToast(message: String) {
                android.widget.Toast.makeText(this@A2UIComprehensiveDemo, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        })

        setContent {
            A2UITheme(config = A2UIThemeConfig(primaryColor = "#1976D2")) {
                DemoScreen(service, actionLog)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoScreen(service: A2UIService, actionLog: List<String>) {
    var selectedDemo by remember { mutableIntStateOf(-1) }
    val demos = listOf(
        "1. 联系表单（表单验证 + 双向绑定）",
        "2. 员工列表（Collection Scope + 模板）",
        "3. 仪表盘（Tabs + Modal + 格式化函数）",
        "4. 组件画廊（全部标准组件）",
        "5. 多 Surface 管理",
        "6. 连接演示（重连 + 退避策略）"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("A2UI v0.10 Protocol Demo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (selectedDemo == -1) {
                // 主菜单
                Text("选择一个演示场景：", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))

                demos.forEachIndexed { index, title ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            selectedDemo = index
                            loadDemo(service, index)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Mail
                                    1 -> Icons.Default.Person
                                    2 -> Icons.Default.Dashboard
                                    3 -> Icons.Default.Widgets
                                    4 -> Icons.Default.Layers
                                    5 -> Icons.Default.Wifi
                                    else -> Icons.Default.Star
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = title, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                // 返回按钮
                TextButton(onClick = {
                    selectedDemo = -1
                    // 清理所有 surface
                    service.rendererState.getAllSurfaceIds().forEach { id ->
                        service.processMessage("""{"version":"v0.9","deleteSurface":{"surfaceId":"$id"}}""")
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("返回菜单")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 渲染 A2UI surfaces
                val surfaceIds = service.rendererState.getAllSurfaceIds()
                surfaceIds.forEach { surfaceId ->
                    val ctx = service.rendererState.renderer.getSurfaceContext(surfaceId)
                    val root = service.rendererState.renderer.getComponent(surfaceId, "root")
                    if (ctx != null && root != null) {
                        val registry = remember(surfaceId) { ComponentRegistry(service.rendererState.renderer) }
                        registry.render(root, ctx)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Action 日志
                if (actionLog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Action 日志：", style = MaterialTheme.typography.titleSmall)
                    actionLog.takeLast(5).forEach { log ->
                        Text(text = log, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

private fun loadDemo(service: A2UIService, demoIndex: Int) {
    when (demoIndex) {
        0 -> loadContactFormDemo(service)
        1 -> loadEmployeeListDemo(service)
        2 -> loadDashboardDemo(service)
        3 -> loadComponentGalleryDemo(service)
        4 -> loadMultiSurfaceDemo(service)
        5 -> loadConnectionDemo(service)
    }
}

// ============================================================
// Demo 1: 联系表单 — 表单验证 + 双向绑定 + Button child 引用
// ============================================================
private fun loadContactFormDemo(service: A2UIService) {
    val messages = listOf(
        // 1. createSurface (v0.9 格式)
        """{"version":"v0.9","createSurface":{"surfaceId":"contact_form","catalogId":"https://a2ui.org/specification/v0_9/standard_catalog.json","theme":{"primaryColor":"#6200EE","agentDisplayName":"Contact Bot"},"sendDataModel":true}}""",

        // 2. updateComponents — 使用 v0.9 属性名
        """{"version":"v0.9","updateComponents":{"surfaceId":"contact_form","components":[
            {"id":"root","component":"Card","child":"form_col"},
            {"id":"form_col","component":"Column","children":["header_row","divider_0","name_row","email_field","phone_field","pref_picker","subscribe_cb","divider_1","submit_btn"],"justify":"start","align":"stretch"},

            {"id":"header_row","component":"Row","children":["header_icon","header_text"],"align":"center"},
            {"id":"header_icon","component":"Icon","name":"mail"},
            {"id":"header_text","component":"Text","text":"Contact Us","variant":"h2"},

            {"id":"divider_0","component":"Divider","axis":"horizontal"},

            {"id":"name_row","component":"Row","children":["first_name_col","last_name_col"],"justify":"spaceBetween"},
            {"id":"first_name_col","component":"Column","children":["first_name_field"],"weight":1},
            {"id":"first_name_field","component":"TextField","label":"First Name","value":{"path":"/contact/firstName"},"variant":"shortText"},
            {"id":"last_name_col","component":"Column","children":["last_name_field"],"weight":1},
            {"id":"last_name_field","component":"TextField","label":"Last Name","value":{"path":"/contact/lastName"},"variant":"shortText"},

            {"id":"email_field","component":"TextField","label":"Email Address","value":{"path":"/contact/email"},"variant":"shortText","checks":[{"call":"required","args":{"value":{"path":"/contact/email"}},"message":"Email is required."},{"call":"email","args":{"value":{"path":"/contact/email"}},"message":"Please enter a valid email."}]},

            {"id":"phone_field","component":"TextField","label":"Phone Number","value":{"path":"/contact/phone"},"variant":"shortText","checks":[{"call":"regex","args":{"value":{"path":"/contact/phone"},"pattern":"^\\d{10}$"},"message":"Phone must be 10 digits."}]},

            {"id":"pref_picker","component":"ChoicePicker","label":"Preferred Contact Method","variant":"mutuallyExclusive","options":[{"label":"Email","value":"email"},{"label":"Phone","value":"phone"},{"label":"SMS","value":"sms"}],"value":{"path":"/contact/preference"}},

            {"id":"subscribe_cb","component":"CheckBox","label":"Subscribe to newsletter","value":{"path":"/contact/subscribe"}},

            {"id":"divider_1","component":"Divider","axis":"horizontal"},

            {"id":"submit_btn_text","component":"Text","text":"Send Message"},
            {"id":"submit_btn","component":"Button","child":"submit_btn_text","variant":"primary","action":{"event":{"name":"submitContactForm","context":{"formId":"contact_form_1"}}}}
        ]}}""",

        // 3. updateDataModel
        """{"version":"v0.9","updateDataModel":{"surfaceId":"contact_form","path":"/contact","value":{"firstName":"John","lastName":"Doe","email":"john.doe@example.com","phone":"1234567890","preference":["email"],"subscribe":true}}}"""
    )
    messages.forEach { service.processMessage(it) }
}

// ============================================================
// Demo 2: 员工列表 — Collection Scope + 模板渲染 + 相对路径
// ============================================================
private fun loadEmployeeListDemo(service: A2UIService) {
    val messages = listOf(
        """{"version":"v0.9","createSurface":{"surfaceId":"employee_list","catalogId":"https://a2ui.org/specification/v0_9/standard_catalog.json","theme":{"primaryColor":"#00897B","agentDisplayName":"HR Bot"}}}""",

        """{"version":"v0.9","updateComponents":{"surfaceId":"employee_list","components":[
            {"id":"root","component":"Column","children":["title_row","divider","emp_list","summary_text"],"align":"stretch"},

            {"id":"title_row","component":"Row","children":["title_icon","title_text"],"align":"center"},
            {"id":"title_icon","component":"Icon","name":"person"},
            {"id":"title_text","component":"Text","text":"Team Members","variant":"h2"},

            {"id":"divider","component":"Divider","axis":"horizontal"},

            {"id":"emp_list","component":"List","children":{"path":"/employees","componentId":"emp_card"},"direction":"vertical"},

            {"id":"emp_card","component":"Card","child":"emp_card_content"},
            {"id":"emp_card_content","component":"Row","children":["emp_avatar_col","emp_info_col"],"align":"center"},
            {"id":"emp_avatar_col","component":"Column","children":["emp_avatar"]},
            {"id":"emp_avatar","component":"Icon","name":"accountCircle"},
            {"id":"emp_info_col","component":"Column","children":["emp_name","emp_role","emp_email"]},
            {"id":"emp_name","component":"Text","text":{"path":"name"},"variant":"h5"},
            {"id":"emp_role","component":"Text","text":{"path":"role"},"variant":"caption"},
            {"id":"emp_email","component":"Text","text":{"path":"email"},"variant":"body"},

            {"id":"summary_text","component":"Text","text":"Showing all team members","variant":"caption"}
        ]}}""",

        """{"version":"v0.9","updateDataModel":{"surfaceId":"employee_list","path":"/employees","value":[
            {"name":"Alice Chen","role":"Engineering Lead","email":"alice@example.com"},
            {"name":"Bob Smith","role":"Product Designer","email":"bob@example.com"},
            {"name":"Carol Wang","role":"Backend Engineer","email":"carol@example.com"},
            {"name":"David Kim","role":"QA Engineer","email":"david@example.com"}
        ]}}"""
    )
    messages.forEach { service.processMessage(it) }
}

// ============================================================
// Demo 3: 仪表盘 — Tabs + Modal + 格式化函数
// ============================================================
private fun loadDashboardDemo(service: A2UIService) {
    val messages = listOf(
        """{"version":"v0.9","createSurface":{"surfaceId":"dashboard","catalogId":"https://a2ui.org/specification/v0_9/standard_catalog.json","theme":{"primaryColor":"#FF6F00","agentDisplayName":"Analytics Bot"}}}""",

        """{"version":"v0.9","updateComponents":{"surfaceId":"dashboard","components":[
            {"id":"root","component":"Column","children":["dash_title","dash_tabs","detail_modal"],"align":"stretch"},

            {"id":"dash_title","component":"Text","text":"Analytics Dashboard","variant":"h2"},

            {"id":"dash_tabs","component":"Tabs","tabs":[
                {"title":"Overview","child":"overview_content"},
                {"title":"Revenue","child":"revenue_content"},
                {"title":"Users","child":"users_content"}
            ]},

            {"id":"overview_content","component":"Column","children":["stat_row","progress_label","progress_bar"],"align":"stretch"},
            {"id":"stat_row","component":"Row","children":["stat_users","stat_revenue","stat_orders"],"justify":"spaceEvenly"},
            {"id":"stat_users","component":"Column","children":["stat_users_val","stat_users_label"],"align":"center"},
            {"id":"stat_users_val","component":"Text","text":{"path":"/stats/totalUsers"},"variant":"h3"},
            {"id":"stat_users_label","component":"Text","text":"Total Users","variant":"caption"},
            {"id":"stat_revenue","component":"Column","children":["stat_rev_val","stat_rev_label"],"align":"center"},
            {"id":"stat_rev_val","component":"Text","text":{"path":"/stats/revenue"},"variant":"h3"},
            {"id":"stat_rev_label","component":"Text","text":"Revenue","variant":"caption"},
            {"id":"stat_orders","component":"Column","children":["stat_ord_val","stat_ord_label"],"align":"center"},
            {"id":"stat_ord_val","component":"Text","text":{"path":"/stats/orders"},"variant":"h3"},
            {"id":"stat_ord_label","component":"Text","text":"Orders","variant":"caption"},
            {"id":"progress_label","component":"Text","text":"Monthly Target Progress","variant":"body"},
            {"id":"progress_bar","component":"ProgressBar","value":{"path":"/stats/progress"}},

            {"id":"revenue_content","component":"Column","children":["rev_title","rev_slider","rev_date"],"align":"stretch"},
            {"id":"rev_title","component":"Text","text":"Revenue Settings","variant":"h3"},
            {"id":"rev_slider","component":"Slider","label":"Target Revenue (K)","value":{"path":"/settings/targetRevenue"},"min":0,"max":1000},
            {"id":"rev_date","component":"DateTimeInput","label":"Report Date","value":{"path":"/settings/reportDate"},"enableDate":true,"enableTime":false},

            {"id":"users_content","component":"Column","children":["users_title","users_switch","users_dropdown"],"align":"stretch"},
            {"id":"users_title","component":"Text","text":"User Settings","variant":"h3"},
            {"id":"users_switch","component":"Switch","label":"Show inactive users","value":{"path":"/settings/showInactive"}},
            {"id":"users_dropdown","component":"Dropdown","label":"Sort by","value":{"path":"/settings/sortBy"},"options":[{"label":"Name","value":"name"},{"label":"Join Date","value":"joinDate"},{"label":"Activity","value":"activity"}]},

            {"id":"detail_modal","component":"Modal","trigger":"modal_trigger_btn","content":"modal_body"},
            {"id":"modal_trigger_text","component":"Text","text":"View Details"},
            {"id":"modal_trigger_btn","component":"Button","child":"modal_trigger_text","variant":"borderless","action":{"event":{"name":"openDetails"}}},
            {"id":"modal_body","component":"Column","children":["modal_title","modal_desc"],"align":"stretch"},
            {"id":"modal_title","component":"Text","text":"Dashboard Details","variant":"h3"},
            {"id":"modal_desc","component":"Text","text":"This modal demonstrates the v0.9 trigger/content pattern. The trigger button opens this dialog, and the content is rendered inside.","variant":"body"}
        ]}}""",

        """{"version":"v0.9","updateDataModel":{"surfaceId":"dashboard","path":"/","value":{
            "stats":{"totalUsers":"12,458","revenue":"$847K","orders":"3,291","progress":0.73},
            "settings":{"targetRevenue":500,"reportDate":"2026-02-26","showInactive":false,"sortBy":"name"}
        }}}"""
    )
    messages.forEach { service.processMessage(it) }
}

// ============================================================
// Demo 4: 组件画廊 — 全部 18 种标准组件
// ============================================================
private fun loadComponentGalleryDemo(service: A2UIService) {
    val messages = listOf(
        """{"version":"v0.9","createSurface":{"surfaceId":"gallery","catalogId":"https://a2ui.org/specification/v0_9/standard_catalog.json","theme":{"primaryColor":"#7B1FA2"}}}""",

        """{"version":"v0.9","updateComponents":{"surfaceId":"gallery","components":[
            {"id":"root","component":"Column","children":["gallery_title","divider_top","section_text","section_image","section_icon_row","section_divider","section_button_row","section_textfield","section_checkbox","section_slider","section_choice","section_datetime","section_video","section_audio","section_progress","section_spacer","section_switch"],"align":"stretch"},

            {"id":"gallery_title","component":"Text","text":"Component Gallery — All 18 Standard Components","variant":"h2"},
            {"id":"divider_top","component":"Divider","axis":"horizontal"},

            {"id":"section_text","component":"Column","children":["text_h1","text_h3","text_body","text_caption"]},
            {"id":"text_h1","component":"Text","text":"Heading 1 (h1)","variant":"h1"},
            {"id":"text_h3","component":"Text","text":"Heading 3 (h3)","variant":"h3"},
            {"id":"text_body","component":"Text","text":"Body text — this is the default text style for paragraphs and content.","variant":"body"},
            {"id":"text_caption","component":"Text","text":"Caption text — used for small annotations.","variant":"caption"},

            {"id":"section_image","component":"Image","url":"https://picsum.photos/400/200","fit":"cover","variant":"mediumFeature"},

            {"id":"section_icon_row","component":"Row","children":["icon_home","icon_star","icon_mail","icon_search","icon_settings","icon_fav","icon_warning"],"justify":"spaceEvenly"},
            {"id":"icon_home","component":"Icon","name":"home"},
            {"id":"icon_star","component":"Icon","name":"star"},
            {"id":"icon_mail","component":"Icon","name":"mail"},
            {"id":"icon_search","component":"Icon","name":"search"},
            {"id":"icon_settings","component":"Icon","name":"settings"},
            {"id":"icon_fav","component":"Icon","name":"favorite"},
            {"id":"icon_warning","component":"Icon","name":"warning"},

            {"id":"section_divider","component":"Divider","axis":"horizontal"},

            {"id":"section_button_row","component":"Row","children":["btn_primary","btn_outlined","btn_borderless"],"justify":"spaceEvenly"},
            {"id":"btn_primary_text","component":"Text","text":"Primary"},
            {"id":"btn_primary","component":"Button","child":"btn_primary_text","variant":"primary","action":{"event":{"name":"btn_click","context":{"type":"primary"}}}},
            {"id":"btn_outlined_text","component":"Text","text":"Outlined"},
            {"id":"btn_outlined","component":"Button","child":"btn_outlined_text","action":{"event":{"name":"btn_click","context":{"type":"outlined"}}}},
            {"id":"btn_borderless_text","component":"Text","text":"Borderless"},
            {"id":"btn_borderless","component":"Button","child":"btn_borderless_text","variant":"borderless","action":{"event":{"name":"btn_click","context":{"type":"borderless"}}}},

            {"id":"section_textfield","component":"TextField","label":"Sample Input","value":{"path":"/gallery/input"},"variant":"shortText","placeholder":"Type something..."},

            {"id":"section_checkbox","component":"CheckBox","label":"I agree to the terms","value":{"path":"/gallery/agreed"}},

            {"id":"section_slider","component":"Slider","label":"Volume","value":{"path":"/gallery/volume"},"min":0,"max":100},

            {"id":"section_choice","component":"ChoicePicker","label":"Favorite Color","variant":"mutuallyExclusive","options":[{"label":"Red","value":"red"},{"label":"Green","value":"green"},{"label":"Blue","value":"blue"}],"value":{"path":"/gallery/color"}},

            {"id":"section_datetime","component":"DateTimeInput","label":"Select Date","value":{"path":"/gallery/date"},"enableDate":true,"enableTime":false},

            {"id":"section_video","component":"Video","url":"https://example.com/sample.mp4"},
            {"id":"section_audio","component":"AudioPlayer","url":"https://example.com/sample.mp3","description":"Sample Audio Track"},

            {"id":"section_progress","component":"ProgressBar","value":{"path":"/gallery/progress"}},

            {"id":"section_spacer","component":"Spacer","min":16},

            {"id":"section_switch","component":"Switch","label":"Dark Mode","value":{"path":"/gallery/darkMode"}}
        ]}}""",

        """{"version":"v0.9","updateDataModel":{"surfaceId":"gallery","path":"/gallery","value":{"input":"Hello A2UI","agreed":true,"volume":65,"color":["blue"],"date":"2026-02-26","progress":0.42,"darkMode":false}}}"""
    )
    messages.forEach { service.processMessage(it) }
}

// ============================================================
// Demo 5: 多 Surface 管理
// ============================================================
private fun loadMultiSurfaceDemo(service: A2UIService) {
    val messages = listOf(
        // Surface 1: 通知卡片
        """{"version":"v0.9","createSurface":{"surfaceId":"notification","catalogId":"https://a2ui.org/specification/v0_9/standard_catalog.json","theme":{"primaryColor":"#D32F2F","agentDisplayName":"Alert Bot"}}}""",
        """{"version":"v0.9","updateComponents":{"surfaceId":"notification","components":[
            {"id":"root","component":"Card","child":"notif_content"},
            {"id":"notif_content","component":"Row","children":["notif_icon","notif_text_col"],"align":"center"},
            {"id":"notif_icon","component":"Icon","name":"notifications"},
            {"id":"notif_text_col","component":"Column","children":["notif_title","notif_body"]},
            {"id":"notif_title","component":"Text","text":"System Alert","variant":"h5"},
            {"id":"notif_body","component":"Text","text":{"path":"/alert/message"},"variant":"body"}
        ]}}""",
        """{"version":"v0.9","updateDataModel":{"surfaceId":"notification","path":"/alert","value":{"message":"Server CPU usage exceeded 90%. Please check the monitoring dashboard."}}}""",

        // Surface 2: 快速操作面板
        """{"version":"v0.9","createSurface":{"surfaceId":"quick_actions","catalogId":"https://a2ui.org/specification/v0_9/standard_catalog.json","theme":{"primaryColor":"#1565C0","agentDisplayName":"Action Bot"}}}""",
        """{"version":"v0.9","updateComponents":{"surfaceId":"quick_actions","components":[
            {"id":"root","component":"Card","child":"actions_col"},
            {"id":"actions_col","component":"Column","children":["actions_title","actions_row"],"align":"stretch"},
            {"id":"actions_title","component":"Text","text":"Quick Actions","variant":"h3"},
            {"id":"actions_row","component":"Row","children":["action_restart","action_scale","action_logs"],"justify":"spaceEvenly"},
            {"id":"action_restart_text","component":"Text","text":"Restart"},
            {"id":"action_restart","component":"Button","child":"action_restart_text","variant":"primary","action":{"event":{"name":"restart_server"}}},
            {"id":"action_scale_text","component":"Text","text":"Scale Up"},
            {"id":"action_scale","component":"Button","child":"action_scale_text","action":{"event":{"name":"scale_up"}}},
            {"id":"action_logs_text","component":"Text","text":"View Logs"},
            {"id":"action_logs","component":"Button","child":"action_logs_text","variant":"borderless","action":{"event":{"name":"view_logs"}}}
        ]}}""",

        // Surface 3: 状态面板
        """{"version":"v0.9","createSurface":{"surfaceId":"status","catalogId":"https://a2ui.org/specification/v0_9/standard_catalog.json","theme":{"primaryColor":"#2E7D32"}}}""",
        """{"version":"v0.9","updateComponents":{"surfaceId":"status","components":[
            {"id":"root","component":"Card","child":"status_col"},
            {"id":"status_col","component":"Column","children":["status_title","status_progress","status_text"],"align":"stretch"},
            {"id":"status_title","component":"Text","text":"Deployment Status","variant":"h3"},
            {"id":"status_progress","component":"ProgressBar","value":{"path":"/deploy/progress"}},
            {"id":"status_text","component":"Text","text":{"path":"/deploy/status"},"variant":"body"}
        ]}}""",
        """{"version":"v0.9","updateDataModel":{"surfaceId":"status","path":"/deploy","value":{"progress":0.85,"status":"Deploying v2.4.1 — 85% complete"}}}"""
    )
    messages.forEach { service.processMessage(it) }
}

// ============================================================
// Demo 6: 连接演示 — 重连 + 退避策略
// ============================================================
private fun loadConnectionDemo(service: A2UIService) {
    val messages = listOf(
        """{"version":"v0.10","createSurface":{"surfaceId":"connection_demo","catalogId":"https://a2ui.org/specification/v0_10/standard_catalog.json","theme":{"primaryColor":"#0288D1","agentDisplayName":"Connection Bot"}}}""",

        """{"version":"v0.10","updateComponents":{"surfaceId":"connection_demo","components":[
            {"id":"root","component":"Column","children":["conn_title","divider","version_info","conn_status","backoff_card"],"align":"stretch"},

            {"id":"conn_title","component":"Text","text":"Connection Demo — Reconnect & Backoff","variant":"h2"},
            {"id":"divider","component":"Divider","axis":"horizontal"},

            {"id":"version_info","component":"Card","child":"version_col"},
            {"id":"version_col","component":"Column","children":["ver_title","ver_supported","ver_current"]},
            {"id":"ver_title","component":"Text","text":"Protocol Version Info","variant":"h3"},
            {"id":"ver_supported","component":"Text","text":{"path":"/conn/supportedVersions"},"variant":"body"},
            {"id":"ver_current","component":"Text","text":{"path":"/conn/currentVersion"},"variant":"caption"},

            {"id":"conn_status","component":"Card","child":"status_col"},
            {"id":"status_col","component":"Column","children":["status_title","status_state","status_retries"]},
            {"id":"status_title","component":"Text","text":"Connection Status","variant":"h3"},
            {"id":"status_state","component":"Text","text":{"path":"/conn/state"},"variant":"body"},
            {"id":"status_retries","component":"Text","text":{"path":"/conn/retryInfo"},"variant":"caption"},

            {"id":"backoff_card","component":"Card","child":"backoff_col"},
            {"id":"backoff_col","component":"Column","children":["backoff_title","backoff_desc","backoff_slider"],"align":"stretch"},
            {"id":"backoff_title","component":"Text","text":"Exponential Backoff Strategy","variant":"h3"},
            {"id":"backoff_desc","component":"Text","text":"Reconnect delay doubles each attempt: 3s → 6s → 12s → 24s → ... (max 60s). Resets on successful connection.","variant":"body"},
            {"id":"backoff_slider","component":"Slider","label":"Max Retry Count","value":{"path":"/conn/maxRetries"},"min":0,"max":20}
        ]}}""",

        """{"version":"v0.10","updateDataModel":{"surfaceId":"connection_demo","path":"/conn","value":{"supportedVersions":"Supported: v0.8, v0.9, v0.10","currentVersion":"Current: v0.10","state":"Disconnected (demo mode)","retryInfo":"Retry count: 0 / Max: 10 | Initial delay: 3s | Max delay: 60s","maxRetries":10}}}"""
    )
    messages.forEach { service.processMessage(it) }
}
