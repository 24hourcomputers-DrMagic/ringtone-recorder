# Google Play store listing — Ringtone Pick Your Sound

Draft copy and assets for Play Console › Grow users › Store presence › Main store listing.
Nothing here has been uploaded to Play. Character counts were measured with Python `len()` (Play counts Unicode characters).

## App name (max 30 characters)
Google Play's app name limit is **30 characters** (it was cut from 50 to 30 in 2021). The earlier suggestion
"Ringtone Pick Your Sound — Custom Ringtones" is **43 characters**, so Play Console would reject it. Play policy also bans emoji, ALL CAPS
(unless it's the brand), and ranking or promo words like "best", "#1", "free" or "new" in the name.

| Option | Chars | Notes |
|---|---|---|
| Ringtone Pick Your Sound | 24 | **Recommended.** Matches the app name shown on the phone (`app_name`). |
| Pick Your Sound: Ringtones | 26 | Puts the brand first; the example suggested in the brief. |
| Pick Your Sound Ringtone Maker | 30 | Uses the full 30 characters with a descriptive keyword. |

## Short description (max 80 characters) — 78 characters
```
Record or upload any sound, trim it, and make it your ringtone. All on-device.
```

## Full description (max 4,000 characters) — 1775 characters
```
Make any sound your ringtone in under a minute.

Ringtone Pick Your Sound is a simple, friendly ringtone maker. Record a sound, or upload one you already have, trim it to the perfect moment, and set it as your phone's ringtone or as a special ringtone for one friend.

RECORD ANY SOUND
• Microphone: your kid's laugh, your dog's bark, your own voice, a doorbell, a song you're singing.
• Sound playing on your phone: capture audio from another app while it plays (with Android's screen-capture permission; only audio is recorded, never your screen).

UPLOAD YOUR OWN
• Tap "Upload your own sound" and pick an MP3, M4A, WAV, OGG or other audio file from Downloads, your file manager or cloud storage.
• Uses Android's own file picker, so the app never needs access to all your files.

TRIM AND PREVIEW
• Two simple sliders set the start and end, in tenths of a second.
• Tap Preview to hear exactly what your phone will play.

SET IT IN ONE TAP
• "Set as ringtone" saves the clip to your phone's Ringtones folder and makes it your default ringtone.
• "Choose a friend" gives one contact their own ringtone, so you know who's calling before you look. "Reset to default" undoes it.

PRIVATE BY DESIGN
• Everything stays on your phone. The app has no internet permission, no account, no analytics and no ad network.
• Contacts access is optional and only asked for when you choose a friend.
• A small, dismissible banner links to another free project from the same developer (Help and Win). Tapping it opens your web browser.

GOOD TO KNOW
• Phone calls and apps that block audio capture can't be recorded. That's an Android rule.
• Please only use sounds and music you have the right to use.
• Works on Android 10 and newer.

Questions or ideas? Email 24hourcomputers@gmail.com.
```

## Graphics (all in `store/`)
| Asset | File | Spec check |
|---|---|---|
| App icon | `store/icon-512.png` | 512×512, 32-bit PNG (RGBA), 20 KB (limit 1 MB). Full-bleed square: Play adds the rounded mask and shadow itself. Made from the app's own adaptive launcher icon (`ic_launcher_background` + `ic_launcher_foreground`). |
| Feature graphic | `store/feature-graphic-1024x500.png` | 1024×500, 24-bit PNG (RGB, no alpha). Header mascot (`ic_header_mascot`), the app's colors (`colors.xml`) and its cartoon icons. |
| Phone screenshots | `store/screenshots/01-record.png` … `05-set-ringtone.png` | 5 × 1080×1920 (9:16), 24-bit PNG. Upload these to "Phone screenshots" in this order. |
| Raw screenshots | `store/screenshots/raw/*.png` | The same 5 screens without captions, plus the system file picker (`02b`). Use these instead if you'd rather have plain screenshots. |

**The screenshots are real, not mockups.** They are captures of the signed v1.2 release build running on an Android 16 (API 36)
emulator (Pixel 6 image with the screen set to 1080×1920), with a caption band and phone frame added on top. The sound
files ("Ocean waves", "Doorbell chime") and the contact "Grandma Rose" are made-up test data. Each one shows:
1. **01-record**: picking a source (microphone) while recording.
2. **02-upload**: after "Upload your own sound" imported a 15-second file.
3. **03-trim-preview**: the trim sliders set to 2.5 s → 12.5 s, with Preview.
4. **04-contact-ringtone**: "Saved! Grandma Rose will hear this ringtone."
5. **05-set-ringtone**: after "Set as ringtone", with the "Ringtone set!" toast.

Optional extras Play accepts but doesn't require: 7-inch and 10-inch tablet screenshots (the layout already stretches) and a YouTube promo video.

## Category and tags
- **App category:** Music & Audio. (Personalization also fits; pick one.)
- **Tags** (Store settings › Manage tags, up to 5 from Play's fixed list): choose the closest matches to *Ringtones*, *Audio editor*,
  *Voice recorder*, *Sound effects* and *Customization*. Play only shows the tags it currently offers, so pick the nearest ones.
- **Contact details:** email `24hourcomputers@gmail.com` (required, shown publicly). Website optional.

## Privacy policy URL
`https://24hourcomputers-drmagic.github.io/ringtone-recorder/privacy.html`
This URL only works after GitHub Pages is turned on for this repo (Settings › Pages › Deploy from branch `main`, folder `/docs`).
**Pages is not enabled yet.** The source is `docs/privacy.html` (same text as `PRIVACY.md`). Any other public HTTPS host works too.

## App content answers (consistent with PRIVACY.md)
These are drafts for the owner to check. They are not legal advice.

### Data safety
| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Nothing leaves the device: no `INTERNET` permission, no SDKs. Audio and the optional contact ringtone are processed on the device only, which Play does not count as "collected". |
| Is all user data encrypted in transit? | Not applicable, since no data is collected | — |
| Do you provide a way for users to request deletion? | Not applicable, since no data is collected | The privacy policy explains clearing storage, uninstalling and deleting ringtones from the Files app. |
| Resulting label | **"No data collected · No data shared with third parties"** | |

### Ads
**Contains ads: Yes.** Play's ads definition includes promotions you serve yourself. The home screen shows a dismissible
first-party "Help and Win" banner that opens https://helpandwin.ca in the browser. There's no ad SDK or network.
(Answering Yes adds a "Contains ads" label on the listing. Removing the banner would let you answer No.)

### Content rating (IARC questionnaire)
- Category: **All Other App Types** (or "Utility, Productivity, Communication, or Other").
- Violence, sexuality, language, controlled substances, crude humor: **No**.
- Gambling or simulated gambling: **No**. The house banner links to an outside website with contest links, but the app itself has no gambling, wagering or prizes.
- Users can interact or exchange content: **No**. Shares the user's location: **No**. Digital purchases: **No**. Unrestricted internet or web browsing in the app: **No** (a link opens the system browser).
- Expected rating: **Everyone / PEGI 3 / ESRB E**.

### Other declarations
- **Target audience and content:** 13+ (13–15, 16–17 and 18+). Not designed for children, so it's not in the Families program.
- **App access:** all features are available without a login.
- **Permissions:** `RECORD_AUDIO` (core: record a sound); `FOREGROUND_SERVICE_MEDIA_PROJECTION` (core: capture phone audio, see below); `WRITE_SETTINGS` (core: set the default ringtone, user-granted special access); `READ_CONTACTS`/`WRITE_CONTACTS` (optional: set one contact's ringtone); `POST_NOTIFICATIONS` (capture notification).
- **Foreground service declaration** (App content › Foreground service permissions): type `mediaProjection`, "User-initiated capture of audio playing on the device, to make a ringtone. Runs only while the user records, with a persistent notification and a Stop button." **Needs a short video** of the flow (choose "Sound playing on the phone now" › Record › accept the system screen-capture prompt › notification shows › Stop). The owner still has to record it.
- **News app, government app, financial features, health:** No.
- **Copyright:** the listing says users should only use sounds they have rights to. Don't market it as a way to rip songs.

## Before you submit
- Play Console developer account (one-time US$25 fee, identity verification).
- New personal accounts must run a **closed test with at least 12 testers for 14 days in a row** before they can apply for production.
- Upload the signed **AAB** (`./gradlew bundleRelease`, see `SIGNING.md`) and turn on Play App Signing.
- Host the privacy page and paste the URL.
- Test on at least one real Android 10–16 phone (not done yet; only an emulator was used).
