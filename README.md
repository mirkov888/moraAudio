# Mora Audio (app)

Small root app that controls the **Mora Audio Suite** module (v3.3+): listening mode, BassBoost (bass, dynamics, loudness) and the main tuning toggles. It runs the same module scripts as the module's WebUI, so both always show the same state.

- Needs the Mora Audio Suite module installed and root granted to the app (KernelSU / Magisk / APatch).
- No dependencies, no network permission, no data collection.

## Build (Android Studio)
Open this folder, let Gradle sync (AGP 8.5.2, compileSdk 34), then Run, or `gradle assembleDebug`.

## Build without a PC (GitHub Actions)
1. Create a repository on GitHub and upload this whole folder (including `.github/workflows/build.yml`).
2. Open the Actions tab, wait for "Build APK" to finish, download `MoraAudio-debug-apk`, install `app-debug.apk`.

Status: the source was type-checked against API stubs only. It has not been compiled with the Android SDK or run on a device. If the build reports an error, send the log and it will be fixed.

Made by Mora & Claude Code.


## Build di GitHub (v2.0)
1. Buat repo baru di GitHub, unggah seluruh isi folder ini (termasuk `.github/workflows/build.yml`).
2. Buka tab **Actions** → workflow **Build APK** berjalan otomatis (atau tekan *Run workflow*).
3. Setelah hijau, unduh **MoraAudio-apk** dari bagian *Artifacts*.
4. Opsional: beri tag `v2.0` (`git tag v2.0 && git push --tags`) agar APK juga muncul di *Releases*.

Catatan jujur: kode belum pernah dikompilasi dengan Android SDK di lingkungan pembuatnya. Jika build gagal, salin log error dari Actions dan kirim agar diperbaiki.

## Baru di v2.0
Navigasi bawah (Beranda/Suara/Tuning/Sistem), kartu status driver dengan penyebab dan tombol **Perbaiki driver**, kartu mode, ikon aplikasi, tampilan log.
