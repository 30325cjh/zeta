# Zeta APK Restorer

Android APK file analyzer and project recovery exporter.

## Features
- Pick an APK using Android's system file picker
- Safely extract APK ZIP entries with path traversal and expanded-size checks
- Preserve DEX bytecode, resources, assets, native libraries, manifest and the original APK
- Export a ZIP containing the extracted files, a recovery report and a Gradle project skeleton
- Build a debug APK with GitHub Actions

## Important limitation
An APK is a compiled package, not the original source project. This tool preserves the DEX bytecode and packaged resources; it does **not** reconstruct original Kotlin/Java source, comments, original Gradle configuration, or a guaranteed buildable clone. Binary AndroidManifest.xml and compiled resources are preserved as-is. A separate DEX decompiler / Android resource decoder would be required for readable reconstructed code.

## Build
GitHub Actions builds `app-debug.apk` on pushes to `main` and on manual workflow dispatch.
