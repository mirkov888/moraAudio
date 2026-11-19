# Mora Audio Rust Core — Phase 1

## Scope

The `mora-audio-core` crate is the platform-independent foundation for the Android audio engine. It currently provides:

- output mode and PCM format types;
- USB audio capability validation;
- exact PCM capability matching;
- deterministic device and exclusive-session state transitions;
- unit tests for invalid states, unsupported formats, and detach behavior.

## Explicit non-goals in Phase 1

This crate does **not** yet access Android USB Host APIs, open a USB Audio Class endpoint, change AudioFlinger routing, modify vendor audio policy, or claim bit-perfect playback. Those require Android platform adapters and device-level validation. Root access alone does not bypass AudioFlinger or grant a normal app arbitrary ownership of every app's audio stream.

## Planned adapters

1. Android/Kotlin USB device discovery and permission handling.
2. JNI boundary to expose device descriptors and events to Rust.
3. KernelSU service for privileged diagnostics and carefully scoped policy operations.
4. A separate direct-playback backend for exclusive PCM sessions.
5. System-wide routing integration, gated by ROM/HAL compatibility checks and rollback.

## Safety invariants

- Never write vendor audio policy files during detection.
- Never restart audioserver automatically during initial discovery.
- Exclusive ownership is exclusive to Mora Audio's own stream; other applications may be interrupted or continue through the Android mixer depending on platform policy.
- A disconnected device always revokes the engine's session state.
- Bit-perfect is a measured property of the actual output path, not inferred from a DAC's advertised capabilities.
