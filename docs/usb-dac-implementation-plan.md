# USB DAC implementation plan

## System-wide mode

System-wide output uses Android's audio routing and the device/vendor Audio HAL. Mora Audio will first observe available output devices and active routing without changing system state. Routing controls are introduced only after verifying the ROM's supported audio-policy mechanism. A normal rooted app cannot guarantee that every application is routed to USB without cooperation from AudioPolicyManager/Audio HAL.

## Exclusive mode

Exclusive playback is a direct stream owned by Mora Audio's playback component. Android USB Host permission, USB Audio Class descriptors, endpoint format negotiation, buffer scheduling, and disconnect handling are required. It cannot give every app simultaneous exclusive access.

## Bit-perfect validation

Validation must establish source PCM format, negotiated USB endpoint format, absence of DSP/resampling in Mora Audio's path, and ideally verify captured digital output using a loopback/analyzer. UI labels must distinguish advertised DAC capability, negotiated stream format, and verified bit-perfect output.

## Rollback

Any future privileged audio-policy modification must be reversible, backed up, scoped to the target ROM, and disabled by default until device tests pass.

## Current implementation checkpoint

- The Android app requests USB Host permission for a detected AudioStreaming interface.
- After permission is granted, it briefly opens the device, reads `UsbDeviceConnection.getRawDescriptors()`, and closes the connection immediately.
- `UsbAudioDescriptorParser` defensively walks the returned descriptor stream and reports AudioControl/AudioStreaming interfaces, UAC protocol version, Type I channel/bit-depth fields, UAC1 discrete or continuous sample-rate declarations, and endpoint transfer/synchronization attributes.
- UAC2/UAC3 clock rates are deliberately not guessed from the format descriptor. A later clock-entity control-query stage is required to discover the current/ranged clock rates.
- The parser is observational only. It does not claim an interface, select an alternate setting, send class-specific control requests, or transfer PCM.

## Next engineering gates

1. Validate parser output against raw descriptor captures from multiple real DACs (UAC1 and UAC2).
2. Add UAC2 clock entity graph parsing and safe GET_RANGE/GET_CUR requests with per-device capability reporting.
3. Design endpoint ownership and alternate-setting negotiation.
4. Prototype isochronous transport separately; do not treat Android bulk transfer APIs as a substitute for UAC isochronous audio.
5. Only then integrate a playback engine and test disconnect, underrun, format switching, and bit-perfect claims.
