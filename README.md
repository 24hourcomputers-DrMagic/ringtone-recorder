# Ringtone Pick Your Sound

*Pick your sound* — a simple Android app (Kotlin, Views) that records a sound and sets it as your ringtone.

## Features
Pick a source:
1. **Sound playing on the phone now** — MediaProjection + `AudioPlaybackCaptureConfiguration` (usages MEDIA, GAME, UNKNOWN), in a foreground service of type `mediaProjection`.
2. **Microphone** — `AudioRecord` from the mic while the app is open.
3. **A segment of music you're listening to** — same playback capture as (1); start/stop around the part you want.

4. **Upload your own sound** — system file picker (`ACTION_OPEN_DOCUMENT`, `audio/*`), no storage permission. MP3, M4A, WAV, and OGG (and other formats the device can decode) are converted to WAV, then enter the same trim flow.

Then: trim with start/end sliders → **Preview** → **Set as ringtone** (saved to `Ringtones/` in MediaStore with `IS_RINGTONE=true`, set via `RingtoneManager.setActualDefaultRingtoneUri` after you grant "Modify system settings").

Optional: **Add contacts if you want specific sounds for specific friends** — contacts permissions are requested only if you tap this, then a contact picker can assign (or reset) that person's custom ringtone.

Recordings and imports are 44.1 kHz 16-bit stereo WAV in app storage.

## iOS version (`/ios`)
SwiftUI, iOS 16+. Apple doesn't allow capturing other apps' audio or setting ringtones from an app, so iOS records from the **microphone only**, lets you trim/preview, and exports a `.m4r` (≤30 s) via the share sheet. Set it with GarageBand (Share › Ringtone) or Finder sync, then Settings › Sounds & Haptics.
Build: `brew install xcodegen && cd ios && xcodegen generate && open PickYourSound.xcodeproj`. CI builds an unsigned simulator app.

## Store readiness
See [STORE-READINESS.md](STORE-READINESS.md).

## Localization
Strings live in `app/src/main/res/values/strings.xml` and `ios/PickYourSound/en.lproj/Localizable.strings`.

## Permissions
`RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `POST_NOTIFICATIONS`, `WRITE_SETTINGS`.
Optional, requested only when you tap "Choose a friend": `READ_CONTACTS`, `WRITE_CONTACTS`.
**No** phone-state, call-log or any call-related permissions. Upload uses the system document picker and does not need a storage permission.

## Build
- Requirements: JDK 17, Android SDK 35.
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Or download the `ringtone-pick-your-sound-debug-apk` artifact from the latest GitHub Actions run.

## Limitations
- Android 10+ (minSdk 29). Playback capture needs you to accept the "start recording/casting" prompt each time.
- Apps that opt out of capture (many streaming apps, all calls, system sounds) record as silence.
- Microphone recording stops if you leave the app.
- **Copyright:** music segments are for personal use only; only use audio you have the rights to.

## Google Play prep
- `SIGNING.md`: upload key and release signing (`./gradlew bundleRelease`)
- `PRIVACY.md` / `docs/privacy.html`: privacy policy (host it, e.g. GitHub Pages from `/docs`)
- `STORE_LISTING.md` + `store/`: listing copy, icon, feature graphic, screenshots, data-safety and rating answers
- `STORE-READINESS.md`: checklist, including the Android 16 / targetSdk 36 review
