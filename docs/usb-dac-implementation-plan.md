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
- UAC2 clock source entities with host-readable frequency controls are discovered from the AudioControl descriptors. The app issues read-only GET_CUR and bounded GET_RANGE requests, reports current/ranged frequencies, and never sends SET requests. UAC3 clock control remains unimplemented.
- UAC2 clock topology parsing now links each AudioStreaming `terminalLink` to its AudioControl terminal and follows its clock-source reference through Clock Multiplier entities.
- Clock Selector entities with multiple inputs are reported as ambiguous; the parser deliberately does not guess the selected pin. A future read-only selector GET_CUR step must resolve that pin before querying a source as active.
- The topology resolver detects missing entity references and cycles. Synthetic tests cover direct source, multiplier, ambiguous selector, and cycle cases.
- The inspector now limits GET_CUR/GET_RANGE requests to readable Clock Sources reachable from an AudioStreaming terminal through a direct terminal clock reference or a single-input Clock Multiplier/Selector path. Unrelated sources are not queried. A multi-input Clock Selector is intentionally skipped until its selected pin can be resolved. Even a reachable source is topology evidence, not proof of the active hardware clock without device validation.
- The scanner reports Android-exposed interface alternate-setting numbers, endpoint transfer types, maximum packet sizes, and polling intervals. This is inventory only; it does not select an alternate setting or claim an interface.
- UAC2 clock GET_CUR/GET_RANGE requests are read-only class-specific control requests. No clock SET request is sent.\n- The parser does not claim a streaming interface or transfer PCM.

## Next engineering gates

1. Validate parser output against raw descriptor captures from multiple real DACs (UAC1 and UAC2).
2. Validate UAC2 clock entity graph traversal, selector/clock-multiplier topologies, and GET_RANGE/GET_CUR responses across real devices.
3. Implement safe alternate-setting selection and interface ownership with explicit user-visible state and rollback on failure.
4. Prototype isochronous transport separately; do not treat Android bulk transfer APIs as a substitute for UAC isochronous audio.
5. Only then integrate a playback engine and test disconnect, underrun, format switching, and bit-perfect claims.
