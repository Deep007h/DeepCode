<div align="center">

<img src="icon.png" alt="DeepCode Logo" width="120" style="border-radius: 24px; box-shadow: 0 8px 24px rgba(0,0,0,0.4);" />

# DeepCode

### Autonomous AI Agent, Code Engine & Automation Studio for Android

<a href="https://git.io/typing-svg">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=19&duration=3200&pause=1000&color=F97316&center=true&vCenter=true&width=680&lines=Autonomous+AI+Coding+Agent+Native+to+Android;Antigravity+%E2%80%A2+Gemini+3.8+%26+3.5+%E2%80%A2+Zen+AI+(Muse+1.3)+%E2%80%A2+Groq;Ollama+Cloud+%E2%80%A2+ChatGPT+Integration+(Images+%26+Docs)+%E2%80%A2+Claude+Fable+5.1;GPT-6+Astra+%E2%80%A2+Terminal+Shell+%E2%80%A2+Sora+Editor+%E2%80%A2+PDF+Studio;Exact+AlarmManager+Automations+With+Dedicated+Chat+Threads" alt="DeepCode Typing Animation" />
</a>

<br/>

[![Latest Release](https://img.shields.io/badge/Release-v1.2-22c55e?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Deep007h/DeepCode/releases)
[![Platform](https://img.shields.io/badge/Android-API%2026%2B%20(8.0--15)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](LICENSE)

<p align="center">
  <a href="#-whats-new-in-v12"><b>What's New</b></a> •
  <a href="#-key-features"><b>Features</b></a> •
  <a href="#-ai-providers--model-matrix"><b>AI Providers</b></a> •
  <a href="#-architecture--agent-loop"><b>Architecture</b></a> •
  <a href="#-agent-tools"><b>Tools</b></a> •
  <a href="#-background-automations"><b>Automations</b></a> •
  <a href="#-installation"><b>Installation</b></a>
</p>

</div>

---

## 🚀 Overview

**DeepCode** transforms your Android device into an autonomous AI development environment. By coupling flagship cloud models (**Google Antigravity**, **OpenCode Zen AI**, **Google Gemini**, **Groq LPU**, **Ollama Cloud**, **OpenAI**, **Anthropic**, **GMI Cloud**) with an integrated **ChatGPT integration for image and docs creation**, native **Dual Terminal Shell**, **Sora Code Editor**, and **Automated PDF Studio**, DeepCode delivers desktop-grade agentic capability directly in the palm of your hand.

Whether you need to generate codebases, audit repositories, run complex multi-turn shell commands with root elevation, compile publication-grade PDFs, or schedule recurring autonomous background tasks that wake up your phone on exact intervals, DeepCode executes without compromise.

---

## ⚡ What's New in v1.2

<details open>
<summary><b>✨ Highlights of Release 1.2</b></summary>
<br/>

- ⚡ **Multi-Provider & Multi-Key Ring Expansion**:
  - Full support for **70+ AI providers** (Nebius AI, AIML API, Scaleway, Lambda Labs, Friendli AI, MiniMax, Qwen, Baichuan, Yi, and more).
  - Storage ID normalization and multi-slot API key rotation (slots 1..6) with instant HTTP 429 / quota failover.
- 💬 **Instant Chat Responsiveness & Smooth Motion**:
  - Zero-latency optimistic in-memory message rendering before SQLite persistence.
  - Natural top-to-bottom scroll physics with precise `isNearBottom` viewport detection and jitter-free streaming follow (~7 fps).
- 🛡️ **Session Health & Error Isolation**:
  - Transient API connection drops and rate-limit errors are automatically filtered out of compacted conversation history to prevent poisoned context windows.
  - Comprehensive HTTP status code mapping (401 Unauthorized, 402 Credits/Quota, 429 Rate Limits, 403 Forbidden).
- 🎙️ **Microsoft Edge Neural Voice Synthesis**:
  - Dynamic client token generation and robust audio synthesis fallback for voice interactions.
- 📱 **Sora Editor & Dashboard Metrics Polish**:
  - Resolved asynchronous file loading race conditions in `EditorScreen.kt`.
  - Authoritative message sync counts and conditional reasoning chart slices.
- 🤖 **Telegram Bridge Upgrades**:
  - Proactive key validation before chat runs and dynamic model-to-provider inference across both static and OpenAI catalog models.

</details>

<details>
<summary><b>📜 Highlights of Release 1.1</b></summary>
<br/>

- 🌐 **ChatGPT Integration for Image and Docs Creation**: Connect your ChatGPT account in **Connections** for zero-API-cost inference, persistent single-session context preservation, high-precision document generation, and inline **DALL-E 3** image rendering.
- ⏰ **Rock-Solid Background Automations**:
  - Exact wakeups powered by Android's `AlarmManager.setExactAndAllowWhileIdle()` and `WAKE_LOCK`.
  - **Dedicated Persistent Threads**: Every scheduled automation task executes inside its own dedicated chat session—never creating duplicate chats on subsequent runs.
  - **Auto-Trigger on App Death**: Fully resilient to process kills and system reboots (`BOOT_COMPLETED`), maintaining scheduled execution reliably.
  - **Interactive Task Editor**: Modify schedules, prompt instructions, repetition intervals, and predefined workflows on the fly.
- 🌌 **Next-Gen Antigravity Provider**: Built-in support for Google's latest **Gemini 3.8 Flash** (Terminal-Bench 2.1 state-of-the-art), **Gemini 3.8 Flash Cyber**, **Gemini 3.5 Flash** (High/Medium/Low), **Gemini 3.1 Pro** (High/Low), and **Claude Sonnet 5 / Opus 4.6**.
- 🌟 **Zen AI Muse Spark 1.3 & Gemini 3.8**: Added **Muse Spark 1.3 Free** (`muse-spark-1.3-contributor-free`, 1M context, 20% fewer tool calls) alongside **Gemini 3.8 Flash**, **Claude Fable 5.1**, and **GPT-6 Astra**.
- ☁️ **Ollama Cloud Integration**: Transitioned from local daemon reliance to official high-throughput **Ollama Cloud** (`https://ollama.com/v1`) with Gemma 4 31B, GLM 4.7, GPT-OSS 120B, and Qwen 3 Coder 480B.
- 🔄 **Updated Zen AI Engine**: Streamlined authentication requiring your Zen API key (`Settings → API Keys`), unlocking both high-throughput Free models (DeepSeek V4 Flash Free, Muse Spark 1.3 Free, MiMo 2.5 Free, Nemotron 3.5 Free) and Pro models (Claude Fable 5.1, Gemini 3.8 Flash, GPT 6 Astra, GPT 5.6 Sol).

</details>

---

## 🌟 Key Features

```
  ┌────────────────────────────────────────────────────────────────────────┐
  │                           DEEPCODE ENGINE                              │
  ├──────────────────┬──────────────────┬──────────────────┬───────────────┤
  │   AI Providers   │   Coding Tools   │ Execution Shell  │  Automations  │
  ├──────────────────┼──────────────────┼──────────────────┼───────────────┤
  │ • Antigravity    │ • Sora Editor    │ • Standard sh    │ • AlarmManager│
  │ • Zen AI (Pro)   │ • Syntax Colors  │ • Root su Shell  │ • Exact Wake  │
  │ • Groq LPU       │ • Git Operations │ • Background PIDs│ • Thread-Safe │
  │ • Ollama Cloud   │ • PDF Studio (6) │ • Grep & Search  │ • Survives OS │
  │ • ChatGPT (Img/Doc)│ • Web Scraping │ • Sandbox Bypass │   Kills       │
  └──────────────────┴──────────────────┴──────────────────┴───────────────┘
```

- 🤖 **Streaming Agentic Loop**: Multi-turn SSE streaming with dynamic tool-calling, chain-of-thought display, and syntax-highlighted code diffs.
- 🔄 **Multi-Key Ring Auto-Rotation**: Store up to **6 API keys per provider** with silent, zero-delay failover when encountering HTTP 429 rate limits or quota caps.
- 💻 **Dual-Mode Terminal Console**: Full interactive terminal emulator supporting standard non-root shell and elevated root (`su` via libsu) execution.
- 📝 **Integrated Sora Editor**: Fast text and code editing engine with line numbers, undo/redo stacks, search-and-replace, and indentation control.
- 📄 **Native PDF Studio**: Generate beautiful, publication-ready PDF documents across 6 specialized presets (`classic`, `modern-minimal`, `corporate-report`, `academic-paper`, `invoice-receipt`, `resume-cv`).
- 🎙️ **Voice & Microsoft Edge TTS**: Speech-to-text input paired with edge-synthesized, neural voice audio responses.
- 🔒 **Hardware-Isolated Security**: All API keys, bearer tokens, and OAuth credentials encrypted via **AES-256-GCM** backed by the hardware **Android Keystore**.

---

## 🧠 AI Providers & Model Matrix

DeepCode offers seamless routing across over 20 AI providers. Configure your keys in **Settings → API Keys**.

| Provider | Access Type | Highlighted Models | Context | Tier |
|:---|:---|:---|:---|:---|
| **Google Antigravity** | **Google OAuth 2.0 / Bearer (`ya29.`)** | `gemini-3.8-flash` (Gemini 3.8 Flash Workhorse)<br/>`gemini-3.8-flash-cyber`<br/>`gemini-3-flash-agent` (Gemini 3.5 Flash High)<br/>`gemini-3.5-flash-medium` / `gemini-3.5-flash-low`<br/>`gemini-3-pro-preview` (Gemini 3.1 Pro)<br/>`gemini-3.1-pro-high` / `gemini-3.1-pro-low`<br/>`gemini-3.1-flash-lite`<br/>`gemini-2.5-pro` / `gemini-2.5-flash`<br/>`claude-sonnet-5` (Thinking)<br/>`claude-opus-4-6-thinking`<br/>`gpt-oss-120b-medium` | 1M Tokens<br/>1M Tokens<br/>1M Tokens<br/>1M Tokens<br/>1M Tokens<br/>1M Tokens<br/>1M Tokens<br/>1M Tokens<br/>200k Tokens<br/>200k Tokens<br/>128k Tokens | **Free**<br/>Paid<br/>**Free**<br/>**Free**<br/>Paid<br/>Paid<br/>**Free**<br/>**Free**<br/>Paid<br/>Paid<br/>Paid |
| **OpenCode Zen AI** | **API Key / Bearer Auth**<br/>`https://opencode.ai/zen/v1` | `deepseek-v4-flash-free`<br/>`muse-spark-1.3-contributor-free` (Muse Spark 1.3)<br/>`muse-spark-1.2-contributor-free`<br/>`mimo-v2.5-free`<br/>`nemotron-3.5-lightning-free`<br/>`nemotron-3-ultra-free`<br/>`gemini-3.8-flash`<br/>`claude-fable-5.1`<br/>`claude-sonnet-5`<br/>`gpt-6-astra` / `gpt-5.6-sol`<br/>`deepseek-v4-pro` | 1M Tokens<br/>1M Tokens<br/>128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>1M Tokens<br/>1M Tokens<br/>200k Tokens<br/>128k Tokens<br/>1M Tokens | **Free**<br/>**Free**<br/>**Free**<br/>**Free**<br/>**Free**<br/>**Free**<br/>Paid<br/>Paid<br/>Paid<br/>Paid<br/>Paid |
| **Ollama Cloud** | **API Key / Bearer Auth**<br/>`https://ollama.com/v1` | `qwen3-coder:480b`<br/>`gpt-oss:120b`<br/>`gemma4` (31B)<br/>`glm-4.7`<br/>`minimax-m3`<br/>`nemotron-3-super` (120B) | 128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>1M Tokens<br/>128k Tokens | **Free**<br/>**Free**<br/>**Free**<br/>**Free**<br/>**Free**<br/>**Free** |
| **Google Gemini** | **Google AI Studio Key** | `gemini-3.8-flash` (Gemini 3.8 Flash)<br/>`gemini-2.0-flash`<br/>`gemini-2.0-flash-lite`<br/>`gemini-2.0-pro-exp-02-05`<br/>`gemini-1.5-pro`<br/>`imagen-3.0-generate-002` | 1M Tokens<br/>1M Tokens<br/>1M Tokens<br/>2M Tokens<br/>2M Tokens<br/>Image Gen | **Free**<br/>**Free**<br/>**Free**<br/>**Free**<br/>Paid<br/>**Free** |
| **Groq Cloud** | **Groq API Key (LPU)** | `llama-3.3-70b-versatile`<br/>`llama-3.1-8b-instant`<br/>`deepseek-r1-distill-llama-70b`<br/>`openai/gpt-oss-120b`<br/>`groq/compound` (Agentic) | 128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>128k Tokens | **Free**<br/>**Free**<br/>**Free**<br/>**Free**<br/>**Free** |
| **ChatGPT Integration** | **Browser Session Bridge** | `chatgpt-web` (Persistent Session)<br/>`dall-e-3` (Image & Document Generation) | Dynamic<br/>1024x1024 | **Free**<br/>**Free** |
| **OpenAI API** | **OpenAI API Key** | `gpt-6-astra` (Flagship Sept 2026)<br/>`gpt-5.6-sol`<br/>`gpt-4o`<br/>`gpt-4o-mini`<br/>`o3-mini`<br/>`o1` / `dall-e-3` | 128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>200k Tokens<br/>200k Tokens | Paid<br/>Paid<br/>Paid<br/>Paid<br/>Paid<br/>Paid |
| **Anthropic** | **Anthropic API Key** | `claude-fable-5.1` (Adaptive Thinking)<br/>`claude-opus-5`<br/>`claude-sonnet-5`<br/>`claude-3-7-sonnet-latest` (Hybrid)<br/>`claude-4.5-sonnet` / `claude-4.5-opus` | 1M Tokens<br/>200k Tokens<br/>200k Tokens<br/>200k Tokens<br/>200k Tokens | Paid<br/>Paid<br/>Paid<br/>Paid<br/>Paid |
| **GMI Cloud** | **GMI API Key** | `Qwen/Qwen3.8-Flash`<br/>`deepseek-ai/DeepSeek-V4-Flash`<br/>`google/gemini-3.8-flash`<br/>`moonshotai/kimi-k3`<br/>`zai-org/GLM-5.3-Flash` | 128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>128k Tokens<br/>128k Tokens | Paid<br/>Paid<br/>Paid<br/>Paid<br/>Paid |
| **OpenRouter / Cerebrus / Mistral** | **Respective API Keys** | Llama 3.3 70B, DeepSeek R1, Hermes 3 405B, Codestral, Mixtral 8x22B | Up to 128k | Free / Paid |

---

## 🛠️ Agent Tools

The autonomous loop is equipped with native tools executed in real-time on Android:

| Tool Signature | Purpose | Capability |
|:---|:---|:---|
| `create_pdf` | Document Generation | Compiles formatted PDFs using 6 clean design templates |
| `web_search` | Live Search | Queries live search engines and feeds markdown summaries back to LLM |
| `web_fetch` | Content Scraping | Fetches clean, stripped article text and documentation from any HTTP/HTTPS URL |
| `read_file` / `write_file` | File System I/O | Reads and writes files with atomic directory creation and UTF-8 handling |
| `list_directory` / `grep_search` | Codebase Exploration | Fast recursive tree traversal and regex-based multi-file pattern searching |
| `execute_command` | Shell Execution | Spawns background process in sandbox or `su` root with stdout/stderr capture |
| `edge_tts` | Speech Synthesis | Generates high-fidelity neural audio playback via WebSocket pipeline |
| `qr_generate` | QR Utility | Encodes texts, URLs, and payloads into high-resolution PNG QR images |
| `zip_create` / `zip_extract` | Archive Management | Bundles workspaces or extracts compressed archives seamlessly |
| `csv_to_json` / `csv_create` | Structured Data | Parses and transforms delimited tabular datasets on the fly |

---

## ⏰ Background Automations Engine

DeepCode includes an enterprise-grade automated execution engine designed for uninterrupted operation:

```mermaid
sequenceDiagram
    autonumber
    participant System as Android OS / AlarmManager
    participant Receiver as TaskAlarmReceiver
    participant Runner as AiBridgeRunner (Foreground Service)
    participant Engine as AgentEngine
    participant Provider as AI Provider (Antigravity / Zen / Groq)
    participant DB as Room Database (Dedicated Chat Session)

    System->>Receiver: Alarm fires (Exact & Allow While Idle)
    Receiver->>Runner: Start Foreground Service + WakeLock
    Runner->>Engine: Run task (TaskId, Single Dedicated Chat ID)
    Engine->>DB: Fetch or create single dedicated chat thread
    Engine->>Provider: Stream completion & execute agent tools
    Provider-->>Engine: Tool results & final response
    Engine->>DB: Persist messages to the same dedicated thread
    Runner->>System: Schedule next interval & release WakeLock
```

### Key Automation Safeguards:
1. **Single Dedicated Chat Session**: Every task creates exactly one chat session on its initial run and persistently reuses that exact session for all future executions. Your conversation history stays organized and clean.
2. **Device Reboot & App Death Resilience**: Registered `BOOT_COMPLETED` receivers reschedule all active tasks instantly when your phone restarts. If the app is swiped from Recents, the `AlarmManager` wake alarm boots the background bridge automatically.
3. **Full In-App Task Editing**: Tap any automation in the **Automations** tab to edit prompt instructions, trigger times, repeating intervals, or delete obsolete tasks.

---

## 🔄 Multi-Key Ring Auto-Rotation

Never let a rate limit interrupt an autonomous coding session:
- **Up to 6 Keys Per Provider**: Store primary and fallback keys in hardware-backed encrypted storage.
- **Silent HTTP 429 Recovery**: If Key #1 encounters a rate limit or quota depletion, DeepCode automatically falls back to Key #2, Key #3, etc., within milliseconds.
- **Adaptive Cooldown Management**: Keys placed in cooldown automatically reactivate after 15 seconds, cycling smoothly through the key ring.

---

## 🌐 ChatGPT Integration (Image & Document Creation)

Available in the **Connections** screen:
- **Zero API Fees**: Connect directly via your browser session without incurring token charges.
- **Image & Docs Creation**: Generate high-fidelity DALL-E 3 images and structured specifications, documentation, and reports.
- **Single Persistent Session**: Preserves chat context and project state across multiple turns.
- **Inline DALL-E 3**: Prompt the assistant to create graphics, mockups, or diagrams; rendered directly within the chat bubble with instant download to your gallery.

---

## 🎙️ Neural Audio & Voice Synthesis

DeepCode includes a multi-engine text-to-speech studio directly accessible via chat commands and autonomous tool execution:
- **Microsoft Edge Neural TTS**: Generates natural speech across 50+ languages with express-as emotions, rates, pitches, and multi-voice casting. Dynamically resolves client tokens via `EncryptedPrefs` or environment configurations.
- **Kokoro TTS Engine**: Ultra-fast lightweight neural voice synthesis via configurable server URL endpoints (`set_kokoro_url`).
- **Native Android System TTS**: Zero-network offline fallback using your device's installed text-to-speech engine.
- **In-Chat Audio Players**: Inline waveform playback cards with scrubbing, speed control, and instant MP3 export.

---

## 🏗️ Architecture & Tech Stack

```
DeepCode/
├── app/src/main/java/ai/deepcode/android/
│   ├── agent/             # AgentEngine, ToolExecutor, PluginEngine
│   ├── data/
│   │   ├── local/         # Room DB (ChatSession, Message, AutomationTask), EncryptedPrefs
│   │   ├── remote/        # 20+ Providers, ZenModels, Antigravity, ApiKeyRotator
│   │   └── repository/    # AgentRepository, AutomationRepository, TokenRepository
│   ├── service/
│   │   ├── automations/   # TaskAlarmReceiver, BootReceiver, TaskScheduler
│   │   ├── tools/         # Native implementations (Pdf, Web, Terminal, EdgeTTS)
│   │   └── AiBridgeRunner.kt # Foreground background execution runner
│   ├── ui/
│   │   ├── automations/   # AutomationsScreen, TaskEditSheet, ScheduleDialog
│   │   ├── chat/          # ChatScreen, ChatViewModel, AiBubble, SoraEditorView
│   │   ├── connections/   # ConnectionsScreen, ChatGPTBridge
│   │   ├── dashboard/     # DashboardScreen, Project Cards, Quick Stats
│   │   └── settings/      # ApiKeysScreen, CustomThemes, LogViewer, Personas
│   └── DeepCodeApp.kt     # Application lifecycle & dependency container
```

| Layer | Component | Details |
|:---|:---|:---|
| **Language** | Kotlin 2.0+ | Coroutines, StateFlow, Channels |
| **UI** | Jetpack Compose | Material Design 3, Dynamic Color, Edge-to-Edge |
| **Architecture** | MVVM + Clean Architecture | Repository pattern, decoupled service layer |
| **Database** | Room SQLite | TypeConverters, encrypted migrations |
| **Networking** | OkHttp 4.12 + Retrofit | HTTP/2 connection pooling, SSE streaming |
| **Editor** | Sora Editor | Custom color schemes, line rendering |
| **Security** | Android Keystore | AES-256-GCM hardware-backed cryptography |
| **Root Engine** | libsu | TopJohnWu libsu for secure superuser shells |

---

## 📦 Installation

### Option 1: Direct APK (Recommended)
1. Download the latest `app-debug.apk` from [GitHub Releases](https://github.com/Deep007h/DeepCode/releases).
2. Allow installation from unknown sources in Android Settings.
3. Open DeepCode and configure your preferred provider in **Settings → API Keys**.

### Option 2: Build From Source
```bash
# 1. Clone repository
git clone https://github.com/Deep007h/DeepCode.git
cd DeepCode

# 2. Compile Debug APK with Gradle
./gradlew assembleDebug

# 3. Install on Android device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔐 Security & Privacy

- **On-Device Storage**: Code files, terminal history, and chat conversations remain strictly stored on your device.
- **Hardware Encryption**: Sensitive credentials are encrypted with AES-256-GCM keys managed by the Android Keystore.
- **Root Protection**: Root commands require explicit superuser elevation dialog approval through your device's root manager (Magisk / KernelSU / APatch).

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

<div align="center">
  <sub>Built with ❤️ for the global open-source developer & AI agent community.</sub>
</div>
