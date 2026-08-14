# Required SDK / Model Files

This project depends on a few third-party SDKs and models that are **not
committed to the public repository**, either because their license / terms
don't permit redistribution, or because they are large standard downloads.
Before the project can build and run, restore the files below to the exact
paths shown.

> **Why are they not in the repo?** See
> [`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md). The repository code
> is MIT-licensed, but third-party components keep their own licenses. We
> deliberately don't bundle proprietary SDK binaries in a public repo.

## 1. AMap (Gaode) SDK

Used by `MapNavigationManager.kt` (location, POI search, route planning).

| File | Target path |
| --- | --- |
| AMap SDK AAR `AMap3DMap_11.2.000_AMapSearch_9.8.0_AMapLocation_11.2.000_20260603.aar` | `app/src/main/assets/maps/AMap3DMap_11.2.000_AMapSearch_9.8.0_AMapLocation_11.2.000_20260603.aar` |
| AMap navigation native lib `libAMapSDK_NAVI_v11_2_000.so` | `app/src/main/jniLibs/arm64-v8a/` |
| AMap location native lib `libapssdk.so` | `app/src/main/jniLibs/arm64-v8a/` |

- Download from the **AMap open platform** (高德开放平台): https://lbs.amap.com/
- An **AMap API key** (`AMAP_API_KEY`) is also required — see
  [configuration.md](configuration.md).
- The build file references the AAR by the exact filename above
  (`app/build.gradle` → `implementation files('src/main/assets/maps/...')`).
  If you use a different SDK build, place the AAR with the same name at that
  path, or update the dependency line accordingly.

> Tip: the older 2.x AMap SDK distributed its `.so` via the AAR; the native
> files listed above are what this project's `jniLibs` expect. If a newer SDK
> bundles them inside its AAR, you may not need the separate `.so` files — but
> the current build setup expects them in `jniLibs/arm64-v8a/`.

## 2. Rokid CXR native libraries

Used at runtime by the CXR-M SDK (glasses connection / near-eye display).

| File | Target path |
| --- | --- |
| `libneonuijni_public.so`, `libneonui_shared.so`, `libnui.so`, `libopenssl.so`, `libc++_shared.so` | `app/src/main/jniLibs/arm64-v8a/` |

- Obtain from the **Rokid developer portal** (see the CXR-M SDK docs).
- The `com.rokid.cxr:client-m` Maven dependency itself is resolved from
  `https://maven.rokid.com` at build time (already configured in
  `settings.gradle`).

## 3. Vosk offline ASR models

Used by `VoiceVoskService.kt` for offline Chinese/English recognition.

| Model | Target path | Typical source |
| --- | --- | --- |
| Chinese model (`am/`, `conf/`, `graph/`, `ivector/`) | `app/src/main/assets/model/` | https://alphacephei.com/vosk/models |
| English model (`am/`, `conf/`, `graph/`, `ivector/`) | `app/src/main/assets/model_en/` | https://alphacephei.com/vosk/models |

- The English model in this project is the "accurate universal" English model
  (based on the Appen Kaldi model, dynamic-graph version) — roughly
  `vosk-model-en-us-0.22`. The Chinese model is the mobile-oriented Chinese
  model (CER report shipped in its `README`).
- These models are Apache-2.0 licensed and freely downloadable. `VoiceVoskService`
  switches models at runtime when the user starts simultaneous interpretation.

## 4. Rokid device license (`.lc`)

The app authenticates with Rokid glasses using an `.lc` license file plus the
`CLIENT_SECRET`. The repo contains a **placeholder** at:

```
app/src/main/res/raw/a1e15aabfb1e4a88bbaf97e31121a84b.lc
```

Replace it with your own license file obtained from the Rokid developer portal.
The placeholder keeps the build green; the device connection will fail at
runtime until a valid license is present. See
[rokid-device-license.md](rokid-device-license.md).

## 5. Self-trained TFLite models (already in the repo)

These two models are project assets and **are** committed:

- `app/src/main/assets/blindpath/best_int8.tflite` — blind-path detection (int8)
- `app/src/main/assets/traffic/best_traffic_med_yolo_v8_float32.tflite` — traffic-light detection (float32)

## Verification

After restoring the files, a build should succeed:

```bash
./gradlew assembleDebug        # macOS / Linux
gradlew.bat assembleDebug      # Windows
```
