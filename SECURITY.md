# Security Policy

## Reporting a vulnerability

If you find a security issue in **Horizon AI Glasses**, please do **not** open a
public issue with the details.

Instead, report it privately by opening a [security advisory](https://github.com/Lewis-L-C/Horizon-AI-Glasses/security/advisories/new)
on this repository, or by contacting the maintainer through GitHub.

**Never include the following in a public issue:**

- API keys or secrets (DeepSeek / GLM / AMap / Rokid `CLIENT_SECRET`)
- Rokid device license (`.lc`) content
- Tokens, device authentication data, or test accounts
- Personally identifiable information

## Secrets in this repository

- All cloud API keys are injected from a local `local.properties` file, which is
  git-ignored and must never be committed.
- The repository ships only `local.properties.example` with placeholder values.
- The Rokid device license `.lc` file in `app/src/main/res/raw/` is a
  **placeholder**; real licenses are device-specific and must not be committed.
- If you accidentally commit a secret, rotate it immediately (regenerate the key
  or license) and remove it from history — treat the old value as compromised.

## Permissions & data

- The app requests camera, microphone, location, Bluetooth and media permissions
  only when the corresponding scenario needs them.
- Raw audio and continuous camera frames are processed on-device and are not
  uploaded by default; only recognized text or a compressed single frame is sent
  to cloud services when a scenario requires it.

## Scope

This policy covers the code in this repository. Third-party components (Rokid
SDK, AMap SDK, Vosk, TensorFlow Lite, ML Kit, DeepSeek, Zhipu) are governed by
their own security processes and license terms.
