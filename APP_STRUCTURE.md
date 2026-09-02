# OpenCode — Full App Structure

> **OpenCode** is an open-source terminal AI coding assistant. This repo is the **Android port** (package `ai.deepcode.android`), which packs the full desktop agent experience into a mobile app with a terminal emulator, code editor, multi-agent orchestration, external service integrations, and cron automation.

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                      │
│  MainActivity, ChatScreen, TerminalScreen, EditorScreen,    │
│  DashboardScreen, SettingsScreen, AutomationsScreen, etc.   │
├─────────────────────────────────────────────────────────────┤
│                     ViewModel Layer                          │
│  ChatViewModel, AgentsViewModel, AutomationsViewModel,      │
│  ConnectionsViewModel, TokenUsageViewModel                  │
├─────────────────────────────────────────────────────────────┤
│                    Orchestration Layer                       │
│  OrchestratorEngine (classify → route)                      │
│  WatchdogManager (silence detection, failover)              │
│  AgentEngine (LLM tool-calling loop)                        │
│  AgentRuntime (sub-agent execution)                         │
├─────────────────────────────────────────────────────────────┤
│                    Service / Tool Layer                      │
│  ToolExecutor (read, write, edit, bash, grep, glob, etc.)   │
│  TerminalRunner (shell execution)                           │
│  GitService (JGit wrapper)                                  │
│  Google: CalendarService, DriveService, GmailService        │
│  GitHubService, NotionService, TelegramBridgeService        │
│  WebViewAutomator (browser automation)                      │
├─────────────────────────────────────────────────────────────┤
│                    Data Layer                                │
│  Room DB (AppDatabase.kt) — sessions, messages, agents,     │
│    automations, integrations, memory, token usage           │
│  EncryptedPrefs — AES256 encrypted settings                 │
│  AIProvider — 8 providers (Zen, Gemini, Groq, OpenRouter,   │
│    OpenAI, Anthropic, Mistral, Ollama) with SSE streaming   │
│  DeepCodeRepository (central data facade)                   │
├─────────────────────────────────────────────────────────────┤
│                    Sidecar Servers (Node.js)                 │
│  webbridge-server/ — Playwright browser automation          │
│  whatsapp-bridge/ — Baileys WhatsApp MD protocol            │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Directory Tree

```
opencode/
├── app/                                    # Main Android module
│   ├── build.gradle.kts                    # Dependencies (Compose, Room, OkHttp, JGit, etc.)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/agents/
│       │   └── builtin_agents.json         # 21 built-in agent definitions
│       └── java/ai/deepcode/android/
│           ├── DeepCodeApp.kt              # Application: init logger, seed agents, schedule sync
│           ├── agent/
│           │   ├── AgentEngine.kt          # Main LLM tool-calling loop (1177 lines)
│           │   ├── AgentMemory.kt          # Agent memory management
│           │   ├── AgentRepository.kt      # Agent persistence + memory search
│           │   └── AgentTool.kt            # Sealed class: tool types inside agent loop
│           ├── data/
│           │   ├── local/
│           │   │   ├── AppDatabase.kt      # Room DB (7 tables, migrations)
│           │   │   ├── EncryptedPrefs.kt   # AES256 encrypted preferences
│           │   │   ├── ModelPriceProvider.kt
│           │   │   ├── ProfileManager.kt
│           │   │   ├── TokenEventDao.kt / TokenEventEntity.kt
│           │   │   └── TokenUsageDao.kt / TokenUsageEntity.kt
│           │   ├── remote/
│           │   │   ├── AIProvider.kt       # 8 AI providers + SSE streaming (~1200 lines)
│           │   │   ├── WebBridgeAPI.kt     # Retrofit API for Playwright bridge
│           │   │   └── WhatsAppBridgeAPI.kt
│           │   └── repository/
│           │       ├── DeepCodeRepository.kt  # Central facade (sessions, messages, tools)
│           │       └── TokenUsageRepository.kt
│           ├── domain/
│           │   ├── ContextWindowManager.kt
│           │   └── model/Models.kt         # Core models: ChatSession, Message, AIModel, Tool, ToolCall
│           ├── memory/
│           │   ├── MemoryChunk.kt
│           │   ├── MemoryDao.kt
│           │   ├── MemoryIndexer.kt
│           │   └── MemoryManager.kt
│           ├── orchestrator/
│           │   ├── OrchestratorEngine.kt    # Intent classification, task decomposition, execution (502 lines)
│           │   ├── OrchestratorModels.kt    # Decisions, TaskPlan, SubTask, ToolJob (127 lines)
│           │   └── WatchdogManager.kt       # Streaming silence detection + failover (129 lines)
│           ├── receiver/
│           │   └── BootReceiver.kt          # Restart automations on boot
│           ├── service/
│           │   ├── TerminalRunner.kt        # Root/shell command execution
│           │   ├── drive/DriveHandler.kt
│           │   ├── git/GitService.kt        # JGit wrapper
│           │   ├── github/ (GitHubHandler.kt, GitHubService.kt)
│           │   ├── gmail/GmailHandler.kt
│           │   ├── google/ (CalendarService, DriveService, GmailService, GoogleAuthService, YouTubeMusicService)
│           │   ├── music/MusicDetectionHandler.kt
│           │   ├── notion/NotionService.kt
│           │   ├── schedule/ (AutomationHandler.kt, SimpleAutomationRunner.kt)
│           │   ├── storage/TelegramDriveService.kt
│           │   ├── telegram/ (AgentRunner.kt, BotConfigStore.kt, TelegramBridgeService.kt)
│           │   └── tools/
│           │       ├── ToolExecutor.kt      # ALL deterministic tool implementations (1112 lines)
│           │       └── WebViewAutomator.kt  # Android WebView browser automation
│           ├── sync/
│           │   ├── AutoFetchWorker.kt
│           │   ├── SyncResult.kt
│           │   └── SyncScheduler.kt
│           ├── ui/
│           │   ├── MainActivity.kt          # Main entry: nav drawer, pager, all screens (1130+ lines)
│           │   ├── AppState.kt
│           │   ├── LoginScreen.kt
│           │   ├── TokenUsageScreen.kt / TokenUsageViewModel.kt
│           │   ├── agents/ (AgentDao, AgentDetailScreen, AgentEntity, AgentRepository, AgentRuntime, AgentScheduler, AgentsScreen, AgentsViewModel)
│           │   ├── automations/ (AutomationAlarmReceiver, AutomationDao, AutomationEntity, AutomationForegroundService, AutomationRepository, AutomationRunner, AutomationScheduler, AutomationsScreen, AutomationsViewModel)
│           │   ├── chat/
│           │   │   └── ChatScreen.kt        # Chat UI + ChatViewModel (orchestrator, streaming, tool calls)
│           │   ├── components/ (Components.kt, OrchestrationComponents.kt)
│           │   ├── connections/ (ConnectionsScreen, ConnectionsViewModel, IntegrationDao, IntegrationEntity, IntegrationRepository, OAuthManager)
│           │   ├── dashboard/DashboardScreen.kt
│           │   ├── editor/EditorScreen.kt
│           │   ├── files/FilesScreen.kt
│           │   ├── integrations/IntegrationsScreen.kt
│           │   ├── memory/MemoryScreen.kt
│           │   ├── settings/ (SettingsScreen.kt, LogViewerScreen.kt)
│           │   ├── terminal/TerminalScreen.kt
│           │   └── theme/Theme.kt
│           └── util/ (AppLogger, AppResult, DozeHelper, PermissionHelper, SafeDispose, SafeJson, SafeState, TokenSaver, VpnManager)
│
├── webbridge-server/                        # Node.js Playwright browser automation sidecar
│   ├── package.json
│   └── src/
│       ├── index.js, browser.js, classifier.js, credentials.js, generic.js, output.js, router.js, utils.js
│       └── playbooks/ (chatgpt.js, copilot.js, gemini.js, perplexity.js, etc.)
│
├── whatsapp-bridge/                         # Node.js Baileys WhatsApp bridge sidecar
│   ├── package.json
│   └── src/ (index.js, socket.js)
│
├── build.gradle.kts                         # Root Gradle build
├── settings.gradle.kts                      # Project name: "DeepCode for Android"
├── gradle/libs.versions.toml                # Version catalog
├── CHANGELOG.md
├── MODELS.md
└── README.md
```

---

## 3. What the App Does

| Feature | Description |
|---|---|
| **Multi-Provider AI Chat** | Chat with 8 LLM providers (Zen, Gemini, Groq, OpenRouter, OpenAI, Anthropic, Mistral, Ollama) with automatic VPN rotation on rate limits |
| **Tool Calling** | Agent can read/write/edit files, run shell commands, grep/glob search, web fetch/search, git operations, GitHub API, Google Drive/Calendar/Gmail, Notion, Telegram |
| **Multi-Agent Orchestration** | Complex tasks are decomposed into sub-tasks and dispatched to specialized agents (planner, researcher, code_executor, critic, etc.) |
| **Terminal Emulator** | Full terminal with root support |
| **Code Editor** | Sora Editor with syntax highlighting |
| **Automation / Cron** | Scheduled tasks (daily briefing, periodic actions) using Android WorkManager + AlarmManager |
| **Knowledge Base / Memory** | FTS4 full-text search memory with automatic indexing |
| **External Integrations** | GitHub, Google Drive, Gmail, Google Calendar, YouTube Music, Notion, Slack, Telegram bot, WhatsApp |
| **Browser Automation** | WebView-based + Playwright sidecar for web AI services (ChatGPT, Gemini, etc.) |
| **Token Usage Tracking** | Per-model cost tracking with daily/monthly budgets |
| **Git Integration** | JGit-based clone, branch, commit, PR, issue management |

---

## 4. Startup Flow

```
Device Boot
  └─ BootReceiver.kt ──→ reschedules all automations

App Launch
  └─ DeepCodeApp.kt (Application.onCreate)
       ├─ Init AppLogger
       ├─ Schedule SyncScheduler (periodic background sync)
       ├─ Reschedule all automations
       └─ Seed builtin_agents.json → Room DB (21 agents)

  └─ MainActivity.kt (ComponentActivity.onCreate)
       ├─ Handle OAuth deep links (deepcode://oauth)
       ├─ Init DeepCodeRepository
       ├─ Warm up EncryptedPrefs
       ├─ Init VpnManager (proxy rotation)
       ├─ Request permissions (storage, notifications, battery)
       ├─ Start TelegramBridgeService
       └─ setContent { DeepCodeTheme { AppMainLayout } }

AppMainLayout
  ├─ ModalNavigationDrawer (sessions, tools, settings, profile)
  ├─ Scaffold + BottomNavBar (Dashboard | Chat | Automations | Connections | Settings)
  └─ HorizontalPager for 5 tabs
```

---

## 5. Chat & Tool Calling Flow

```
User sends message in ChatScreen
  └─ ChatViewModel.sendMessage()
       └─ OrchestratorEngine.classifyIntent(userMessage)
            ├─ PassToMain ──→ ChatViewModel.runAgentLoop()
            │                     └─ AIProvider.streamCompletion(tools=ToolExecutor.getDeclaredTools())
            │                          ├─ onToken(token)     → update _streamedText
            │                          ├─ onToolCall(toolCall) → executeToolCall()
            │                          │    └─ ToolExecutor.executeTool(name, args)
            │                          │         ├─ "read_file"    → read file content
            │                          │         ├─ "write_file"   → write file (requires user confirm)
            │                          │         ├─ "edit"         → exact string replacement
            │                          │         ├─ "run_command"  → bash shell execution
            │                          │         ├─ "grep_search"  → regex content search
            │                          │         ├─ "glob"         → file pattern matching
            │                          │         ├─ "web_fetch"    → fetch URL
            │                          │         ├─ "web_search"   → web search
            │                          │         ├─ "github_*"    → GitHub API operations
            │                          │         ├─ "drive_*"     → Google Drive operations
            │                          │         ├─ "notion_*"    → Notion operations
            │                          │         ├─ "webbridge_agent" → browser automation
            │                          │         └─ ... (30+ tools total)
            │                          │    └─ result fed back → runAgentLoop() again (max 5 iterations)
            │                          └─ onComplete → save message to DB
            │
            ├─ DirectTool(toolJob) ──→ ToolExecutor.executeTool("webbridge_agent", ...)
            │
            └─ Orchestrate(taskPlan) ──→ OrchestratorEngine.executePlan()
                  └─ For each phase (dependency-ordered):
                       └─ Run sub-agents in parallel via AgentRuntime.executeAgent()
                            └─ Each sub-agent runs its own LLM loop + tool calling
                  └─ synthesizeResults() → final summary
```

### Two Tool Systems

| System | File | Purpose | Called By |
|---|---|---|---|
| `ToolExecutor` | `service/tools/ToolExecutor.kt` | 30+ deterministic backend tools (files, shell, web, git, GitHub, Drive, Notion) | `ChatViewModel`, `DeepCodeRepository` |
| `AgentEngine` tools | `agent/AgentEngine.kt` | Agent-specific tools (integrations, memory, Telegram, automations, settings) | Inside agent loop for built-in agents |

---

## 6. Agent System

### 21 Built-in Agents (from `assets/agents/builtin_agents.json`)

| Agent ID | Tier | Role |
|---|---|---|
| `orchestrator` | `chat` | Root agent, routes tasks |
| `planner` | `reasoning` | Architecture & planning |
| `code_executor` | `worker` | Code lifecycle (clone, edit, build, test, git) |
| `researcher` | `worker` | Web/documentation crawling |
| `tools_agent` | `worker` | Heavyweight ad-hoc execution |
| `critic` | `worker` | Adversarial code review (read-only) |
| `archivist` | `worker` | Background memory/indexing |
| `skill_creator` | `worker` | JavaScript skill/SKILL.md creator |
| `tool_maker` | `worker` | Polyfill script writer |
| `integrations_agent` | `worker` | Service integration driver |
| `crypto_agent` | `worker` | Crypto wallet & markets |
| `markets_agent` | `worker` | Prediction-market trading |
| `mcp_setup` | `worker` | MCP server installation |
| `webbridge` | `worker` | Browser automation |
| `document_creator` | `worker` | PDF/DOCX/XLSX/PPTX creation |
| `help` | `worker` | Product documentation Q&A |
| `trigger_triage` | `worker` | Webhook/cron trigger classification |
| `trigger_reactor` | `worker` | Small reactive actions |
| `summarizer` | `worker` | Tool result compression |
| `morning_briefing` | `worker` | Scheduled daily briefing |

### Agent Config (per agent)
`agent_id`, `display_name`, `description`, `agent_tier`, `temperature`, `max_iterations`, `sandbox_mode`, `system_prompt`, `tools` list, `subagents` list, `omit_*` flags.

---

## 7. AI Provider Architecture

```
AIProvider (interface)
  ├─ streamCompletion(session, toolDefs) → Flow<StreamEvent>
  │    StreamEvent = onToken | onToolCall | onReasoning | onComplete | onError
  │
  ├─ ZenProvider (free tier, default)
  ├─ GeminiProvider (Google)
  ├─ GroqProvider (Groq API)
  ├─ OpenRouterProvider
  ├─ OpenAIProvider
  ├─ AnthropicProvider (Claude)
  ├─ MistralProvider
  └─ OllamaProvider (local)

All share common utilities:
  ├─ streamOpenAiCompatible()  — for OpenAI-compatible APIs
  ├─ streamZenCompatible()     — for Zen endpoint
  └─ SSE parsing via OkHttp streaming
```

VPN rotation (`VpnManager.rotateServer()`) is triggered on 429 / network errors.

---

## 8. Orchestration Engine

```
OrchestratorEngine.classifyIntent(userMessage)
  │
  ├─ Keyword-based routing (no LLM call):
  │   "generate image" / "imagine" → DirectTool(webbridge_agent, image_gen)
  │   "analyze image" / "describe" → DirectTool(webbridge_agent, image_analysis)
  │   "make audio" / "generate sound" → DirectTool(webbridge_agent, audio_gen)
  │
  ├─ Multi-agent decomposition keywords:
  │   "create app" / "build" → [planner, code_executor, critic, researcher]
  │   "code review" → [critic]
  │   "research" → [researcher]
  │   "skill" / "teach" → [researcher, skill_creator]
  │   "automate" / "schedule" → [planner, code_executor]
  │   "deploy" → [code_executor]
  │
  └─ Fallback: PassToMain (simple Q&A)

OrchestratorEngine.executePlan(taskPlan)
  └─ Processes phases sequentially (each phase is a parallel batch of sub-agents)
  └─ synthesizeResults() → final combined output
```

---

## 9. Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 2.0.0 |
| Build | Gradle 9.1.0 with Kotlin DSL |
| UI | Jetpack Compose (Material Design 3), Compose BOM 2026.03.01 |
| Architecture | MVVM + Clean Architecture |
| Database | Room (SQLite) — 7 tables |
| Encrypted Storage | EncryptedSharedPreferences (AES256) |
| Networking | OkHttp 4.12.0 (raw streaming), Retrofit 2.11.0 |
| Serialization | Gson + kotlinx.serialization |
| Navigation | Navigation Compose 2.7.7 |
| Coroutines | kotlinx-coroutines 1.10.2 |
| Code Editor | Sora Editor (Rosemoe) 0.23.4 |
| Markdown | Markwon 4.6.2 |
| Git | JGit 6.9.0 |
| Images | Coil 2.6.0 |
| Background Work | AndroidX WorkManager 2.9.0 |
| Sidecar Servers | Node.js, Express, Playwright, Baileys |

---

## 10. Key Design Patterns

- **Clean Architecture + MVVM**: domain → data → service → UI layers, each with clear responsibilities
- **Repository Pattern**: `DeepCodeRepository` is the single facade over Room DAOs, EncryptedPrefs, and services
- **Sealed Class Decisions**: `OrchestratorDecision` with `PassToMain`, `Orchestrate`, `DirectTool`
- **Callback-Flow Streaming**: agents emit `Flow<String>` via `callbackFlow` for real-time streaming
- **Manual DI**: no Dagger/Hilt/Koin; dependencies constructed manually using `context` as factory
- **Dual Tool Systems**: `ToolExecutor` for deterministic backend tools, `AgentEngine` for agent-specific tools
- **Sidecar Pattern**: Node.js servers (webbridge, whatsapp) run as separate processes alongside the Android app
