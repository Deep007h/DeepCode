# DeepCode — AI Agent, Code Engine & Automation Assistant for Android

<div align="center">

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen?logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%2B%20Jetpack%20Compose-blue?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![AI Engine](https://img.shields.io/badge/AI-Zen%20AI%20%7C%20Antigravity%20%7C%20Gemini%20%7C%20Groq%20%7C%20Ollama-orange)](https://github.com/Deep007h/DeepCode)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-purple?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

</div>

> **Next-Gen Autonomous AI Coding & Mobile Agent** — Run multi-turn agentic loops, high-performance developer terminal shells, document compilation, file editing, and automated workflows right on your Android phone.

---

## About

**DeepCode** is an advanced, full-featured developer-focused AI coding assistant and agent environment designed natively for Android. Combining **cloud AI providers** (Zen AI, Google Antigravity, Gemini, Groq, Mistral, OpenAI, Claude, OpenRouter) and **local inference** (Ollama), DeepCode bridges the gap between mobile productivity and powerful desktop coding workflows.

Beyond conversational assistance, DeepCode acts as an **autonomous agent** capable of generating beautiful PDF documents, performing web search and live URL scraping, executing shell and terminal commands (with root fallback), compiling QR codes and ZIP archives, manipulating files with syntax highlighting, and automating device events seamlessly.

---

## Features

| Feature | Description |
|---------|-------------|
| **AI Coding & Agent Loop** | Streaming SSE chat with Markdown rendering, code highlighting, and multi-step tool execution |
| **Zen AI Free & Pro Models** | Instant access to high-performance Zen AI models with low TTFT and multi-provider failover |
| **Multi-Key Ring Auto-Rotation** | Supports up to 6 API keys per provider with instant, silent auto-rotation upon rate limits (429) |
| **Native Terminal Console** | Dual-mode terminal shell (Standard Shell & Root `su` Shell) with quick command execution |
| **Code Editor (Sora Editor)** | Integrated code editor with line numbering, undo/redo buffers, find-and-replace, and syntax support |
| **Automated PDF Studio** | Native high-speed multi-layout PDF generation (`classic`, `modern-minimal`, `corporate-report`, `academic-paper`, `invoice-receipt`, `resume-cv`) |
| **Web Search & Fetch** | Live web searching, article extraction, and real-time data lookup capabilities |
| **Voice & Edge TTS** | Built-in text-to-speech audio synthesis and voice input directly within the chat UI |
| **File Management & Tools** | Read, write, delete, search, grep, and manipulate files with sandbox bypass and root permissions |
| **Automations & Triggers** | Scheduled time-based and event-based battery/power AI triggers |
| **Custom Personas & Theming** | Fully customizable UI themes, accent colors, chat wallpapers, and customizable assistant personas |
| **Encrypted Security** | API keys and tokens stored securely with AES-256-GCM via Android Keystore |

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Language** | Kotlin 2.0+ / Coroutines / Flow |
| **UI Framework** | Jetpack Compose (Material Design 3) |
| **Architecture** | MVVM with Clean Repository Pattern |
| **Local Database** | Room (SQLite) with encrypted persistence |
| **Networking** | OkHttp 4.12 (HTTP/2 Connection Pooling) + Retrofit + Gson |
| **PDF Generation** | Native Android Graphics Canvas + Android PdfDocument |
| **Code Editor** | Sora Editor Integration |
| **TTS Engine** | Microsoft Edge TTS WebSocket Pipeline |
| **Security** | Android Keystore + EncryptedSharedPreferences (AES-256-GCM) |
| **Root Access** | libsu (optional for advanced root capabilities) |
| **Build System** | Gradle 8.9 with Kotlin DSL |
| **Min SDK** | API 26 (Android 8.0) |
| **Target SDK** | API 34 (Android 14) |

---

## Hardware Requirements

| Component | Requirement |
|-----------|-------------|
| **Android OS** | 8.0 (API 26) or higher |
| **RAM** | 3 GB+ (4 GB+ recommended) |
| **Storage** | 100 MB for app installation |
| **Architecture** | ARM64 (`arm64-v8a`), ARMv7 (`armeabi-v7a`), x86_64 |
| **Root Access** | Optional (APatch, Magisk, KernelSU) for system-level execution |

---

## Software Requirements

| Tool | Version |
|------|---------|
| Android Studio | Ladybug / Hedgehog (2023.1.1+) or newer |
| Android SDK | API 34 |
| JDK | Java 17+ |
| Gradle | 8.9+ |
| Kotlin | 2.0.0+ |

---

## Installation

### From APK (Recommended)
1. Download the latest `app-debug.apk` from [GitHub Releases](https://github.com/Deep007h/DeepCode/releases).
2. Enable **Install from Unknown Apps** in your Android device settings.
3. Install and launch **DeepCode**.

### From Source

```bash
# 1. Clone the repository
git clone https://github.com/Deep007h/DeepCode.git
cd DeepCode

# 2. Build the Debug APK using Gradle
./gradlew assembleDebug

# 3. Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Structure

```
DeepCode/
├── app/
│   ├── src/main/
│   │   ├── java/ai/deepcode/android/
│   │   │   ├── data/
│   │   │   │   ├── local/           # Room Database, DAOs, EncryptedPrefs (AES-256-GCM)
│   │   │   │   ├── remote/          # AI Providers (Zen, Antigravity, Gemini, Groq, Ollama), ApiKeyRotator
│   │   │   │   └── repository/      # AgentRepository, WorkspaceRepository, TokenRepository
│   │   │   ├── domain/
│   │   │   │   ├── agent/          # AgentEngine, ToolExecutor, PluginEngine
│   │   │   │   └── model/          # Message, ChatSession, AIModel, ToolCall
│   │   │   ├── service/            # AiBridgeRunner, Background services
│   │   │   ├── ui/
│   │   │   │   ├── chat/           # ChatScreen, ChatViewModel, AiBubble, InputBar
│   │   │   │   ├── dashboard/      # DashboardScreen, Project Cards, Quick Stats
│   │   │   │   ├── automations/    # AutomationsScreen, Scheduled Triggers
│   │   │   │   ├── connections/    # ConnectionsScreen, OAuth Manager, Integrations
│   │   │   │   ├── settings/       # ApiKeysScreen, CustomThemes, LogViewer, Personas
│   │   │   │   ├── components/     # MarkdownText, CodeBlock, SoraEditorView
│   │   │   │   └── theme/          # Color, Type, Shape, Dynamic Themes
│   │   │   ├── util/               # EdgeTtsEngine, PdfGenerator, FileSystemUtil, AppLogger
│   │   │   └── DeepCodeApp.kt      # Application class & initialization
│   │   ├── res/                    # Drawables, M3 Themes, Layouts, Vector Icons
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── LICENSE
└── README.md
```

---

## AI Providers & Models

Configure API keys in **Settings → API Keys** (with up to 6 keys per provider for automatic failover):

| Provider | Supported Models | Access Type |
|----------|------------------|-------------|
| **Zen AI** | Mimo v2.5, DeepCode Coder, Qwen 2.5 Coder, Llama 3.3 | API Key / Dedicated Infrastructure |
| **Google Antigravity** | Gemini 2.5 Flash, Gemini Pro Experimental | OAuth 2.0 / API Key |
| **Google Gemini** | Gemini 2.5 Flash, Gemini 1.5 Pro | Free Tier & Paid API Key |
| **Groq** | Llama 3.3 70B, Mixtral 8x7B | Ultra-fast Free Tier Available |
| **OpenRouter** | Claude 3.5 Sonnet, GPT-4o, DeepSeek V3 | Multi-model Router |
| **Ollama** | Any local model (Llama 3, Qwen, DeepSeek, CodeLlama) | Self-hosted / Local Network |
| **Mistral AI** | Mistral Large, Codestral | API Key |
| **Anthropic / OpenAI** | Claude 3.5 Sonnet, GPT-4o, o3-mini | Standard API Key |

---

## Agent Tools & Capabilities

The autonomous agent loop can leverage powerful built-in tools:

| Tool | Capability |
|------|------------|
| `create_pdf` | Generate styled PDF documents with customizable layouts natively |
| `web_search` | Search the web for live, up-to-date information |
| `web_fetch` | Scrape and extract readable content and markdown from URLs |
| `edge_tts` | High-quality text-to-speech voice generation |
| `read_file` / `write_file` | Inspect and edit workspace files directly |
| `list_directory` / `grep_search` | Explore directory trees and search codebases |
| `execute_command` | Execute shell commands in standard sandbox or root shell |
| `qr_generate` | Generate high-resolution QR codes |
| `csv_to_json` / `csv_create` | Structured CSV tabular processing |
| `zip_create` / `zip_extract` | Archive creation and extraction |
| `json_format` / `json_minify` | JSON data transformation utilities |
| `hash_text` / `base64_encode` | Cryptographic hashing and encoding tools |

---

## Multi-Key API Auto-Rotation

Never worry about rate limits (HTTP 429) or quota disruptions:
- **6 Key Slots Per Provider**: Store up to 6 API keys for every AI provider in encrypted storage.
- **Instant Silent Failover**: When any key hits a rate limit or quota ceiling, DeepCode instantly rotates to the next available key in the ring without interrupting conversation flow.
- **Smart Ring Fallback**: Uses adaptive cooldown windows (15s) and wraps around configured key slots automatically.

---

## Security & Privacy Notice

- All API keys, bearer tokens, and OAuth credentials are encrypted with **AES-256-GCM** backed by the hardware-isolated **Android Keystore**.
- DeepCode does not upload your private project files or workspace code to any intermediary tracking servers.
- When contributing or sharing your forks, ensure you do not commit any secret tokens or private keys to source control.

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

## Acknowledgements

- [Jetpack Compose](https://developer.android.com/jetpack/compose) by Google
- [OkHttp](https://square.github.io/okhttp/) by Square
- [Sora Editor](https://github.com/Rosemoe/sora-editor) for the mobile code editing engine
- [libsu](https://github.com/topjohnwu/libsu) by TopJohnWu for Android root management
- The Open Source AI & Android Developer Community

