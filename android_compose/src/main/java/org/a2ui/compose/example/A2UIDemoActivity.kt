package org.a2ui.compose.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.a2ui.compose.rendering.*

class A2UIDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logger = object : A2UILogger {
            override fun log(level: A2UILogLevel, message: String) {
                Log.d("A2UI", "[$level] $message")
            }
        }

        setContent {
            val renderer = rememberA2UIRenderer(logger)

            val actionHandler = remember {
                object : ActionHandler {
                    override fun onAction(surfaceId: String, actionName: String, context: Map<String, Any>) {
                        Log.d("A2UI", "Action received: $actionName on surface: $surfaceId")
                        Log.d("A2UI", "Context: $context")

                        when (actionName) {
                            "submit_form" -> {
                                val formData = renderer.getDataModel(surfaceId)?.getDataSnapshot()
                                Log.d("A2UI", "Form submitted with data: $formData")
                            }
                            "navigate" -> {
                                val url = context["url"] as? String
                                url?.let {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                                }
                            }
                        }
                    }

                    override fun openUrl(url: String) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }

                    override fun showToast(message: String) {
                        android.widget.Toast.makeText(this@A2UIDemoActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            renderer.setActionHandler(actionHandler)

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    A2UIDemoScreen(renderer)
                }
            }
        }
    }
}

@Composable
fun A2UIDemoScreen(renderer: A2UIRenderer) {
    var currentDemo by remember { mutableStateOf("form") }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = when (currentDemo) {
            "form" -> 0
            "components" -> 1
            "list" -> 2
            else -> 0
        }) {
            Tab(
                selected = currentDemo == "form",
                onClick = { currentDemo = "form" },
                text = { Text("Form Demo") }
            )
            Tab(
                selected = currentDemo == "components",
                onClick = { currentDemo = "components" },
                text = { Text("Components") }
            )
            Tab(
                selected = currentDemo == "list",
                onClick = { currentDemo = "list" },
                text = { Text("List Demo") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (currentDemo) {
                "form" -> FormDemo(renderer)
                "components" -> ComponentsDemo(renderer)
                "list" -> ListDemo(renderer)
            }
        }
    }
}

@Composable
fun FormDemo(renderer: A2UIRenderer) {
    val surfaceId = "form_demo"
    var isInitialized by remember { mutableStateOf(false) }

    DisposableEffect(surfaceId) {
        if (!isInitialized) {
            initializeFormDemo(renderer, surfaceId)
            isInitialized = true
        }

        onDispose {
            try {
                renderer.processMessage("""{"version":"v0.10","deleteSurface":{"surfaceId":"$surfaceId"}}""")
            } catch (e: Exception) {
                // 忽略清理时的错误
            }
        }
    }

    RenderSurface(renderer, surfaceId)
}

private fun initializeFormDemo(renderer: A2UIRenderer, surfaceId: String) {
    listOf(
        """{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "$surfaceId",
                "catalogId": "https://a2ui.org/specification/v0_10/standard_catalog.json",
                "theme": {"primaryColor": "#6200EE"}
            }
        }""",
        """{
            "version": "v0.10",
            "updateComponents": {
                "surfaceId": "$surfaceId",
                "components": [
                    {"id": "root", "component": "Card", "child": "form_content"},
                    {"id": "form_content", "component": "Column", "children": ["title", "name_field", "email_field", "phone_field", "preference_picker", "subscribe_checkbox", "divider", "submit_button"], "justify": "start", "align": "stretch"},
                    {"id": "title", "component": "Text", "text": "Contact Form", "variant": "h2"},
                    {"id": "name_field", "component": "TextField", "label": "Full Name", "value": {"path": "/form/name"}, "placeholder": "Enter your full name"},
                    {"id": "email_field", "component": "TextField", "label": "Email Address", "value": {"path": "/form/email"}, "placeholder": "Enter your email"},
                    {"id": "phone_field", "component": "TextField", "label": "Phone Number", "value": {"path": "/form/phone"}, "placeholder": "Enter your phone number"},
                    {"id": "preference_picker", "component": "ChoicePicker", "value": {"path": "/form/preference"}, "options": [{"label": "Email", "value": "email"}, {"label": "Phone", "value": "phone"}, {"label": "Both", "value": "both"}]},
                    {"id": "subscribe_checkbox", "component": "CheckBox", "label": "Subscribe to newsletter", "value": {"path": "/form/subscribe"}},
                    {"id": "divider", "component": "Divider"},
                    {"id": "submit_button", "component": "Button", "text": "Submit", "variant": "primary", "action": {"event": {"name": "submit_form", "context": {"formId": "contact_form"}}}}
                ]
            }
        }""",
        """{
            "version": "v0.10",
            "updateDataModel": {
                "surfaceId": "$surfaceId",
                "path": "/form",
                "value": {"name": "John Doe", "email": "john@example.com", "phone": "123-456-7890", "preference": "email", "subscribe": true}
            }
        }"""
    ).forEach { message ->
        renderer.processMessage(message)
    }
}

@Composable
fun ComponentsDemo(renderer: A2UIRenderer) {
    val surfaceId = "components_demo"
    var isInitialized by remember { mutableStateOf(false) }

    DisposableEffect(surfaceId) {
        if (!isInitialized) {
            initializeComponentsDemo(renderer, surfaceId)
            isInitialized = true
        }

        onDispose {
            try {
                renderer.processMessage("""{"version":"v0.10","deleteSurface":{"surfaceId":"$surfaceId"}}""")
            } catch (e: Exception) {
                // 忽略清理时的错误
            }
        }
    }

    RenderSurface(renderer, surfaceId)
}

private fun initializeComponentsDemo(renderer: A2UIRenderer, surfaceId: String) {
    listOf(
        """{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "$surfaceId",
                "catalogId": "https://a2ui.org/specification/v0_10/standard_catalog.json"
            }
        }""",
        """{
            "version": "v0.10",
            "updateComponents": {
                "surfaceId": "$surfaceId",
                "components": [
                    {"id": "root", "component": "Column", "children": ["title", "text_section", "button_section", "input_section", "slider_section", "switch_section"], "justify": "start", "align": "stretch"},
                    {"id": "title", "component": "Text", "text": "Component Gallery", "variant": "h2"},
                    
                    {"id": "text_section", "component": "Card", "child": "text_content"},
                    {"id": "text_content", "component": "Column", "children": ["text_title", "text_h1", "text_h2", "text_body", "text_caption"]},
                    {"id": "text_title", "component": "Text", "text": "Text Components", "variant": "subtitle"},
                    {"id": "text_h1", "component": "Text", "text": "Headline 1", "variant": "h1"},
                    {"id": "text_h2", "component": "Text", "text": "Headline 2", "variant": "h2"},
                    {"id": "text_body", "component": "Text", "text": "This is body text. It is used for regular content.", "variant": "body"},
                    {"id": "text_caption", "component": "Text", "text": "Caption text for small labels", "variant": "caption"},
                    
                    {"id": "button_section", "component": "Card", "child": "button_content"},
                    {"id": "button_content", "component": "Column", "children": ["button_title", "button_row"]},
                    {"id": "button_title", "component": "Text", "text": "Button Components", "variant": "subtitle"},
                    {"id": "button_row", "component": "Row", "children": ["btn_primary", "btn_secondary", "btn_text"], "justify": "spaceBetween"},
                    {"id": "btn_primary", "component": "Button", "text": "Primary", "variant": "primary"},
                    {"id": "btn_secondary", "component": "Button", "text": "Secondary", "variant": "secondary"},
                    {"id": "btn_text", "component": "Button", "text": "Text", "variant": "text"},
                    
                    {"id": "input_section", "component": "Card", "child": "input_content"},
                    {"id": "input_content", "component": "Column", "children": ["input_title", "input_field", "dropdown_field"]},
                    {"id": "input_title", "component": "Text", "text": "Input Components", "variant": "subtitle"},
                    {"id": "input_field", "component": "TextField", "label": "Sample Input", "value": {"path": "/input/text"}, "placeholder": "Type something..."},
                    {"id": "dropdown_field", "component": "Dropdown", "label": "Select Option", "value": {"path": "/input/option"}, "options": [{"label": "Option 1", "value": "opt1"}, {"label": "Option 2", "value": "opt2"}, {"label": "Option 3", "value": "opt3"}]},
                    
                    {"id": "slider_section", "component": "Card", "child": "slider_content"},
                    {"id": "slider_content", "component": "Column", "children": ["slider_title", "slider_field"]},
                    {"id": "slider_title", "component": "Text", "text": "Slider Component", "variant": "subtitle"},
                    {"id": "slider_field", "component": "Slider", "value": {"path": "/slider/value"}, "min": 0, "max": 100, "step": 1, "label": "Volume"},
                    
                    {"id": "switch_section", "component": "Card", "child": "switch_content"},
                    {"id": "switch_content", "component": "Column", "children": ["switch_title", "switch_field"]},
                    {"id": "switch_title", "component": "Text", "text": "Switch Component", "variant": "subtitle"},
                    {"id": "switch_field", "component": "Switch", "label": "Enable notifications", "value": {"path": "/switch/enabled"}}
                ]
            }
        }"""
    ).forEach { message ->
        renderer.processMessage(message)
    }
}

@Composable
fun ListDemo(renderer: A2UIRenderer) {
    val surfaceId = "list_demo"
    var isInitialized by remember { mutableStateOf(false) }

    DisposableEffect(surfaceId) {
        if (!isInitialized) {
            initializeListDemo(renderer, surfaceId)
            isInitialized = true
        }

        onDispose {
            try {
                renderer.processMessage("""{"version":"v0.10","deleteSurface":{"surfaceId":"$surfaceId"}}""")
            } catch (e: Exception) {
                // 忽略清理时的错误
            }
        }
    }

    RenderSurface(renderer, surfaceId)
}

private fun initializeListDemo(renderer: A2UIRenderer, surfaceId: String) {
    listOf(
        """{
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "$surfaceId",
                "catalogId": "https://a2ui.org/specification/v0_10/standard_catalog.json"
            }
        }""",
        """{
            "version": "v0.10",
            "updateComponents": {
                "surfaceId": "$surfaceId",
                "components": [
                    {"id": "root", "component": "Column", "children": ["title", "tabs_section", "list_section"], "justify": "start", "align": "stretch"},
                    {"id": "title", "component": "Text", "text": "Tabs & List Demo", "variant": "h2"},
                    {"id": "tabs_section", "component": "Tabs", "options": [{"label": "Tab 1", "value": "tab1"}, {"label": "Tab 2", "value": "tab2"}, {"label": "Tab 3", "value": "tab3"}], "child": "tab_content"},
                    {"id": "tab_content", "component": "Text", "text": "This is tab content. You can add any components here.", "variant": "body"},
                    {"id": "list_section", "component": "Card", "child": "list_content"},
                    {"id": "list_content", "component": "List", "children": {"path": "/items", "componentId": "list_item"}},
                    {"id": "list_item", "component": "Row", "children": ["item_icon", "item_text"], "align": "center"},
                    {"id": "item_icon", "component": "Icon", "text": "star"},
                    {"id": "item_text", "component": "Text", "text": {"path": "/name"}, "variant": "body"}
                ]
            }
        }""",
        """{
            "version": "v0.10",
            "updateDataModel": {
                "surfaceId": "$surfaceId",
                "path": "/items",
                "value": [
                    {"name": "Item 1"},
                    {"name": "Item 2"},
                    {"name": "Item 3"},
                    {"name": "Item 4"},
                    {"name": "Item 5"}
                ]
            }
        }"""
    ).forEach { message ->
        renderer.processMessage(message)
    }
}

@Composable
fun RenderSurface(renderer: A2UIRenderer, surfaceId: String) {
    val context = renderer.getSurfaceContext(surfaceId)
    // ✅ 直接读取，利用 SnapshotStateMap 响应式更新
    val rootComponent = renderer.getComponent(surfaceId, "root")

    if (context != null && rootComponent != null) {
        val registry = remember { ComponentRegistry(renderer) }
        registry.render(rootComponent, context)
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
