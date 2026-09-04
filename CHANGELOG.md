# 📝 Changelog

All notable changes and milestones for **DeepCode for Android** are documented below.

---

## [1.1.0] - 2026-09-04

### Added
- **Headless ChatGPT Engine**:
  - Integrated browser session bridge (`ChatGPTHeadlessBridge`) in the Connections tab allowing seamless interaction with OpenAI's consumer web platform at zero API token cost.
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
