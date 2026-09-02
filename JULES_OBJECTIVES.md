# DeepCode Autonomous Self-Evolution Directives (for Jules)

This document outlines the core architectural principles, performance benchmarks, and evolution roadmap for **Jules** and autonomous AI agents working on the **DeepCode** repository (`Deep007h/DeepCode`).

---

## 🎯 Primary Vision & Pillars

DeepCode is designed to be the fastest, smoothest, and most capable developer AI coding environment on Android. Every improvement cycle should target one or more of these three pillars:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          DEEPCODE TRINITY                               │
├──────────────────────┬──────────────────────────┬───────────────────────┤
│  ⚡ Zero-Latency AI  │  🎨 120Hz Ultra-Smooth   │  🛠️ Unlimited Plugin  │
│     Inference & SSE  │     Jetpack Compose UI   │     & Developer Tools │
└──────────────────────┴──────────────────────────┴───────────────────────┘
```

---

## 1. ⚡ Pillar 1: Zero-Latency AI & Network Optimization

When optimizing remote networking and inference streams:
- **Time-To-First-Token (TTFT)**:
  - Keep prompt headers concise. Avoid passing bulky 15+ tool schemas on standard conversational or pure code-generation prompts.
  - Use smart tool trigger detection (`shouldIncludeTools()`) in `ZenProvider` and all `AIProvider` implementations.
- **Connection Reuse**:
  - Maintain persistent HTTP/2 connection pools (`ConnectionPool(8, 5, TimeUnit.MINUTES)`) with TCP keep-alive to avoid repetitive TLS handshakes.
- **Unbuffered SSE Dispatch**:
  - Stream tokens directly to UI state flows with zero line-buffering delay.
- **Silent Multi-Key Ring Rotation**:
  - Ensure `ApiKeyRotator` and `ChatScreen` immediately failover to alternative slots (1..6) upon HTTP 429 / rate limits without throwing user-facing disruptions.

---

## 2. 🎨 Pillar 2: 120Hz Ultra-Smooth Jetpack Compose UI

When refactoring or styling the Compose UI layer:
- **Recomposition Optimization**:
  - Use `remember` with precise keys for message formatting and markdown parsing.
  - Use `derivedStateOf` for scroll state observers, active turn counters, and dynamic colors.
  - Wrap UI models in `@Immutable` or `@Stable` data classes to enable Compose compiler skipping.
- **Lazy List Performance**:
  - Provide unique, stable keys (`key = { message.id }`) and explicit `contentType` for all `LazyColumn` and `LazyRow` items.
  - Avoid heavy allocations or Regex parsing inside `@Composable` drawing loops; perform pre-calculations in ViewModels or background coroutines.
- **Micro-Interactions & Fluid Animations**:
  - Use smooth physics-based springs (`spring(stiffness = Spring.StiffnessMediumLow)`) for drawers, message bubbles, and bottom sheets.
  - Respect system refresh rates (60Hz / 90Hz / 120Hz).

---

## 3. 🛠️ Pillar 3: Tools & Plugins Expansion

When adding new tools to `ToolExecutor.kt` and `PluginEngine.kt`:
- **Document & Office Tools**:
  - Markdown to EPUB / DOCX / HTML converters.
  - OCR Text Extractor (extract code and text from attached images).
  - Code diff & patch applier.
- **Developer Utilities**:
  - Code formatter & syntax checker (Kotlin, Python, JSON, YAML).
  - Regular expression tester & visualizer.
  - Network ping & HTTP header inspector.
  - Base64/Hex/JWT inspector & decoder.
- **Media & Audio**:
  - Audio waveform generator & speech pitch customizer for `EdgeTtsEngine`.
  - Image resizer, compressor, and palette extractor.

---

## 🔒 Codebase Integrity & Security Rules

1. **Security & Cryptography**:
   - Never hardcode secret keys or API tokens.
   - All credentials must be saved via `EncryptedPrefs` with AES-256-GCM backed by the Android Keystore.
2. **Clean Architecture**:
   - Preserve MVVM separation: `data` (Room, Network) → `domain` (AgentEngine, Models) → `ui` (Compose).
3. **Build Stability**:
   - All changes must compile without errors (`./gradlew compileDebugKotlin` / `./gradlew assembleDebug`).
   - Preserve backward compatibility with Android 8.0+ (Min SDK 26, Target SDK 34).
