# Configuration

Cloud capabilities (chat, translation, simultaneous interpretation, photo
problem-solving, maps, glasses auth) require API keys. Keys are injected at
build time from your local `local.properties` — which is **git-ignored** and
must never be committed.

## 1. Create `local.properties`

Copy the template:

```bash
# macOS / Linux
cp local.properties.example local.properties
# Windows (PowerShell)
Copy-Item local.properties.example local.properties
```

## 2. Fill in your keys

```properties
# Android SDK location (required to build)
sdk.dir=/path/to/your/Android/Sdk

# DeepSeek API key — chat / translation / simultaneous / intent analysis
DEEPSEEK_API_KEY=your_deepseek_api_key_here

# Zhipu / GLM API key — photo problem-solving (GLM-4V)
GLM_API_KEY=your_glm_api_key_here

# AMap (Gaode) API key — map / location / route planning
AMAP_API_KEY=your_amap_api_key_here

# Rokid CXR device client secret — glasses device authentication
ROKID_CLIENT_SECRET=your_rokid_client_secret_here
```

Where to get each key:

| Key | Provider | Where to sign up |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | DeepSeek | https://platform.deepseek.com/ |
| `GLM_API_KEY` | Zhipu AI (智谱) | https://open.bigmodel.cn/ |
| `AMAP_API_KEY` | AMap (高德) | https://lbs.amap.com/ |
| `ROKID_CLIENT_SECRET` | Rokid developer portal | see CXR-M SDK docs |

## 3. How the keys are used

- `app/build.gradle` reads `local.properties` and generates `BuildConfig`
  fields:
  - `BuildConfig.DEEPSEEK_API_KEY` → used by `DeepSeekChat.kt` and
    `VoiceTranslationManager.kt`
  - `BuildConfig.GLM_API_KEY` → used by `ProblemSolver.kt`
  - `BuildConfig.ROKID_CLIENT_SECRET` → used by `CommonModel.kt` → `CxrUtil.kt`
  - `AMAP_API_KEY` → injected into `AndroidManifest.xml` as the
    `com.amap.api.v2.apikey` meta-data value
- If a key is empty, the corresponding cloud feature will fail its auth at
  runtime (the app still builds and the on-device features still work).

## 4. On-device keys note

- The **Rokid device license** (`.lc` file) is separate from the secret above;
  see [rokid-device-license.md](rokid-device-license.md).
- For production, cloud keys should be moved out of the app to a server-side
  proxy. This prototype keeps them client-side for demonstration, which is
  documented in the README's known limitations.

## 5. Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| DeepSeek/translation returns 401 | `DEEPSEEK_API_KEY` missing or invalid |
| Photo solving returns auth error | `GLM_API_KEY` missing or invalid |
| Map shows blank / no location | `AMAP_API_KEY` missing or invalid, or AMap SDK files not restored |
| Glasses don't connect | `.lc` license not replaced, or `ROKID_CLIENT_SECRET` missing |
| Build fails on missing file | SDK/model files not restored — see [required-sdk-files.md](required-sdk-files.md) |
