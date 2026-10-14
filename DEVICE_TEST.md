# Pixel Pets v3 — Real-Device Tests (Device Matrix)

Status: **PENDING USER** — nothing below has been run or verified yet.
Do not claim any result in this file until it has actually been observed
on the device. Fill in the Result column as you go.

The app must run on EVERY Android device, not just the flagship: the
matrix below covers a low-end API 26 device, a mid-range device, the
S26 Ultra, and a tablet. Run sections 1–10 on each matrix device where
feasible; the low-end device gets extra attention on jank (§12) and
battery (§11); the tablet gets extra attention on layouts (species grid
columns, toolbar, widget sizing).

## Device matrix

| Device | Why it matters | Priority checks |
|---|---|---|
| Low-end, API 26 (Android 8.0), ~2 GB RAM class | minSdk floor: oldest OS + tightest memory | Install, §8 jank, §11 battery, no OOM on 108-variant roster |
| Mid-range, API 30–33 (Android 11–13) | The bulk of the installed base; POST_NOTIFICATIONS (33) + receiver rules | Install, nudges permission, widget, powers |
| S26 Ultra (flagship, 120 Hz) | Reference device: 120 Hz frame budget, wallpaper roam, aura | All sections, §8 at 120 Hz, real screenshot (§10) |
| Tablet (any API 26+) | Large screens, foldables: grid columns, toolbar, widget sizing | Layouts (§1 + species grid), wallpaper canvas sizing |

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

## 11. Battery (overnight — low drain by design)

The app holds NO wake locks, schedules NO alarms, runs NO foreground
service, and performs NO network calls. The only scheduled work is the
30-min JobScheduler widget refresh (batched/deferrable by the system);
the wallpaper loop runs only while visible + screen-on and pauses after
30 s idle. Overnight drain should be indistinguishable from baseline.

- [ ] Baseline: record Settings → Battery % at bedtime with the app
      installed but wallpaper NOT set and no widget placed.
- [ ] Test night: set the wallpaper + place the widget, same conditions.
- [ ] Collect: `adb shell dumpsys batterystats --reset` at bedtime,
      then `adb shell dumpsys batterystats > stats.txt` in the morning
      (or open the dump in Battery Historian).
- [ ] Target: PixelPet's overnight drain within noise of baseline
      (no wake-lock / alarm / job attribution beyond the 30-min refresh).
- [ ] No "app is using battery" system warnings.

Result: _pending_

## 12. Jank (smooth operation)

- [ ] In-app stage: `adb shell dumpsys gfxinfo com.vn.pixelpet framestats`
      while interacting (pet/play/feed/powers) for 60 s.
      Targets: p95 < 16 ms on 60 Hz screens, p95 < 8 ms on 120 Hz
      (PetView caches the pet bitmap across frames; re-renders only when
      the render key changes).
- [ ] Wallpaper: same command while the lock-screen wallpaper is visible
      and roaming for 60 s. Target: p95 < 33 ms (the loop is ~30 fps).
- [ ] No dropped-frame spikes when the bitmap re-renders (render-key
      change) or when a power effect fires.
- [ ] Low-end device: no visible stutter on first launch (SFX synthesis
      runs on a background thread) or on species-grid scroll.

Result: _pending (in-app p95: ___ ms, wallpaper p95: ___ ms)_

## Sign-off

- [ ] All sections above filled in with observed results, per matrix device.
- [ ] No section left as "pending" at release.

Tester: __________  Date: __________
