# Mora Audio — ecosystem integration notes

## Current phase

Mora Audio is a standalone Android app and remains useful without any other Mora app.
The current build does not connect to Mora Shield and does not send telemetry.

## Future optional integration

When the Mora Connector SDK and host protocol are stable, integration may be added as
an optional capability-based connector:

- `audio.status.read`: read driver/module status and non-sensitive diagnostics.
- `audio.profile.select`: select one of the app-supported listening profiles.
- `audio.settings.write`: change explicitly allowlisted audio settings, with user consent.
- `audio.diagnostics.read`: read a bounded diagnostic report with sensitive paths/data removed.

Do not expose a generic `shell.execute`, arbitrary command string, or unrestricted root
operation through the connector. The connector must not become a remote root terminal.

## Compatibility rules

- Keep the standalone app's local UI and controls operational when Shield is absent.
- Connector installation/connection is separate from module installation/root permission.
- Show distinct states: Shield not installed, installed but disconnected, and connected.
- Require explicit user opt-in and per-capability permission.
- Do not add background monitoring or transmit audio/library metadata by default.
- Use the same authenticated connector/signing policy adopted by the Mora ecosystem only
  when that policy is finalized; never hardcode signing keys or tokens in source.
