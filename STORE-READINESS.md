# Store readiness — Ringtone Pick Your Sound

Not legal advice. This is an engineering checklist against store policies.

## Google Play (Android)
| Item | Status |
|---|---|
| Target SDK 35 (meets 2025–26 Play requirement), minSdk 29 | ✅ |
| Permissions: RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, POST_NOTIFICATIONS, WRITE_SETTINGS only | ✅ |
| No READ_PHONE_STATE / call log / call audio / SMS | ✅ (playback capture cannot hear calls by OS design) |
| Foreground service type `mediaProjection` declared in manifest and in `startForeground` | ✅ |
| **FGS declaration form** (Play Console › App content › Foreground service permissions): mediaProjection — "user-initiated capture of device audio to make a ringtone"; needs a short **video** of the flow | ⏳ you |
| WRITE_SETTINGS: requested via ACTION_MANAGE_WRITE_SETTINGS only when the user taps "Set as ringtone"; core function | ✅ (not a restricted Play permission) |
| Mic/projection only after user taps Record; persistent notification with Stop while capturing | ✅ |
| Data safety: audio recorded and stored **on device only**, not collected/shared; no account, no analytics, no network permission | ✅ draft: "No data collected" |
| Privacy policy URL (required because RECORD_AUDIO) | ⏳ you host one (e.g. GitHub Pages) |
| Content rating questionnaire, target audience 13+ | ⏳ you |
| Release signing (Play App Signing) + AAB (`./gradlew bundleRelease`) | ⏳ needs your upload key |
| Personal dev accounts: 12 testers × 14 days closed test | ⏳ you |
| IP policy: describe as "record sounds", don't advertise ripping copyrighted songs | ⚠️ listing wording |
| Store assets: 512px icon, feature graphic, screenshots | ⏳ (app uses default icon — add a real launcher icon) |

## Apple App Store (iOS, `/ios`)
iOS cannot capture other apps' audio or set ringtones programmatically, so the iOS build is **microphone-only** and exports a 30-second-max `.m4r` via the share sheet, with GarageBand/Finder instructions.

| Guideline | Status |
|---|---|
| 5.1.1 / NSMicrophoneUsageDescription with clear purpose | ✅ |
| 2.5.1 public APIs only (AVAudioRecorder, AVAssetExportSession, UIActivityViewController) | ✅ |
| 2.3 accurate metadata — don't claim it captures music/other apps or "sets" the ringtone | ⚠️ listing wording |
| **4.2 minimum functionality** — record/trim/export is thin; Apple often rejects simple ringtone makers | ⚠️ highest risk; add waveform, fades, library of saved clips |
| 4.1 / 4.3 copycat & spam — many similar apps | ⚠️ |
| 5.1.2 privacy nutrition label: "Data Not Collected" | ✅ draft |
| Privacy manifest (PrivacyInfo.xcprivacy) — no required-reason APIs used | ✅ none needed |
| Signing, App Store Connect record, app icon set, screenshots | ⏳ you (Apple Developer Program US$99/yr) |

## Global
- Localization: all UI strings are in `values/strings.xml` (Android) and `en.lproj/Localizable.strings` (iOS); add `values-xx/` and `xx.lproj/` folders to translate.
- Copyright: recording music for a ringtone is for personal use; distribution rights vary by country. In-app note included.
- Not handled yet: real launcher icons, release signing, crash-free testing on physical devices across Android 10–15 and iOS 16–18.
