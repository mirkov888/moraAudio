# Mora Audio Suite module/app integration review

Reviewed against the readable source export of `Mora_Audio_Suite_Unified_v3.6.0.zip`.

## Result

- The source archive passes ZIP integrity, required-file, module identity/version, and shell syntax checks.
- The readable export contains 57 source/document/config files. The previous empty-export bug is fixed.
- The KernelSU flashable package workflow completed successfully for this commit.
- The APK workflow validates/formats/tests/lints the Rust workspace before building the Android app. The APK build for this exact commit was still running at the time of this review.

## App control to module mapping

| App control | Module entry point | Config keys / behavior | Review |
|---|---|---|---|
| Listening modes | `set_audio_mode.sh` | `audiophile`, `movie`, `power_saver` | Real mapping. Applies properties and optional tinymix controls; may restart audioserver. |
| Bass Boost toggles/sliders | `bass_ctl.sh set` | `BB_BASS`, `BB_STRENGTH`, `BB_DP`, `BB_GAIN`, `BB_LOUDNESS`, `BB_TARGET` | Real mapping. Live effect depends on the separate `dev.kazuyoo.bassboost` app/service being installed. |
| System tuning | `mora_cfg.sh set` | `VOLSTEPS100`, `SAFEVOL_OFF`, `SPATIALIZER_OFF`, `VOLLISTENER_OFF`, `RYSOUND` | Keys are accepted by the module config validator. Some changes require apply/restart/reboot. |
| Apply now | `mora_cfg.sh apply` + `bass_ctl.sh apply` | Reapplies props and BassBoost values | Real mapping; BassBoost apply reports failure if its companion app is absent. |
| Driver repair | `viper_fix.sh fix` | ViPER registration/load repair | Real mapping; success still depends on ROM HAL and driver compatibility. |
| Audio restart | Android root command | `setprop ctl.restart audioserver` | Not a module script; requests Android audioserver restart. |
| Backup export | `backup_ctl.sh export` | Archives `/data/adb/mora_audio_suite` | Real mapping. App currently displays the script JSON response as output. |

## Diagnostic script discrepancy

The ZIP contains `setup_check.sh`, `report.sh`, and `monitor.sh`. They are not missing. The current Android app's diagnostic actions instead assemble their own partial checks:

- Installation check calls `get_audio_status.sh` and `viper_status.sh`, plus root/path checks, rather than `setup_check.sh`.
- Full report calls status scripts and `dumpsys media.audio_flinger`, rather than `report.sh`.
- Monitor reads Android `dumpsys battery` and `dumpsys thermalservice`, rather than the module's `monitor.sh`.

This is not a broken script invocation, but it duplicates and bypasses available module diagnostics. A follow-up app change should call the three existing scripts and format their output, preserving a fallback when an older installed module lacks them.

## USB / exclusive playback boundary

The module's `get_audio_status.sh` detects USB audio from ALSA procfs card/stream data and reads Android audio dumps. This is useful status telemetry, but it does not:

- open a USB Audio Class interface or endpoint;
- request and retain Android USB host permission;
- negotiate UAC alternate settings, endpoint format, sample rate, packet size, or clock feedback;
- own an exclusive playback stream or prevent AudioFlinger/system mixing;
- verify sample identity end-to-end or measure bit-perfect output.

`set_audio_mode.sh` changes system properties and optional mixer controls; it is not an exclusive USB playback implementation. Root access and the module alone do not provide global exclusive routing on an arbitrary ROM.

The current Rust crate remains a platform-independent capability/state model. A real exclusive player still requires an Android USB Host/UAC data path, a native playback engine, and Rust/JNI integration. System-wide routing would additionally require compatible ROM AudioPolicyManager/vendor Audio HAL support.

## Safe next implementation order

1. Use the module's existing diagnostics scripts from the app and render their output clearly.
2. Add Android USB Host permission/device/interface discovery and expose the actual UAC descriptors to the Rust core.
3. Implement endpoint negotiation and a PCM streaming engine in a dedicated native layer.
4. Add measured playback diagnostics; only label a session exclusive/bit-perfect when the active stream and data path are verified.
5. Keep system-wide routing as a separate ROM/HAL-specific capability, not a generic KernelSU toggle.
