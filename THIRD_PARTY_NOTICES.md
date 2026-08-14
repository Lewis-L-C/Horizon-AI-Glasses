# Third-Party Notices

This project builds on a number of third-party components. They remain under
their own licenses and terms; **the MIT License of this repository does not
apply to them**. Where a component's license or distribution terms prevent us
from redistributing it, the file is intentionally **not** included in this
repository and must be obtained from the official channel listed below.

## Software / libraries

| Component | Purpose | Source | License / Terms |
| --- | --- | --- | --- |
| AndroidX (Core, AppCompat, CameraX, Lifecycle, Fragment, Activity) | platform & UI | maven.google.com | Apache-2.0 |
| Kotlin stdlib | language runtime | JetBrains / mavenCentral | Apache-2.0 |
| Rokid CXR-M SDK (`com.rokid.cxr:client-m`) | glasses connection & near-eye display | maven.rokid.com | Rokid proprietary (not redistributed) |
| Rokid native libs (NeonUI / nui / openssl) | glasses runtime support | Rokid developer portal | Rokid proprietary (not included) |
| AMap (Gaode) SDK | map / location / POI / route | lbs.amap.com | AMap proprietary (not included) |
| Vosk (Kaldi) | offline speech recognition | alphacephei.com/vosk | Apache-2.0 (models: see notes below) |
| Android TTS | speech synthesis | Android platform | Android OS |
| ML Kit Text Recognition (Chinese) / Barcode Scanning | OCR & QR | Google | Google ML Kit Terms of Service |
| TensorFlow Lite | on-device inference | TensorFlow | Apache-2.0 |
| QMUI Android | UI components | Tencent QMUI | Apache-2.0 |
| GreenDAO | local database | greenrobot | Apache-2.0 |
| OkHttp / Okio | networking | Square | Apache-2.0 |
| Retrofit / Gson | networking / JSON | Square / Google | Apache-2.0 |
| EventBus | in-app event bus | greenrobot | Apache-2.0 |
| Glide | image loading | bumptech | BSD-2-Clause |
| kotlinx-coroutines | async | JetBrains | Apache-2.0 |
| utilcodex | Android utils | Blankj | Apache-2.0 |
| JNA | native access | jna.dev | Apache-2.0 (LGPL exceptions) |
| Google WebRTC | audio processing dependency | webrtc.org | BSD-3-Clause |
| Ultra AV Loading (com.wang.avi) | loading animations | wangjiegulu | MIT |

## Models

| Model | Source | License / Terms |
| --- | --- | --- |
| Vosk Chinese acoustic/language model (`assets/model/`) | alphacephei.com/vosk/models | Apache-2.0 (downloadable) |
| Vosk English model (`assets/model_en/`) | alphacephei.com/vosk/models (based on Appen Kaldi) | Apache-2.0 (downloadable) |
| Blind-path YOLOv8 int8 TFLite (`assets/blindpath/`) | self-trained for this project | project asset (included) |
| Traffic-light YOLOv8 float32 TFLite (`assets/traffic/`) | self-trained for this project | project asset (included) |

The two self-trained TFLite models are built on the YOLOv8 architecture
(Ultrastrycs Ultralytics). If you distribute or use them, please check and
honor the applicable Ultralytics model licensing terms.

## Cloud services

| Service | Purpose | Terms |
| --- | --- | --- |
| DeepSeek API | chat / translation / intent analysis | DeepSeek Platform terms |
| Zhipu AI (GLM-4V) | photo problem-solving | Zhipu Open Platform terms |
| AMap open platform | map / location / route services | AMap terms (requires your own API key) |

## Files intentionally not included in this repository

These files are needed at build/runtime but are **not committed** because of
their license terms or size. See [`docs/development/required-sdk-files.md`](docs/development/required-sdk-files.md)
for the exact target paths and how to obtain them:

- `app/src/main/assets/maps/` — AMap SDK AAR(s)
- `app/src/main/jniLibs/arm64-v8a/` — AMap & Rokid native libraries
- `app/src/main/assets/model/`, `app/src/main/assets/model_en/` — Vosk models
- a real Rokid device license `.lc` file (replaces the placeholder at
  `app/src/main/res/raw/a1e15aabfb1e4a88bbaf97e31121a84b.lc`)
