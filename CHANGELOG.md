# 📝 Changelog

All notable changes and milestones for the **DeepCode for Android** port are documented below.

---

## [1.0.0] - 2026-06-01

### Added
- **Core Architecture Framework**: Initialized native Android application using MVVM and Clean Architecture patterns under the package name `ai.deepcode.android`.
- **DeepCode Zen Free Tier**:
  - Registered the complete suite of Zen free models (including Big Pickle, DeepSeek V4 Pro, MiniMax M2.7, Kimi, GLM, and Qwen) served via `https://api.deepcode.ai/v1`.
  - Implemented client header `X-DeepCode-Client: android/1.0.0` and authentication parsing.
  - Added anonymous access mode for the flagship **Big Pickle** model allowing zero-setup usage on first install.
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
