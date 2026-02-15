# A2UI: Agent-to-User Interface

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)

A2UI is an open-source project that allows agents to generate or populate rich user interfaces. This repository includes a **complete Android Jetpack Compose renderer implementation**.

<img src="docs/assets/a2ui_gallery_examples.png" alt="Gallery of A2UI components" height="400">

## 🚀 Android Compose Renderer

The **Android Compose Renderer** is a full-featured implementation of the A2UI v0.10 protocol, built with modern Jetpack Compose technology.

### ✨ Key Features

| Feature | Description |
|---------|-------------|
| **20+ UI Components** | Text, Button, TextField, Card, List, Modal, Tabs, and more |
| **Data Binding** | Path-based expressions with two-way binding support |
| **Input Validation** | Built-in validators: email, url, phone, regex, length, etc. |
| **Theme Customization** | Dynamic colors, dark mode, custom themes |
| **Network Transport** | WebSocket and SSE (Server-Sent Events) support |
| **State Persistence** | Automatic state save/restore on configuration changes |
| **Accessibility** | WCAG A level compliance with TalkBack support |
| **Error Handling** | Global error handler with user-friendly feedback |

### 📱 Supported Components

```
Text • Button • TextField • CheckBox • Switch • Slider
ChoicePicker • Dropdown • Card • Row • Column • List
Tabs • Modal • Image • Icon • Divider • Spacer
ProgressBar • DateTimeInput • Video • AudioPlayer • Surface
```

### 🏃 Quick Start

```kotlin
@Composable
fun A2UIScreen() {
    val renderer = rememberA2UIRenderer()
    
    // Process A2UI message
    renderer.processMessage("""
        {
            "version": "v0.10",
            "createSurface": {
                "surfaceId": "hello",
                "catalogId": "https://a2ui.org/catalog.json"
            }
        }
    """)
    
    // Render UI
    A2UISurface(surfaceId = "hello")
}
```

### 📦 Installation

Add the compose module to your project:

```kotlin
// settings.gradle.kts
include(":android_compose")

// build.gradle.kts
dependencies {
    implementation(project(":android_compose"))
}
```

### 📖 Full Documentation

See [android_compose/README.md](android_compose/README.md) for complete documentation including:
- Architecture overview
- Component reference
- Theme customization
- Network integration
- Error handling
- Testing guide

---

## 📋 Project Overview

A2UI is an open standard and set of libraries that allows agents to "speak UI." Agents send a declarative JSON format describing the *intent* of the UI. The client application then renders this using its own native component library.

### Why A2UI?

- **Security First**: Declarative data format, not executable code
- **LLM-Friendly**: Flat component list, easy for LLMs to generate incrementally
- **Framework-Agnostic**: Same JSON payload renders on multiple platforms
- **Flexibility**: Open registry pattern for custom components

### Available Renderers

| Platform | Status | Location |
|----------|--------|----------|
| **Android (Compose)** | ✅ Complete | [android_compose/](android_compose/) |
| **Web (Lit)** | ✅ Available | renderers/lit/ |
| **Flutter** | ✅ Available | [GenUI SDK](https://github.com/flutter/genui) |
| **React** | 🔜 Planned | - |
| **iOS (SwiftUI)** | 🔜 Planned | - |

---

## 🏗️ Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   A2UI Agent    │────▶│    Transport    │────▶│    Renderer     │
│   (Backend)     │     │  WebSocket/SSE  │     │   (Compose)     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                                               │
        │  JSON Message                                 │
        ▼                                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  {                                                              │
│    "version": "v0.10",                                          │
│    "createSurface": { "surfaceId": "main", ... },               │
│    "updateComponents": { "components": [...] }                  │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 21+ (Android 5.0 Lollipop)
- Kotlin 1.9.22
- JDK 17

### Running the Android Demo

1. **Clone the repository:**
   ```bash
   git clone https://github.com/lmee/A2UI-Android.git
   cd A2UI-Android
   ```

2. **Open in Android Studio:**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Run the app:**
   - Select a device or emulator
   - Click "Run" or press `Shift+F10`

### Running the Web Demo (Original)

For the original web-based demo:

```bash
# Set API key
export GEMINI_API_KEY="your_gemini_api_key"

# Run Agent
cd samples/agent/adk/restaurant_finder
uv run .

# Run Client (new terminal)
cd renderers/web_core && npm install && npm run build
cd ../lit && npm install && npm run build
cd ../../samples/client/lit/shell && npm install && npm run dev
```

---

## 📁 Project Structure

```
A2UI/
├── android_compose/            # 🆕 Android Compose Renderer
│   ├── src/main/java/org/a2ui/compose/
│   │   ├── data/              # Data model & processing
│   │   ├── rendering/         # Core renderer & components
│   │   ├── transport/         # Network layer (WebSocket/SSE)
│   │   ├── theme/             # Theme customization
│   │   ├── error/             # Error handling
│   │   └── example/           # Demo application
│   ├── src/test/              # Unit tests (49 tests)
│   ├── build.gradle.kts       # Build configuration
│   └── README.md              # Full documentation
├── renderers/
│   ├── web_core/              # Web core library
│   └── lit/                   # Lit renderer
├── samples/
│   ├── agent/                 # Agent examples
│   └── client/                # Client examples
├── docs/                      # Documentation
└── specification/             # A2UI specification
```

---

## 🔧 Android Compose Features

### Component Example

```kotlin
// Define UI via JSON
val message = """
{
    "version": "v0.10",
    "updateComponents": {
        "surfaceId": "form",
        "components": [
            {
                "id": "email_field",
                "component": "TextField",
                "label": "Email",
                "value": { "path": "/form/email" },
                "required": true,
                "checks": [
                    { "call": "email", "message": "Invalid email format" }
                ]
            },
            {
                "id": "submit_btn",
                "component": "Button",
                "text": "Submit",
                "action": { "event": { "name": "submit_form" } }
            }
        ]
    }
}
"""
```

### Theme Customization

```kotlin
val themeConfig = A2UIThemeConfig(
    primaryColor = "#6200EE",
    secondaryColor = "#03DAC6",
    darkMode = false
)

A2UITheme(config = themeConfig) {
    A2UISurface(surfaceId = "main")
}
```

---

## ⚠️ Status: Early Stage Public Preview

> **Note:** A2UI is currently in **v0.10 (Public Preview)**. The specification and implementations are functional but are still evolving.

---

## 🗺️ Roadmap

- [x] Android Compose Renderer (Complete)
- [ ] React Renderer
- [ ] iOS SwiftUI Renderer
- [ ] REST Transport Support
- [ ] Additional Agent Frameworks (Genkit, LangGraph)

---

## 🤝 Contributing

A2UI is an **Apache 2.0** licensed project. We welcome contributions!

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

### Development Setup

```bash
# Clone and setup
git clone https://github.com/lmee/A2UI-Android.git
cd A2UI-Android

# Run tests
./gradlew :android_compose:test

# Build
./gradlew :android_compose:build
```

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## 🔗 Links

- **Android Compose Renderer**: [android_compose/README.md](android_compose/README.md)
- **A2UI Specification**: [specification/](specification/)
- **GenUI SDK (Flutter)**: https://github.com/flutter/genui
- **CopilotKit Widget Builder**: https://go.copilotkit.ai/A2UI-widget-builder

---

**Maintained by the A2UI Community**
