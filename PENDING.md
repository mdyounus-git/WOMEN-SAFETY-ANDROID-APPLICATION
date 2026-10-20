# Milestone: v6.0 — what's done and what's left

Last updated: 20 Oct 2026. Branches: `main` and `claude/safety-v6` (same code).
This file is the single source of truth for picking the work back up. Tick items off as they're finished.

---

## ✅ Done (in the code on this branch)

- [x] **SOS trigger** — hold the SOS button 2 s, or **hold Volume Up 6 s** from anywhere (accessibility service; works after the app is closed or cleared from memory; Android restarts it if killed).
- [x] **Live location** shared with the circle all the time (pausable); every ~5 s during SOS.
- [x] **QR linking** — one-time 10-minute codes; family scan to join; she's notified of every join and can remove anyone.
- [x] **Live camera + microphone to family during SOS only** (WebRTC, phone-to-phone, free STUN servers). Guardian phones ring a full-screen alarm.
- [x] **Front / Back camera switch** on the guardian's live view, showing which camera is live.
- [x] **Recording while live** — MP4 (H.264 + AAC) in 30-second files on her phone *and* on each watching guardian's phone. Plus photo every 4 s and 15-s audio clips uploaded to the cloud.
- [x] **SMS fallback** to emergency contacts (no internet needed).
- [x] **Evidence vault** — videos, photos, audio per session; streams received from others are labelled.
- [x] **News tab** (Google News RSS) and **crime map** (Google Maps + NCRB figures, local news per city).
- [x] **New UI** — Material 3, custom fonts, paper texture, dark mode.
- [x] **Code quality** — removed plain-text password login, dead screens and fake crime data; crash-proofing of foreground services and maps; unit tests (13 passing).
- [x] **Build verified** — real Gradle build in Android Studio, installed and launched on a Pixel 5 emulator (Android 16) with no errors.

---

## ⏳ Pending — needs the owner (accounts / cards Claude can't create)

- [ ] **1. Firebase project (free Spark plan)** — without it, QR linking, live location, live camera/mic and SOS alarms to family **do not work**.
  Steps: README → *Set up Firebase*. Ends with `app/google-services.json` in place and the rules from `database.rules.json` published.
- [ ] **2. Google Maps API key** — without it the Crime map tab shows a message instead of a map.
  Steps: README → *Google Maps key*. Put `MAPS_API_KEY=...` in `local.properties`. Google requires a billing account to issue the key.
- [ ] **3. Give Claude push access to this repo (optional)** — install the Claude GitHub App on this repo, or re-link GitHub in Claude settings. Until then, pushes are done from Android Studio on the owner's PC.

## ⏳ Pending — testing on real phones (not possible on the emulator used so far)

The emulator mirror in Android Studio didn't accept taps, so these were **not** run end to end. Use two real phones.

- [ ] **4. Volume Up trigger, app closed:** finish Safety setup (incl. *Volume-button SOS*) → swipe app away → lock phone → press power once → hold Volume Up 6 s → expect 3 buzzes, "SOS is on" notification, SMS sent.
- [ ] **5. Live stream:** SOS on phone A → phone B (guardian) rings → open live view → video + sound within a few seconds.
- [ ] **6. Front / Back switch:** tap Front, then Back on phone B → phone A's camera changes; highlight follows.
- [ ] **7. Recording:** after 1–2 minutes of SOS, stop → Evidence vault on **both** phones shows playable MP4 files (her phone: "Your SOS"; guardian: "Live stream from …").
- [ ] **8. Test on several brands** — at least one Samsung and one Xiaomi/Oppo/Vivo (aggressive battery savers; enable *Autostart* there).
- [ ] **9. Mobile data:** repeat step 5 with both phones on 4G/5G. If video never connects, add a free TURN relay (README → *TURN relay*).

## ⏳ Pending — remaining development work

- [ ] **10. Screenshots for the README** — capture Home, SOS active, Circle/QR, guardian live view, News, Crime map, Evidence vault on a real phone and add them to `docs/screenshots/` (and the README table below).
- [ ] **11. New launcher icon** — the app still uses the old v5 icon; replace with the shield + beacon logo (`res/drawable/ws_logo.xml`) via Android Studio → *New → Image Asset*.
- [ ] **12. Release build + APK download** — create a signing key (*Build → Generate Signed App Bundle / APK*), build a release APK, and attach it to a **GitHub Release** so people can install without Android Studio.
- [ ] **13. Crime data gaps** — Bengaluru, Mumbai, Hyderabad, Pune and others show case counts or "no figure". Fill rates from the latest NCRB *Crime in India* report in `app/src/main/assets/crime_stats.json`.
- [x] **14. Guardian mute no longer silences the guardian's recording** — the recorder now takes the received audio through an `AudioTrackSink`, before speaker volume is applied.
- [ ] **15. Merge `claude/safety-v6` into `main`** once items 1–9 pass.

## 💡 Ideas (not started)

- Screen-off volume trigger using a media-session volume handler (works with the display off on more phones; needs careful battery testing).
- Upload video segments to a free storage provider (e.g. Google Drive of the user) so they also survive phone loss.
- Hindi / regional language translations of all strings.

---

## Known platform limits (can't be fixed in app code)

- Android doesn't let apps use the **power button**; the trigger is Volume Up.
- With the **screen fully off**, most phones don't pass volume keys to any app — press power once first (phone can stay locked).
- **Force stop** in Settings turns the volume trigger off until the app is opened again.
- APKs shared via WhatsApp / file managers may need **App info → ⋮ → Allow restricted settings** before the volume trigger can be switched on.

## How to resume

1. Clone and check out `claude/safety-v6`; open in Android Studio; let Gradle sync.
2. Press ▶ Run to build and install (it builds and runs without Firebase/Maps).
3. Pick the next unchecked item above. Commit with a message naming the item number, and tick it here.
