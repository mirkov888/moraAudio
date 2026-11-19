# USB DAC implementation plan

## System-wide mode

System-wide output uses Android's audio routing and the device/vendor Audio HAL. Mora Audio will first observe available output devices and active routing without changing system state. Routing controls are introduced only after verifying the ROM's supported audio-policy mechanism. A normal rooted app cannot guarantee that every application is routed to USB without cooperation from AudioPolicyManager/Audio HAL.

## Exclusive mode

Exclusive playback is a direct stream owned by Mora Audio's playback component. Android USB Host permission, USB Audio Class descriptors, endpoint format negotiation, buffer scheduling, and disconnect handling are required. It cannot give every app simultaneous exclusive access.

## Bit-perfect validation

Validation must establish source PCM format, negotiated USB endpoint format, absence of DSP/resampling in Mora Audio's path, and ideally verify captured digital output using a loopback/analyzer. UI labels must distinguish advertised DAC capability, negotiated stream format, and verified bit-perfect output.

## Rollback

Any future privileged audio-policy modification must be reversible, backed up, scoped to the target ROM, and disabled by default until device tests pass.
