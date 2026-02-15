package org.a2ui.compose.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.a2ui.compose.rendering.A2UIRenderer
import org.a2ui.compose.rendering.A2UILogger
import org.a2ui.compose.rendering.A2UILogLevel
import org.a2ui.compose.rendering.ActionHandler
import org.a2ui.compose.service.A2UIService

class A2UISampleActivity : ComponentActivity() {

    private lateinit var a2uiService: A2UIService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logger = object : A2UILogger {
            override fun log(level: A2UILogLevel, message: String) {
                android.util.Log.d("A2UI", "[$level] $message")
            }
        }

        a2uiService = A2UIService(A2UIRenderer(logger))

        val actionHandler = object : ActionHandler {
            override fun onAction(surfaceId: String, actionName: String, context: Map<String, Any>) {
                android.util.Log.d("A2UI", "Action: $actionName on surface: $surfaceId, context: $context")
            }

            override fun openUrl(url: String) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
        }

        a2uiService.rendererState.renderer.setActionHandler(actionHandler)

        // Process A2UI messages
        val messages = getSampleMessages()
        messages.forEach { message ->
            a2uiService.processMessage(message)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    A2UIScreen(a2uiService)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        a2uiService.dispose()
    }

    private fun getSampleMessages(): List<String> {
        return listOf(
            """{
                "version": "v0.10",
                "createSurface": {
                    "surfaceId": "contact_form",
                    "catalogId": "https://a2ui.org/specification/v0_10/standard_catalog.json",
                    "theme": {
                        "primaryColor": "#6200EE"
                    }
                }
            }""",
            """{
                "version": "v0.10",
                "updateComponents": {
                    "surfaceId": "contact_form",
                    "components": [
                        {
                            "id": "root",
                            "component": "Card",
                            "child": "form_container"
                        },
                        {
                            "id": "form_container",
                            "component": "Column",
                            "children": ["header_row", "name_field", "email_field", "preference_picker", "subscribe_checkbox", "submit_button"],
                            "justify": "start",
                            "align": "stretch"
                        },
                        {
                            "id": "header_row",
                            "component": "Row",
                            "children": ["header_icon", "header_text"],
                            "align": "center"
                        },
                        {
                            "id": "header_icon",
                            "component": "Icon",
                            "text": "mail"
                        },
                        {
                            "id": "header_text",
                            "component": "Text",
                            "text": "Contact Us",
                            "variant": "h2"
                        },
                        {
                            "id": "name_field",
                            "component": "TextField",
                            "label": "Full Name",
                            "value": {"path": "/contact/name"},
                            "placeholder": "Enter your name"
                        },
                        {
                            "id": "email_field",
                            "component": "TextField",
                            "label": "Email Address",
                            "value": {"path": "/contact/email"},
                            "placeholder": "Enter your email"
                        },
                        {
                            "id": "preference_picker",
                            "component": "ChoicePicker",
                            "label": "Contact Preference",
                            "value": {"path": "/contact/preference"},
                            "options": [
                                {"label": "Email", "value": "email"},
                                {"label": "Phone", "value": "phone"},
                                {"label": "Both", "value": "both"}
                            ]
                        },
                        {
                            "id": "subscribe_checkbox",
                            "component": "CheckBox",
                            "label": "Subscribe to newsletter",
                            "value": {"path": "/contact/subscribe"}
                        },
                        {
                            "id": "submit_button",
                            "component": "Button",
                            "text": "Submit",
                            "variant": "primary",
                            "action": {
                                "event": {
                                    "name": "submit_form",
                                    "context": {"formId": "contact_form"}
                                }
                            }
                        }
                    ]
                }
            }""",
            """{
                "version": "v0.10",
                "updateDataModel": {
                    "surfaceId": "contact_form",
                    "path": "/contact",
                    "value": {
                        "name": "John Doe",
                        "email": "john.doe@example.com",
                        "preference": "email",
                        "subscribe": true
                    }
                }
            }"""
        )
    }
}

@Composable
fun A2UIScreen(service: A2UIService) {
    val rendererState = service.rendererState

    val surfaceIds = remember { rendererState.getAllSurfaceIds() }

    surfaceIds.forEach { surfaceId ->
        val context = rendererState.renderer.getSurfaceContext(surfaceId)
        val rootComponent = rendererState.renderer.getComponent(surfaceId, "root")

        if (context != null && rootComponent != null) {
            val registry = remember { org.a2ui.compose.rendering.ComponentRegistry(rendererState.renderer) }
            registry.render(rootComponent, context)
        }
    }
}
