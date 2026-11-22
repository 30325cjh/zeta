# zeta

Personal Android offline music player for Android 8.0+.

## Features
- Analyze a public YouTube / YouTube Music playlist page with Gemini 3.5 Flash-Lite to display track names, artists, and available thumbnails.
- Batch-search Internet Archive for matching public-domain / permissively licensed recordings.
- Download available MP3 files only when the Archive search result advertises an allowed Creative Commons or public-domain license.
- Import multiple audio files already owned by the user.
- Local offline library with per-file deletion and clear-library controls.
- Foreground playback service with notification pause/resume/stop controls and wake lock for screen-off playback.

## Gemini API key
The app asks for the Gemini API key once and stores it in the app's private local preferences. A GitHub Actions secret is not available to an installed app. The key is deliberately not embedded into this public repository's APK, because anyone who downloads the APK could extract it.

Model: `gemini-3.5-flash-lite`.

## Important limitations
- The app does not extract, rip, or convert YouTube Music streams to MP3. A playlist URL is used for public track metadata only; the original YouTube audio is not downloaded.
- Batch download searches for separate recordings that are advertised under public-domain / Creative Commons terms. A match is not guaranteed for every track.
- Check each item's license and attribution requirements before reuse. Search metadata is not a substitute for reviewing the item page.

## Build
Push to `main` or run the Android APK workflow manually. Download the `ZETA-MUSIC-APK` artifact from Actions.
