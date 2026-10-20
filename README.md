# Women Safety (Android)

A personal safety app for Android. One tap-and-hold (or holding **Volume Up for 6 seconds**) starts an SOS that alerts family with live location, **live camera and microphone**, and saved evidence. Family members join her circle by scanning a **QR code**. The app also has a **daily news** feed and a **crime map** of Indian cities based on official NCRB data.

Everything runs on free services — there is no paid server.

> **Project status:** feature-complete in code; builds and installs cleanly (verified on an Android 16 emulator). Still needs Firebase + Maps setup and real-phone testing — see **[PENDING.md](PENDING.md)** for the exact list of what's left and how to resume.

---

## Features

### 1. SOS with live camera, microphone and location
- **Big SOS button** on Home: press and hold for 2 seconds (prevents pocket-taps). Hold again to stop.
- **Volume-button SOS:** hold **Volume Up for 6 seconds** from anywhere — inside other apps or on the lock screen. Light vibration ticks after 2 s; three strong buzzes mean SOS has started. Uses an accessibility service (the "special permission"), which only listens to the Volume Up key. The volume button keeps working normally.
- During SOS the phone:
  - shares **precise location every ~5 s** with her circle,
  - rings a **loud alarm on every guardian's phone** (even on silent/DND where allowed, over the lock screen),
  - streams **live video + audio** wirelessly to guardians (WebRTC, phone-to-phone over Wi-Fi or mobile data). Their phone rings and opens the live view straight away,
  - lets guardians **switch between her front and back camera** (Front | Back switch on their screen shows which one is live),
  - **records the live stream as MP4 video with sound** while it's live, in 30-second files, so nothing is lost if the phone is switched off or taken. Recorded on **her phone** *and* on **each guardian's phone** that is watching,
  - saves a **photo every 4 s** and **audio in 15-second clips** — on the phone *and* in the cloud (videos are too big for the free cloud tier, so they stay on the phones),
  - texts **SMS contacts** with a Google Maps link (works without internet), then every 3 minutes.
- If the phone is switched off mid-SOS, SOS resumes when it turns back on.

### 2. Circle (QR linking)
- **Her phone:** Circle → *Add someone with QR*. Shows a one-time code (valid 10 minutes; deleted when she leaves the screen).
- **Family phone:** Circle → *Scan QR* (or type the 10-letter code). They confirm, and join.
- **Location is always shared** with the circle (pausable). **Camera and microphone are only available while her SOS is on** — enforced by the app, and explained on the guardian's screen.
- She gets a notification whenever someone joins, and can remove anyone instantly. Sharing always shows an ongoing notification naming who can see her.

### 3. News and crime map
- **News tab:** Top stories, Women's safety, *Near you* (her city) and Crime alerts — from Google News RSS (India edition). Pull to refresh.
- **Evidence vault:** every SOS (and every live stream a guardian watched) appears as a session with its videos, photos and audio — play, share or delete.
- **Crime map tab:** Google Map with a coloured dot per city for **crimes against women**, coloured relative to the average (Low / Average / High / Very high). Tap a city for the figure, its source, and the **latest local safety news** for that city.

### 4. Redesigned UI
Material 3 with a custom design: warm "paper" background with a fine grain texture, deep plum + coral palette, Fraunces (display) and Manrope (UI) fonts, floating bottom bar, a pulsing SOS button with a hold-progress ring, full dark mode, and edge-to-edge layouts.
- **New app icon:** shield with an SOS beacon on a plum-to-coral gradient; adapts to every launcher shape and to Android 13+ themed icons.
- **Icons:** Google's Material Symbols (Rounded) throughout; the bottom bar shows filled icons for the selected tab; gradient badges behind feature icons.
- **Motion:** fade-through when switching tabs, slide transitions between screens, a gentle squeeze when cards and buttons are pressed, content easing in on screen open, Android 14+ predictive back, and a branded launch screen. All motion respects the system *Remove animations* setting.

---

## What it costs to run: nothing

| Need | Service | Cost |
|---|---|---|
| Sign-in, live location, SOS alarms, video signalling, evidence copies | Firebase **Spark** plan: Anonymous Auth + Realtime Database | Free (1 GB stored, 10 GB/month downloaded) |
| Live video/audio | WebRTC, phone-to-phone, Google's public STUN servers | Free |
| SOS alarm on family phones | The family app keeps a database connection open (no paid push server) | Free |
| News | Google News RSS | Free, no key |
| Map | Google Maps SDK for Android | Google requires a billing account to create a key; see step 3 |
| Crime figures | NCRB data bundled in the app | Free |
| SMS fallback | The phone's own SMS plan | Your normal SMS rate |

Firebase **Storage** is deliberately *not* used: since late 2024 it needs the paid Blaze plan. Evidence is stored as small base64 items in Realtime Database instead.

---

## Setup

### 1. Open the project
Android Studio (latest stable) → *Open* this folder. Let Gradle sync. It builds and runs **before** Firebase is set up — live sharing simply shows "needs setup" until then.

### 2. Set up Firebase (free, ~10 minutes)
1. Go to https://console.firebase.google.com → **Add project** (keep the free Spark plan; Analytics optional).
2. **Build → Authentication → Get started → Sign-in method → Anonymous → Enable.**
3. **Build → Realtime Database → Create database.** Pick a location (e.g. *Singapore, asia-southeast1*, closest to India). Start in **locked mode**.
4. In Realtime Database → **Rules**, replace everything with the contents of [`database.rules.json`](database.rules.json) and click **Publish**. (These rules are what stop strangers from seeing anyone's data.)
5. **Project settings → Your apps → Android:** package name `com.example.womensafety`. Download **`google-services.json`** and put it in the **`app/`** folder.
   *Download it after step 3 so it contains the database URL.*
6. Rebuild. The Circle tab now works.

`app/google-services.json` is in `.gitignore`.

### 3. Google Maps key
1. https://console.cloud.google.com → select (or create) a project → **APIs & Services → Library → Maps SDK for Android → Enable.**
2. **Credentials → Create credentials → API key.** Restrict it to *Android apps* with package `com.example.womensafety` and your SHA-1 (Android Studio: *Gradle → signingReport*).
3. Add to **`local.properties`** (project root, never committed):
   ```
   MAPS_API_KEY=AIza...your key...
   ```
Google asks for a billing account to issue the key. Check Google's current pricing page for the Maps SDK for Android, and set a **budget alert** in Cloud Billing so you are told before any charge. Without a key the app still works; the map screens just show a hint.

### 4. Optional: TURN relay for live video on strict networks
Phone-to-phone video works on most Wi-Fi and many mobile networks with the free STUN servers. Some mobile networks block it; then guardians see the evidence photos (every 4 s) instead of live video. To fix that, add a free TURN relay (for example Metered's "Open Relay" free tier) in `local.properties`:
```
TURN_URL=turn:your.relay.host:80,turn:your.relay.host:443?transport=tcp
TURN_USERNAME=...
TURN_PASSWORD=...
```

### 5. Run on two phones
Install on her phone and on a family member's phone. On both: complete the short onboarding and **Safety setup** checklist.

---

## Using it

**On her phone**
1. Safety setup → allow location (*Allow all the time*), notifications, camera + mic, SMS, **Volume-button SOS**, and background running.
   - *Volume-button SOS:* Settings → Accessibility → Installed/Downloaded apps → Women Safety → On. If the switch is greyed out ("Restricted setting", common for apps installed outside the Play Store): App info → ⋮ → **Allow restricted settings**, then try again.
2. Circle → *Add someone with QR* → show it to family.
3. Optional: Profile → add **SMS contacts** (they don't need the app).

**On a family member's phone**
1. Circle → *Scan QR*. Confirm.
2. Allow notifications and (Android 14+) **full-screen alarms** in Safety setup, so an SOS can wake the screen.
3. Tap her name any time to see her live location. During SOS the same screen shows her **live camera and microphone** (switch camera, mute, call, directions).

---

## Test checklist
- [ ] Hold the SOS button 2 s → SOS card appears with a timer; family phone rings; live view shows video within a few seconds.
- [ ] Lock her phone, hold Volume Up 6 s → ticks, three buzzes, SOS starts.
- [ ] Turn off Wi-Fi/data on her phone during SOS → SMS still arrives; evidence keeps saving locally.
- [ ] Profile → Evidence vault shows photos and playable audio for the session.
- [ ] Remove the guardian on her phone → their live view closes with "no longer in circle".
- [ ] News tabs load; Crime map shows coloured dots and local news for a tapped city.

Unit tests (`app/src/test`): hold-timer logic, phone number validation, contact storage, invite-code parsing, crime-level thresholds.

---

## Known limits (honest list)
- **Screen fully off:** some phone makers stop sending the volume key to apps when the display is off. Pressing power first (screen on, still locked) always works.
- **Live video** needs both phones online; on networks that block peer-to-peer, add TURN (step 4). Evidence photos are always available as a fallback.
- **Guardian alarms** rely on the family app staying alive in the background. Ask family to allow "Run in background" in Safety setup (some brands like Xiaomi/Oppo/Vivo also need *Autostart* enabled in their own settings).
- **Crime data:** the app bundles official NCRB figures that could be verified: 34 cities from *Crime in India 2023* (Table 3B.1) and several large metros from *Crime in India 2022/2023*. Some big metros (e.g. Bengaluru, Mumbai, Hyderabad, Pune) show case counts or "no figure" until their rates are added. To update, edit [`app/src/main/assets/crime_stats.json`](app/src/main/assets/crime_stats.json) with values from the latest report at ncrb.gov.in. Reported crime reflects reporting rates too — the app says so on screen.
- **Google News RSS** is meant for personal, non-commercial use. For a commercial release, switch to a licensed news API.
- **Firebase free limits** are generous for a family app. Each SOS minute uploads roughly 1 MB of evidence.
- Anonymous sign-in is per install: if someone reinstalls the app, they need to scan the QR again.

---

## Project structure
```
app/src/main/java/com/example/womensafety/
├── App.java                     notification channels, Firebase init
├── core/                        Prefs, Backend (Firebase paths), Permissions, Notifications, Ui helpers
├── sos/                         SosService, VolumeSosService (volume key), HoldTimer, SosLaunchActivity, SMS
├── live/                        WebRTC: Broadcaster (her phone), Viewer (family phone), evidence capture
├── circle/                      QR invites (InviteActivity, QrRenderer), CircleRepository, CircleFragment
├── guardian/                    GuardianWatchService (SOS alarm), LiveViewActivity
├── location/                    LocationShareService (always-on sharing), BootReceiver
├── news/                        Google News RSS feed
├── map/                         Crime map, NCRB data loader
├── evidence/                    EvidenceStore, EvidenceActivity
└── ui/                          MainActivity (tabs), Home, Onboarding, Setup, Profile, SosButton view
database.rules.json              Realtime Database security rules (paste into Firebase console)
```

## What changed from v5
- Rebuilt every screen with XML layouts + ViewBinding instead of building views in Java code; one design system (colors, type, shapes) shared by all screens; dark mode.
- **Removed the email/password login**, which stored passwords in plain text on the phone. Onboarding now asks only for a name and number; identity for sharing comes from Firebase Anonymous Auth.
- Volume SOS no longer **blocks the volume button** and no longer holds a permanent wake lock (battery drain). Fixed the in-app volume detector that never fired (it checked a setting that was never saved).
- SOS: camera/mic now start correctly from the lock screen (Android 14 rules), SMS repeat reduced from every 10 s to every 3 min, evidence is uploaded, and SOS resumes after a reboot.
- Removed unreachable code (Deepfake and Crime Risk screens that weren't registered, duplicate camera helpers, unused receivers) and the crime "hotspots" that were made-up sample data — replaced by real NCRB figures.
- Added unit tests.

Fonts: Fraunces and Manrope, SIL Open Font License. Icons: Material Symbols, Apache 2.0 (see `licenses/`).
