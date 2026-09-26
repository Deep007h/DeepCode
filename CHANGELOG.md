# 📝 Changelog

All notable changes and milestones for **DeepCode for Android** are documented below.

---

## [1.5.6] - 2026-09-26

### Fixed & Enhanced
- **Telegram Reply & Audio Synthesis Context**:
  - Fixed audio generation when replying to messages: quoting a poem, story, or text and requesting *"create audio of this message"* now speaks the quoted replied message content rather than speaking literal instructions or meta words.
  - Stripped `[reply]` quotes before calculating meta-reference string length thresholds to avoid false negatives on long poems.
  - Preserved up to 4000 characters of replied content in chat threading and added full text resolution across user and assistant messages in `TelegramBridgeService` and `ToolExecutor`.
  - Added strict guardrail in `synthesizeSpeechWithResult`: pronoun/meta-reference phrases are never synthesized directly; system falls back to conversation context or prompts for clarification.
- **YouTube Music Playback & Real-Time Query Reasoning**:
  - Fixed query reasoning for song playback: requests like *"play new karan aujla song in youtube music"* now reason over the artist, query web search for recent releases/track titles, and resolve the actual target song name.
  - Added Android 11+ `<queries>` package visibility for `com.google.android.apps.youtube.music`, `com.google.android.youtube`, `com.spotify.music`, and intent actions.
  - Enhanced intent resolution with `MEDIA_PLAY_FROM_SEARCH` and explicit YouTube Music component fallback.
- **Setup Wizard Navigation & UX**:
  - Added back button / gesture handler in `SetupWizardScreen` to allow stepping backwards through onboarding steps.
  - Dynamic notification titles in `RuntimeExecutionService`.

---

## [1.5.0] - 2026-09-25

### Added & Enhanced
- **PC-Environment Direct Execution for Root Users**:
  - Unrestricted superuser execution: AI operates as an autonomous Linux coding agent with direct, friction-free tool invocation without confirmation prompts.
  - Universal root elevation across all file tools: `read_file`, `write_file`, `edit_file`, `list_directory`, `create_file`, `delete_file`, `grep_search`, and `run_command` automatically inherit root privileges when root mode is enabled or granted.
  - Arbitrary-size root file writes using temporary file streaming via `context.cacheDir` + root `cp -f` with `chmod 644`, bypassing command-line length limits (`ARG_MAX`).
  - Native root `edit_file` with string replacement supporting protected system/data paths.
  - Expanded tool aliases (`view_file`, `write_to_file`, `list_dir`, `ls`, `touch`, `mkdir`, `rm`, `remove_file`, `bash`, `sh`, `exec`) and alternative parameter keys (`command`, `cmd`, `script`, `input`, `code`).
- **Storage & System Privileges**:
  - Silent AppOps grant: Automatically executes `appops set <package> MANAGE_EXTERNAL_STORAGE allow` upon root detection, providing unrestricted access to `/storage/emulated/0` without manual SAF prompts.
  - Startup Superuser Auto-Detection: Added non-blocking `checkSuAlreadyGranted()` in `detectInitialState()` to recognize active root privileges on app launch.
  - Fixed `resolvePath` to expand `~` to the home directory and removed path traversal clamping.
- **AI Tool Calling Robustness**:
  - Text-based tool call execution: Models emitting `<tool_call>`, `<tool_calls>`, `<invoke>`, `<function=...>`, or markdown code blocks have their tools automatically parsed and executed in `sendMessage` on the first turn.
  - Dynamic tool inclusion & provider latency optimization (`shouldIncludeTools`): Excludes massive tool schemas for simple conversational turns, reducing time-to-first-token by ~95% on reasoning models (e.g., Atria).
  - Increased OkHttp `readTimeout` to 90s, `writeTimeout` to 60s, `connectTimeout` to 20s.
  - Capped redundant retry attempts and preserved `_deferredResponse` on network errors to eliminate the "Thinking..." UI bubble glitch.
- **Telegram-Style Swipe-to-Reply & Quote Threading**:
  - Swipe-to-reply gesture on messages with animated reply preview bar and `[reply author="..." id="..."]...[/reply]` quote threading.
- **Security & Integrity**:
  - Sentinel Arbiter integration, Credential Surrogation, Network Sentinel, and muse.ai Video Intelligence Plugin.

---

## [1.4.0] - 2026-09-20

### Added & Improved
- OpenCode Zen wire protocol fixes and streaming stability.
- Multi-key rotation across configured API key slots.
- Variable refresh rate support (60Hz–144Hz) and UI smoothness enhancements.

---

## [1.3.0] - 2026-09-18

### Added
- **Telegram Bot Image Generation & Context Persistence**:
  - Multipart form-data uploads for local images with automatic document fallback if Telegram CDN rejects dimensions or formats.
  - Full conversational history persistence in Room DB for direct tool runs (image gen, image search, audio, video) and system bypasses (music, automations, github, gmail), enabling multi-turn follow-ups and retry inquiries.
  - Multi-tier image generation fallback cascade: OpenAI DALL-E 3 → Google Gemini Imagen 3 → Cloudflare Flux → Pollinations Flux.
- **ChatGPT & Automation ChatScreen Deduplication**:
  - Filter out repeated automation input prompts in `ChatScreen` so only the first scheduled prompt is shown at the top, followed strictly by AI replies.
  - Prevent duplicate prompt insertion in `AutomationRunner` for scheduled executions while retaining session provider tagging.

### Fixed & Improved
- **Theme System & Visual Polish**:
  - Theme consistency for thinking indicators, pulsating icons, blinking cursors, and audio players across light and dark themes.
  - Scheduled task markdown blockquote and formatting adjustments.

---

## [1.2.0] - 2026-09-09

### Added
- **Multi-Provider Key Resolution & Model Expansion**:
  - Full catalog expansion for 70+ AI providers (Nebius AI, AIML API, Scaleway, Lambda Labs, Friendli AI, MiniMax, Qwen, Baichuan, Yi, and more).
  - Storage ID normalization and multi-slot API key rotation (slots 1..6) with instant HTTP 429 / quota failover across AgentEngine, SimpleAutomationRunner, and AiBridgeRunner.
- **Session Health & Error Isolation**:
  - Transient API connection drops and rate-limit errors are automatically filtered out of compacted conversation history to prevent poisoned context windows.
  - Comprehensive HTTP status code error mapping (401 Unauthorized, 402 Credits/Quota exhausted, 429 Rate Limits, 403 Forbidden).
- **Microsoft Edge Neural Voice Synthesis**:
  - Dynamic client token generation and robust audio synthesis fallback for voice interactions.
- **Telegram Bridge Upgrades**:
  - Proactive key validation before chat runs and dynamic model-to-provider inference across both static and OpenAI catalog models.

### Fixed & Improved
- **Chat Motion & Rendering**:
  - Instant optimistic in-memory message rendering before SQLite persistence.
  - Natural top-to-bottom scroll physics with precise `isNearBottom` viewport detection and jitter-free streaming follow (~7 fps).
  - Refined Material 3 shape geometry (22dp, 24dp, 14dp) and enhanced CodeBlock dark contrast (`#1B1B20`).
- **CodeEditor & Dashboard Metrics**:
  - Resolved asynchronous file loading race condition in `EditorScreen.kt`.
  - Authoritative message sync counts and conditional reasoning chart slices in `DashboardScreen.kt`.

---

## [1.1.0] - 2026-09-04

### Added
- **ChatGPT Integration for Image & Docs Creation**:
  - Integrated browser session bridge in the Connections tab allowing seamless interaction with OpenAI's consumer web platform at zero API token cost for rich document and image generation.
  - Dedicated persistent single-session context preservation across multi-turn queries.
  - Direct inline DALL-E 3 image rendering with immediate download to the Android media gallery.
- **Background Automations Engine**:
  - Exact background execution via `AlarmManager.setExactAndAllowWhileIdle()` and `WAKE_LOCK`.
  - Single dedicated chat session per scheduled task—eliminating duplicate chat creation on subsequent runs.
  - Full system reboot and process-kill resilience (`BOOT_COMPLETED` receiver auto-reschedules active tasks).
  - Interactive Task Editor dialog allowing modification of prompt instructions, execution times, repetition intervals, and predefined workflows.
- **Antigravity Next-Gen Models**:
  - Added support for Google's latest Gemini 3.5 Flash (High/Medium/Low), Gemini 3.1 Pro (High/Low), Gemini 3.1 Flash Lite, Gemini 2.5 series, and Anthropic Claude Sonnet 5 / Claude Opus 4.6.
- **Ollama Cloud Integration**:
  - Migrated from local host daemon setup to the official high-speed Ollama Cloud endpoint (`https://ollama.com/v1`).
  - Added cloud-native open-weight models: Gemma 4 31B, GLM 4.7, GPT-OSS 120B, Qwen 3 Coder 480B, MiniMax M3, and Nemotron 3 series.
- **Zen AI Authentication & Model Catalog**:
  - Updated access type requiring a Zen API Key (`Settings → API Keys`) with support for both Free tier (DeepSeek V4 Flash Free, MiMo 2.5 Free, Nemotron 3.5 Free) and Pro/Paid models (Claude Sonnet 5, Gemini 3.7 Flash, GPT 5.6 Sol).

---

## [1.0.5] - 2026-09-04

### Added
- **Multi-Key Ring Auto-Rotation**:
  - Support for up to 6 API keys per provider in hardware-backed encrypted storage.
  - Instant transparent failover on HTTP 429 rate limit or quota depletion without interrupting ongoing chats.
  - 15-second adaptive cooldown windows with intelligent key ring wrap-around.
- **Decommissioned Models Sanitizer**:
  - Automated translation of decommissioned or deprecated model identifiers to active counterparts.
- **Enhanced PDF Studio Presets**:
  - Added 6 specialized publication-ready templates: `classic`, `modern-minimal`, `corporate-report`, `academic-paper`, `invoice-receipt`, and `resume-cv`.

---

## [1.0.0] - 2026-06-01

### Added
- **Core Architecture Framework**: Initialized native Android application using MVVM and Clean Architecture patterns under the package name `ai.deepcode.android`.
- **DeepCode Zen Free Tier**:
  - Registered the complete suite of Zen free models served via `https://opencode.ai/zen/v1`.
  - Implemented client header `X-OpenCode-Client: android/1.0.0` and authentication parsing.
- **Launcher Icon**: Designed the official adaptive pixel-block vector icon. Foreground incorporates the two-tone `deepcode` letter blocks font, background uses a solid `#0D0D0D` color.
- **Database & Data Storage**:
  - Implemented Room database (`AppDatabase`) with message and session persistence entities.
  - Implemented secure cryptographic preference helper (`EncryptedPrefs`) using AndroidX EncryptedSharedPreferences for developer keys.
- **Agent Integration**:
  - Custom streaming SSE networking engine (`AIProvider`) with support for Gemini, Groq, OpenRouter, Mistral, OpenAI, Anthropic, and local Ollama.
  - Local tool executor (`ToolExecutor`) supporting `read_file`, `write_file`, `list_directory`, `grep_search`, `delete_file`, and `execute_command`.
  - Permission approval dialog workflow for destructive file writes/deletes.
- **Git Service**: Added JGit library dependency to extract repo metadata, branches, status, and commit logs directly in the app.
- **Terminal Runner**: Created native `TerminalRunner` class spawning a shell process with standard or root `su` elevation capabilities.
- **Premium Jetpack Compose UI**:
  - Implemented terminal-themed dark color palette (`Theme.kt`).
  - Created sliding navigation drawer UI coordinator (`MainActivity`).
  - Created a rich markdown chat interface (`ChatScreen`) with soft-keyboard coding bar shortcuts.
  - Added a responsive workspace file tree navigator (`FilesScreen`) supporting context attachment flags.
  - Integrated high-performance **Sora Editor** (`EditorScreen`) supporting syntax colors, undo/redo, wrap toggling, and find-and-replace actions.

### Fixed
- **Package Relayout**: Renamed project package paths and directories from `com.deepcode.android` to `ai.deepcode.android`.
- **Room Compilation Conflicts**: Upgraded Room to stable `2.7.0` to resolve compiler incompatibilities with Kotlin 2.0 metadata formatting.
- **Annotation Class Deprecations**: Configured global dependency exclusions for `annotations-java5` conflicting with JGit metadata tags.
- **Type Collection Errors**: Fixed missing Flow imports (`MutableStateFlow`, `asStateFlow`) inside `FilesScreen.kt`.
- **Variable Shadow Clashes**: Renamed the local variable `current` within `toggleAttachFile` in `FilesScreen.kt` to `attachedSet` to resolve scope shadowing.
- **Compose Icon Incompatibilities**: Replaced newer experimental `Icons.AutoMirrored.Filled.Send` imports with the fully stable standard `Icons.Default.Send`.
- **Editor Reference Errors**: Resolved Sora Editor API conflicts by importing the correct package path `io.github.rosemoe.sora.widget.schemes.EditorColorScheme` and calling `setTextSize(14f)` and `colorScheme = ...`.
