# Mora Audio

Mora Audio is a standalone Android control app for the **Mora Audio Suite** root module.
It is not a music player yet: the current source controls the module's listening presets,
ViPER driver status, BassBoost, selected audio tuning switches, and diagnostic actions.

## Requirements

- Android 8.0 (API 26) or newer.
- Mora Audio Suite module installed at one of the supported module paths.
- Root access granted explicitly to Mora Audio through KernelSU, Magisk, or APatch.
- The module scripts and JSON status fields expected by this app must match the installed module version.

Without the module, the app can open but module-dependent controls will report that the module is unavailable.
The app does not request network access and does not currently connect to Mora Shield.

## Build

The project uses Android Gradle Plugin 8.5.2, Gradle 8.7, Java 17, and compileSdk 34.

### GitHub Actions

1. Push this project to a GitHub repository.
2. Open **Actions** and run **Build APK** (or push a commit).
3. Download the `MoraAudio-apk` artifact.

The workflow explicitly installs `platform-tools`, `platforms;android-34`, and
`build-tools;34.0.0`. It deliberately does **not** request the obsolete SDK package
named `tools`, which causes `Failed to find package 'tools'` on current SDK repositories.

### Local build

```bash
gradle assembleDebug
```

## Root command safety

The root bridge uses `su -c`, restricts module script names and argument characters,
and is only intended for fixed app-owned commands. Do not add an exported activity,
service, receiver, or IPC endpoint that accepts arbitrary shell commands.

## Mora ecosystem compatibility

Mora Audio remains independent during this development phase. No Mora Shield connector,
network endpoint, account, or background telemetry is included.

A future integration can be added as an optional connector module after the Mora connector
protocol and permissions are stable. The audio controls must continue to work when
Mora Shield is absent, disconnected, or uninstalled. Connector access should be opt-in,
capability-scoped, authenticated, and must never expose arbitrary root shell execution.

## Current limitations

- This APK controls the installed root module; it does not implement audio decoding or
  local music playback.
- The source archive does not include the `mora_audio_suite` module scripts, so their
  compatibility cannot be verified from this project alone.
- A successful GitHub Actions build is still required to confirm compilation against
  the Android SDK, followed by testing on the target device.
