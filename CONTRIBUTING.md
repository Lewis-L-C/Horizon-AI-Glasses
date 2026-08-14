# Contributing to Horizon AI Glasses

Thanks for your interest in contributing! Whether it's a bug report, a new
voice action, a translation fix, or documentation — all contributions are
welcome.

## Project overview

Horizon AI Glasses is a **voice-first, edge-cloud collaborative** assistant for
Rokid AI glasses. It runs on Android (Kotlin) and orchestrates offline ASR
(Vosk), on-device vision (TensorFlow Lite / ML Kit), cloud LLMs (DeepSeek,
Zhipu GLM) and map services (AMap) behind a single voice entry point with **27
voice actions**.

> 📖 Read [`README.md`](README.md) and [`docs/architecture/architecture.md`](docs/architecture/architecture.md)
> before diving in.

## Setting up the project

1. **Requirements**
   - JDK 17+
   - Android Studio (AGP 8.9 / Gradle 8.11.1)
   - Android SDK with `compileSdk 36` (minSdk 28)
   - An arm64-v8a Android device (emulators are not supported for camera/glasses flows)

2. **Clone & open**
   ```bash
   git clone https://github.com/Lewis-L-C/Horizon-AI-Glasses.git
   cd Horizon-AI-Glasses
   # open the folder in Android Studio and let it sync
   ```

3. **Restore required SDK / model files** (they are intentionally not in the
   repo): follow [`docs/development/required-sdk-files.md`](docs/development/required-sdk-files.md)
   — AMap SDK, Rokid native libs, Vosk models and the Rokid `.lc` license.

4. **Configure keys**
   ```bash
   cp local.properties.example local.properties
   # fill in DEEPSEEK_API_KEY / GLM_API_KEY / AMAP_API_KEY / ROKID_CLIENT_SECRET
   ```
   See [`docs/development/configuration.md`](docs/development/configuration.md).
   Never commit `local.properties`.

5. **Build**
   ```bash
   ./gradlew assembleDebug        # macOS / Linux
   gradlew.bat assembleDebug      # Windows
   ```

## Repository layout

```
app/src/main/java/com/blue/glassesapp/
├── core/          # CXR connection, Bluetooth HID, GreenDAO DB, permissions, utils
├── common/        # shared models, data-binding helpers, widgets
└── feature/
    ├── init/                  # launcher
    ├── scanblueroorh/         # Bluetooth scan & pairing
    └── home/                  # main UI + all capability modules
        ├── ui/                # activities, analyzers, TTS/ASR/translation managers
        ├── vm/                # ViewModels
        └── model/             # domain helpers
```

## How to add a new voice action

Voice commands flow through `feature/home/ui/VoiceTranslationManager.kt`.
To add a new action:

1. **Add the intent name** to the action list in `analyzeIntent()`'s prompt
   (so DeepSeek can return it), with example commands.
2. **Handle it** in the `when (intent)` block of `executeIntent()` — call the
   underlying capability (or add a new one).
3. **Wire the capability** in the place where `VoiceTranslationManager` is
   constructed (`HomeFragment` / `RealTimeTranslateActivity`), passing a new
   callback lambda if needed.
4. Optionally add a **local fast-match** branch (no LLM) for high-frequency
   commands — see the sports / 3D-navigation branches.

Also update [`docs/architecture/voice-actions.md`](docs/architecture/voice-actions.md)
so the action list stays in sync.

## How to add a new on-device capability

- Add a new `*Analyzer` under `feature/home/ui/` following
  `TrafficLightAnalyzer` / `BlindRoadAnalyzer` (TFLite) or
  `QRCodeAnalyzer` (ML Kit) as a template.
- Register the model asset under `app/src/main/assets/` (keep model size in
  mind — files over 100 MB cannot be pushed to GitHub).
- Connect it to a voice action as described above.

## Code style

- **Kotlin**: follow the surrounding style (2-space indent, official Kotlin
  style per `gradle.properties`). The codebase uses `viewBinding` /
  `dataBinding`.
- **Comments**: keep them concise and in Chinese where the surrounding code is
  Chinese — consistency matters more than language.
- **Do not** commit secrets, SDK binaries, build outputs, or IDE files.
  Double-check with `git status` before pushing.

## Commit & PR workflow

1. Fork the repo and clone your fork.
2. Create a focused branch:
   ```bash
   git checkout -b feat/add-something
   ```
3. Make your change, then build locally:
   ```bash
   ./gradlew assembleDebug
   ```
4. Commit with a clear, conventional message:
   ```
   feat(voice): add a new "summarize screen" action
   fix(navigation): correct POI search for empty queries
   docs(readme): clarify key configuration
   ```
   One logical change per commit.
5. Push and open a **Pull Request** against `main`:
   ```bash
   git push -u origin feat/add-something
   ```
6. In the PR description, briefly explain what changed and how you verified it
   (build result, screenshots, test steps).

## Issue guidelines

- Search existing issues first.
- For bugs, include: device model / Android version, app version, reproduction
  steps, and any logcat output.
- For feature requests, describe the scenario and why it matters for a
  hands-free smart-glasses workflow.

## Security

- **Never** post API keys, `local.properties`, `.lc` license files, keystores
  or device credentials in issues or PRs.
- Follow [`SECURITY.md`](SECURITY.md) to report vulnerabilities privately.
- When reviewing a PR, watch specifically for hardcoded secrets — use
  `BuildConfig.*` fields injected from `local.properties` instead.

## Code of conduct

Be respectful and constructive. This is a student competition project — help
make it better, not harder.
