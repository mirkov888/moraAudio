# Mora Audio Suite source archive

Place the supplied, unmodified source archive here:

`vendor/Mora_Audio_Suite_Unified_v3.6.0.zip`

Expected archive: **Mora Audio Suite Unified v3.6.0** (module id `mora_audio_suite`).

The module packaging workflow extracts and validates this archive, then produces a flashable ZIP artifact. It does not rewrite vendor binaries, audio effect libraries, presets, or installation scripts.

Why the archive is kept intact:
- It contains native libraries and vendor-specific assets that must not be reconstructed from text.
- The original module remains independently flashable through KernelSU.
- The Android app and the root module are separate deliverables; the app controls the module through its existing root bridge.

After adding the archive, run **Actions → Build Mora Audio Suite module → Run workflow**. The generated ZIP is available under the workflow's Artifacts section.
