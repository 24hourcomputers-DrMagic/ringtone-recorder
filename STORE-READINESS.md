# Store readiness — Ringtone Pick Your Sound

Not legal advice. This is an engineering checklist against store policies.

## Google Play (Android)
| Item | Status |
|---|---|
| compileSdk/targetSdk **36** (Android 16), minSdk 29, version 1.2 (code 3). Meets Play's targetSdk 36 requirement for new apps and updates (from 31 Aug 2026) | ✅ |
| Android 16 behavior changes reviewed (see section below) | ✅ builds + lint clean; ⏳ real-device test |
| Permissions: RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, POST_NOTIFICATIONS, WRITE_SETTINGS, plus optional READ_CONTACTS / WRITE_CONTACTS | ✅ |
| No READ_PHONE_STATE / call log / call audio / SMS | ✅ (playback capture cannot hear calls by OS design) |
| Foreground service type `mediaProjection` declared in manifest and in `startForeground` | ✅ |
| **FGS declaration form** (Play Console › App content › Foreground service permissions): mediaProjection — "user-initiated capture of device audio to make a ringtone"; needs a short **video** of the flow | ⏳ you |
| WRITE_SETTINGS: requested via ACTION_MANAGE_WRITE_SETTINGS only when the user taps "Set as ringtone"; core function | ✅ (not a restricted Play permission) |
| Mic/projection only after user taps Record; persistent notification with Stop while capturing. "Upload your own sound" uses the system picker (`ACTION_OPEN_DOCUMENT` / `audio/*`) and copies/decodes the file in-app — no storage permission | ✅ |
| Data safety: audio is recorded or imported and stored **on device only**, not collected/shared; no account, no analytics, no network permission. Optional contacts access is used only to write/reset that contact's `CUSTOM_RINGTONE` on the device — names and numbers are not collected, stored in the app, or shared | ✅ draft: audio and (optional) contacts used on-device only; no data collected off the device |
| Privacy policy URL (required because RECORD_AUDIO **and** optional contacts permissions) | ⏳ you host one (e.g. GitHub Pages) — must mention microphone, optional contacts, and that nothing is uploaded |
| Content rating questionnaire, target audience 13+ | ⏳ you |
| Release signing (Play App Signing) + AAB (`./gradlew bundleRelease`) | ✅ release `signingConfig` reads `keystore.properties` or `RELEASE_*` env vars (unsigned fallback); see `SIGNING.md`. An upload key exists outside the repo; ⏳ you back it up and enroll in Play App Signing |
| Personal dev accounts: 12 testers × 14 days closed test | ⏳ you |
| IP policy: describe as "record sounds", don't advertise ripping copyrighted songs | ⚠️ listing wording |
| Store assets: 512px icon, feature graphic, screenshots | ⏳ app now has a cartoon adaptive launcher icon; Play listing still needs a 512px icon, feature graphic, and device screenshots |

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
- Not handled yet: crash-free testing on physical devices across Android 10–16 and iOS 16–18.



## Optional contacts (Android)
The "Add contacts if you want specific sounds for specific friends" action is optional.
- `READ_CONTACTS` and `WRITE_CONTACTS` are declared in the manifest but **never requested at install or launch**.
- They are requested at runtime only after the user taps "Choose a friend", and only after a short in-app rationale.
- On grant, the contact picker (`ACTION_PICK` / `PickContact`) opens. The app writes `ContactsContract.Contacts.CUSTOM_RINGTONE` to the ringtone just saved in MediaStore, or clears it on "Reset to default".
- Contact names and numbers are not stored in the app and are not uploaded (the app has no `INTERNET` permission).
- Data safety: declare contacts as optional, collected? No (not sent off the device). Used to "Manage contacts" / set a ringtone only. Privacy policy must describe this.

## House ad (Help and Win)
A dismissible first-party banner on the home screen (hidden during record/preview) links to https://helpandwin.ca. No ad SDK, no tracking, no INTERNET permission — the tap opens the system browser. The app stays free.
Play Console "Contains ads": the form's wording includes ads you serve yourself, so declare **Yes** to be strictly correct. There is no ad network.


## Android 16 / targetSdk 36 review (v1.2)
Build: AGP 8.13.0, Gradle 8.13, compileSdk/targetSdk 36. `./gradlew assembleDebug lintDebug` passes locally.
| Change for apps targeting 36 | What this app does |
|---|---|
| Edge-to-edge opt-out (`windowOptOutEdgeToEdgeEnforcement`) removed | Never used it. `MainActivity` now calls `enableEdgeToEdge()` (transparent bars, dark icons on the light UI) and pads the scroll view by system-bar + display-cutout insets. Removed the no-op `statusBarColor` / `navigationBarColor` theme items. |
| Predictive back on by default; `onBackPressed` / `KEYCODE_BACK` no longer delivered | The app never intercepts back (no `onBackPressed`, no key handling), so system back simply closes the single activity. `android:enableOnBackInvokedCallback="true"` is now explicit in the manifest so Android 13–15 also get the predictive animation. |
| Adaptive layouts: orientation / resizability / aspect-ratio locks ignored on large screens | No locks declared; the layout is a single scrolling column, so it already resizes. |
| `elegantTextHeight` ignored | Not used. |
| Safer Intents (explicit intents must match filters) | Only explicit intents to its own non-exported `CaptureService`; system intents (`ACTION_OPEN_DOCUMENT`, `PickContact`, `ACTION_MANAGE_WRITE_SETTINGS`, `ACTION_VIEW` for the house ad) are implicit system actions. |
| MediaProjection / FGS / audio capture | No new API-36 rules for `mediaProjection` FGS. The Android 14+ rules still hold and are met: FGS type `mediaProjection` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `startForeground` before `getMediaProjection`, fresh user consent each session, `MediaProjection.Callback` registered. The deprecated `getParcelableExtra(String)` was replaced with `IntentCompat.getParcelableExtra`. |
| Permissions | No API-36 permission changes affect this app (the new ones cover health data and local-network access; the app has no network permission). Media read permissions are not needed: upload uses the system document picker. |
| JobScheduler quota changes | App uses no jobs/WorkManager. |
| 16 KB page size (Play, Nov 2025) | Pure Kotlin, no native `.so` libraries, so nothing to realign. |

Device testing: not done on a physical phone. Smoke-tested only on a headless Android 16 (API 36) emulator — see the release notes / owner report.
