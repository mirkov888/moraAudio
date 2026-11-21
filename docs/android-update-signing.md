# Android APK update signing

Android permits an in-place app update only when all of these remain true:

- application ID stays `app.mora.audio`;
- APK signing certificate stays the same;
- new `versionCode` is greater than the installed one.

## One-time setup

Create a dedicated release keystore on a trusted local machine:

```bash
keytool -genkeypair -v -keystore mora-audio-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias mora-audio
base64 -w0 mora-audio-release.jks
```

Store the keystore and passwords in a secure password manager and keep an offline backup.
Never commit the keystore or passwords.

In GitHub repository Settings → Secrets and variables → Actions, add:

- `MORA_KEYSTORE_BASE64`: the Base64 output (one line);
- `MORA_KEYSTORE_PASSWORD`: keystore password;
- `MORA_KEY_ALIAS`: `mora-audio` (or the alias chosen during generation);
- `MORA_KEY_PASSWORD`: private key password.

The workflow decodes the keystore into the ephemeral runner, signs a release APK, and
uses GitHub Actions' monotonically increasing workflow run number as `versionCode`.

## First install and future upgrades

The existing CI debug APK is signed with an ephemeral debug key, which cannot be
recovered from the APK and may differ between runs. If the first persistent-key release
cannot update the already-installed debug build, uninstall the old app once and install
the persistent-key APK. After that, install each new APK directly over the previous one.
Android app data is retained during normal in-place upgrades.

Before publishing an upgrade, verify the application ID, signing certificate, and
version code. Never lose the private keystore: a different signing key cannot update
the installed app.
