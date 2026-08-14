# Rokid Device License (`.lc`)

## What it is

The app authenticates with Rokid glasses through the CXR-M SDK. The
authentication needs:

1. a **license file** (`.lc`) stored in the app's `res/raw`, and
2. the **client secret** (`ROKID_CLIENT_SECRET` in `local.properties`).

Both are obtained from the Rokid developer portal for your developer account /
device. The `.lc` file is device/account-specific.

## What's in the repository

The repository ships a **placeholder** (plain-text note) at:

```
app/src/main/res/raw/a1e15aabfb1e4a88bbaf97e31121a84b.lc
```

The `CxrUtil.readRawFile()` code reads this resource by its resource id
(`R.raw.a1e15aabfb1e4a88bbaf97e31121a84b`), so the file must exist for the
build to succeed. The placeholder keeps the build green.

## What you must do

1. Get your real `.lc` file from the Rokid developer portal.
2. Replace the placeholder content with the real file (keep the same filename).
3. Set `ROKID_CLIENT_SECRET` in your local `local.properties`.

## Security

- **Never commit a real `.lc` file or `ROKID_CLIENT_SECRET`** to a public
  repository. They are device authentication credentials.
- If one leaks, request a new license / regenerate the secret and treat the old
  value as compromised.
