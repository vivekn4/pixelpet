# Pixel Pets v3 — Real-Device Test (S26 Ultra)

Status: **PENDING USER** — nothing below has been run or verified yet.
Do not claim any result in this file until it has actually been observed
on the device. Fill in the Result column as you go.

## Prerequisites

- [ ] CI green on `main` (Android CI workflow). **Current blocker (2026-10-13):**
      CI fails at the "Set up Android SDK" step because
      `android-actions/setup-android@v3` installs the legacy `tools`
      package, which Google removed from the SDK repository. Fix: change
      the workflow (via the GitHub web UI — the API cannot edit workflow
      files) to `android-actions/setup-android@v4`, then re-run.
      Until CI is green, build locally with `./gradlew assembleDebug`.
- [ ] Debug APK: from the CI `pixelpet-debug-apk` artifact, or a local build.
- [ ] S26 Ultra with USB debugging (or wireless debugging) enabled.
- [ ] `adb` on the workstation (`adb devices` shows the phone).

## 1. Install

- [ ] `adb install -r app-debug.apk` succeeds.
- [ ] Android 13+ gate: if prompted, grant
      **Settings → Apps → PixelPet → ⋮ → Allow restricted settings**.
- [ ] App launches, no crash on first launch, Pip (default cat) visible.
- [ ] First-launch widget pin prompt appears once.

Result: _pending_

## 2. Wallpaper roam (lock screen)

- [ ] Set PixelPet as the lock-screen wallpaper (Samsung: separate
      lock-screen wallpaper setting).
- [ ] Pip roams the lock screen; motion is smooth, no stutter or freeze.
- [ ] Roam pauses after ~30 s with no interaction; resumes on touch.
- [ ] Tap = pet (hearts), double-tap = play (giggle).
- [ ] Screen off → Pip sleeps; wake shows a "While you were away…" recap.
- [ ] No visible battery drain anomaly after 1 hour (check Settings → Battery).

Result: _pending_

## 3. Widget walk frame (home screen)

- [ ] Add the PixelPet widget; face bitmap renders Pip + 4 stat bars + name.
- [ ] Face changes walk frame after a stat tick / tap (snapshot bitmap —
      it does NOT animate continuously; that is by design, RemoteViews
      cannot host a live view).
- [ ] Quick-feed button works; snack cooldown toast appears when cooling.
- [ ] Face tap opens the app.
- [ ] Widget refreshes within ~30 min without a reboot (job scheduled
      from app launch as well as boot).

Result: _pending_

## 4. Aura skins (premium)

- [ ] With a valid premium key activated, Premium screen shows the aura
      picker (Gold Orbit / Rainbow / Ember / Starlight / No Aura).
- [ ] Selecting an aura changes the glow around Pip in-app, in the widget,
      and in the wallpaper.
- [ ] "No Aura" removes the glow; non-premium users see no aura at all.
- [ ] Aura changes LOOK only: stats, cooldowns, unlocks unchanged.

Result: _pending_

## 5. Powers (spot-check, one per family group)

- [ ] Power button visible; disabled state + countdown text while cooling.
- [ ] Trigger a power (e.g. Cat Purr Heal): effect plays (particles /
      overlay), cooldown starts (1/day default).
- [ ] Hamster Cheek Stash / Panda Bamboo Snack: 6 h cooldown.
- [ ] Phoenix Rebirth: restores exactly ONE missed streak day; does NOT
      consume the weekly cooldown when there is no missed day.
- [ ] Turtle Shell Shield: stats frozen while shielded, then decay resumes.
- [ ] Owl Night Watch: shows tomorrow's first goal as a reminder.
- [ ] Dog Fetch Gift: toast + confetti (no coins — the app has no economy).

Result: _pending_

## 6. Personality card

- [ ] Open the Personality card (🧠 button): 5 trait bars + summary line
      render; values in 10–90.
- [ ] "Pip learned" section shows honest "still getting to know you"
      copy until ~3 days of interaction data exist.
- [ ] Pet/play/feed a few times; trait bars nudge in the expected
      direction (play → Playful up, doomscroll → Bold/Social down).

Result: _pending_

## 7. Brain learning (multi-day — cannot fully verify in one session)

- [ ] Use the app at consistent times for 3+ days; Brain records
      schedule buckets and favorites.
- [ ] After ~3 days, the Personality card's learned section shows real
      learned lines (favorite action / usual hour), not placeholders.
- [ ] After a long gap, greeting is shy, then warms with care.

Result: _pending (multi-day)_

## 8. Frame-time measurement (wallpaper draw path)

The wallpaper loop is ~30 fps, so the per-frame budget is **33 ms**
(16 ms is the 60 fps budget; it does not apply here). Static analysis
says the steady path is bg fill + 70 star rects + cached-bitmap blit +
shadow — well inside budget — but that is NOT a measurement.

Measure on device:
```bash
adb shell dumpsys gfxinfo com.vn.pixelpet framestats
# or record a systrace / Perfetto trace while the wallpaper is visible
```
- [ ] p95 frame time < 33 ms over a 60 s visible-roam window.
- [ ] No dropped-frame spikes when the bitmap re-renders (render-key change).

Result: _pending (p95: ___ ms)_

## 9. Migration (v2 → v3)

Static migration checks pass (`python3 qa_migration.py`). On-device
migration over a real v2 install is **not** covered by this checklist
(no v2 build is on the test device). If a v2 install exists:
- [ ] Install v3 over v2 (do NOT clear data): stats, streak, goals,
      unlocks, and premium survive; new keys default sensibly.

Result: _pending / N/A_

## 10. Real screenshot (replaces the mockup)

- [ ] Capture a real device screenshot of the app stage (not the mockup).
- [ ] Replace `assets/screenshot.png` with the capture and update the
      README caption (remove the "representative mockup" note).

Result: _pending_

## Sign-off

- [ ] All sections above filled in with observed results.
- [ ] No section left as "pending" at release.

Tester: __________  Date: __________
