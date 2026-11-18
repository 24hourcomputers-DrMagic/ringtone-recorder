# Store readiness — Ringtone Pick Your Sound

Not legal advice. This is an engineering checklist against store policies.

## Google Play (Android)
| Item | Status |
|---|---|
| Target SDK 35, minSdk 29. **Play (as of 31 Aug 2026) requires targetSdk 36 for new apps and updates** | ⚠️ bump compileSdk/targetSdk to 36 before a Play upload |
| Permissions: RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, POST_NOTIFICATIONS, WRITE_SETTINGS, plus optional READ_CONTACTS / WRITE_CONTACTS | ✅ |
| No READ_PHONE_STATE / call log / call audio / SMS | ✅ (playback capture cannot hear calls by OS design) |
| Foreground service type `mediaProjection` declared in manifest and in `startForeground` | ✅ |
| **FGS declaration form** (Play Console › App content › Foreground service permissions): mediaProjection — "user-initiated capture of device audio to make a ringtone"; needs a short **video** of the flow | ⏳ you |
| WRITE_SETTINGS: requested via ACTION_MANAGE_WRITE_SETTINGS only when the user taps "Set as ringtone"; core function | ✅ (not a restricted Play permission) |
| Mic/projection only after user taps Record; persistent notification with Stop while capturing. "Upload your own sound" uses the system picker (`ACTION_OPEN_DOCUMENT` / `audio/*`) and copies/decodes the file in-app — no storage permission | ✅ |
| Data safety: audio is recorded or imported and stored **on device only**, not collected/shared; no account, no analytics, no network permission. Optional contacts access is used only to write/reset that contact's `CUSTOM_RINGTONE` on the device — names and numbers are not collected, stored in the app, or shared | ✅ draft: audio and (optional) contacts used on-device only; no data collected off the device |
| Privacy policy URL (required because RECORD_AUDIO **and** optional contacts permissions) | ⏳ you host one (e.g. GitHub Pages) — must mention microphone, optional contacts, and that nothing is uploaded |
| Content rating questionnaire, target audience 13+ | ⏳ you |
| Release signing (Play App Signing) + AAB (`./gradlew bundleRelease`) | ⏳ needs your upload key |
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
- Not handled yet: release signing, Play listing graphics, crash-free testing on physical devices across Android 10–16 and iOS 16–18.



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
