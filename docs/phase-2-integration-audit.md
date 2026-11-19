# Phase 2 integration audit

## Scope

This phase connects the existing Android control app to the supplied Mora Audio Suite v3.6.0 KernelSU module and verifies that the app reports real device information without overstating audio quality.

## Changes in this audit

- The Android app's module check now calls scripts that exist in the supplied module archive: `get_audio_status.sh` and `viper_status.sh`. It also reports root identity, SELinux mode, and candidate module directories.
- The module report now gathers the status JSON, ViPER status, and AudioFlinger diagnostic output. It no longer calls assumed `report.sh` or `setup_check.sh` entry points.
- Temperature/battery diagnostics use Android system services (`dumpsys battery` and `dumpsys thermalservice`) rather than an assumed module `monitor.sh`.
- USB DAC capability reporting now selects an actual USB output device (`TYPE_USB_DEVICE` or `TYPE_USB_HEADSET`) instead of blindly reading the first output device, which could be the phone speaker or Bluetooth.
- The UI explicitly distinguishes advertised USB capabilities and AudioFlinger mixer information from verified bit-perfect playback.
- Diagnostic output uses actual line breaks.
- A CI archive audit checks ZIP integrity, path traversal, required module entry points, module identity/version metadata, and shell-script syntax before packaging.

## Validation

The GitHub Actions module workflow must pass both the archive audit and flashable ZIP packaging. The Android workflow must pass Rust formatting, Rust tests, Clippy, and Gradle APK assembly for the latest commit before the app changes are considered build-validated.

## Not implemented yet

This audit does not add a native USB Audio Class playback engine, USB permission/endpoint negotiation, JNI bindings from Android to Rust, or system-wide AudioPolicy/Audio HAL routing. The current Rust crate is a portable state/capability model, not a live USB driver. Exclusive playback and bit-perfect output therefore remain unimplemented and unverified. System-wide routing remains ROM/vendor-HAL dependent and must not be presented as guaranteed by root access alone.

## Exit criteria for Phase 2

- [ ] Latest Android APK workflow passes on the final Phase 2 commit.
- [ ] Latest module audit and packaging workflow pass.
- [ ] App controls are mapped only to real module scripts/config keys and report failures accurately.
- [ ] Module installation, status refresh, configuration changes, and rollback are tested on the target Android device with KernelSU.
- [ ] No UI reports exclusive or bit-perfect playback until a real playback path is implemented and measured.
