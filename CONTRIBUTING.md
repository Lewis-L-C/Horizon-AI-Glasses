# Contributing to Horizon AI Glasses

Thanks for your interest in contributing! This project is built for a
competition but we welcome any thoughtful contributions.

## Getting started

1. **Fork** the repository and clone your fork.
2. Create a branch for your change:

   ```bash
   git checkout -b feat/your-change
   ```

3. Make your changes. Keep them focused on one concern.
4. Commit with a clear, conventional message:

   ```bash
   git commit -m "feat(translation): add X"      # feature
   git commit -m "fix(navigation): correct Y"     # bug fix
   git commit -m "docs(readme): clarify Z"        # documentation
   ```

5. Push and open a **Pull Request** against `main`.

## Before submitting

- Build locally to make sure nothing is broken:

  ```bash
  ./gradlew assembleDebug        # or gradlew.bat on Windows
  ```

- **Never commit secrets**: API keys, `local.properties`, `.lc` license files,
  keystores, or tokens. Double-check your diff with `git diff --cached`.
- Do not include generated build outputs (`build/`, `.gradle/`, `.idea/`).

## Issue guidelines

- Search existing issues first.
- Include the app version, device model / Android version, and a minimal
  reproduction when reporting a bug.
- For security issues, follow [SECURITY.md](SECURITY.md) and do **not** post
  secrets in public issues.

## Code of conduct

Be respectful and constructive. This is a student project — help make it better,
not harder.
