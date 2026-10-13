<!-- description: PixelPet — a pixel-art virtual pet that lives on your Android lock screen (live wallpaper) + home-screen widget. Raise 12 species across Normal, Wild and Mystical; complete real-life goals to feed them, pet them for attention, and don't doomscroll. 🐾 -->

# 🐾 PixelPet

![Android CI](https://github.com/vivekn4/pixelpet/actions/workflows/android.yml/badge.svg)

**A pixel-art virtual pet that lives on your lock screen.**
Meet **Pip** — but Pip is just the beginning. Raise **12 species** across three
categories: **Normal** (cat, dog, bunny, hamster), **Wild** (wolf, fox, tiger, owl),
and **Mystical** (dragon, phoenix, unicorn, kraken).

- 📱 **Lock-screen pet** — a live wallpaper renders Pip on your lock screen
  (Samsung: set it as the separate lock-screen wallpaper). Tap to pet,
  double-tap to play. *(Android removed lock-screen widgets in 5.0 — the live
  wallpaper is the real mechanism.)*
- 🌙 **AOD = sleep** — third-party apps can't draw on Always-On Display, so when
  the screen goes off Pip sleeps (decay ×0.1). Wake up to a recap:
  *"While you were away…"*
- 🎯 **Feeding = real life** — complete goals (gym, study, ship a video) to feed Pip.
- ✋ **Attention meter** — pet Pip or he'll get lonely; neglect him and happiness
  drains twice as fast.
- 🌧 **Doomscroll = sulk** — manual log + optional on-device auto-detect.
- 👑 **Premium** — Wild + Mystical pets, exclusive skins and aura animations, via
  license-key activation (Gumroad pattern). *Price: TODO — one-time, user's call.*

> ✦ VN ✦ — Made by Vivek Nair

## Screenshot

![PixelPet app UI mockup](assets/screenshot.png)

*Above: representative UI mockup (not a device capture) — the real app renders Pip
live on canvas with idle bounce, blinking, confetti, hearts, and sulk animations.*

## How it works

| Action | Effect on Pip |
|---|---|
| ✅ Complete a goal | +Hunger, +Happiness, confetti burst, happy arpeggio |
| 🍪 Snack (widget/app) | +Hunger, 15-min cooldown |
| ▶ Play (double-tap pet) | −Energy, +Happiness, +Attention, giggle |
| ✋ Pet (tap pet / touch wallpaper) | +Attention, +Happiness, purr, hearts |
| 📱 Log doomscrolling (or auto-detect) | −Happiness, Pip sulks for 30 min |
| ⏰ Time passes | Stats decay hourly; attention drains when ignored |
| 🌙 Screen off | Pip sleeps — decay ×0.1, wake shows a recap |

**Unlocks:** Wild pets via care streaks (7/14/21/30 days); Mystical via
long-term achievements (60/90-day streaks, 100 lifetime goals) **or** 👑 Premium.
The Kraken is premium-only — the white whale. 🦑

**Doomscroll auto-detect (optional, off by default):** uses Android's on-device
UsageStats API to notice heavy short-video app use (TikTok / Instagram / YouTube)
and auto-logs once per day. Nothing ever leaves your phone — it works fully offline.

## Features

- 📱 **Lock-screen live wallpaper** — Pip on every wake; tap to pet, double-tap to play
- 🏠 **Home-screen widget** — glanceable Pip + 4 mini stat bars + quick-feed button
- 🐾 **12 species, 3 categories** — parametric pixel-art renderer, one code path
- 🔓 **Unlock progression** — care streaks and achievements (or premium shortcut)
- 👑 **Premium tier** — HMAC license-key activation, exclusive skins + aura VFX
- 🎯 **Goal tracker** — daily or one-shot goals with streaks; completing one feeds Pip
- ✋ **Attention loop** — petting, "needs attention" states, rate-limited nudges
- 🌙 **Sleep/wake** — screen off sleeps Pip (0.1× decay), wake shows a recap
- 🌧 **Doomscroll logging** — manual button + optional UsageStats auto-detector
- 🔊 **17 synthesized SFX** — generated in code at first launch; **zero audio files**
- ✨ **Canvas VFX** — idle bounce, blinking, confetti storms, hearts, screen shake, sleep Z's
- 📳 **Haptic patterns** — per-event effects that respect mute + system settings
- 🔇 **Mute toggle**, 📌 first-launch widget pin prompt, 📴 full offline support

## Build

Requires JDK 17. No API keys, no paid services, no accounts.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push builds a debug-signed, sideloadable APK via GitHub Actions
(see *Actions* → *Android CI* → `pixelpet-debug-apk` artifact).

**Sideloading on Android 13+:** after installing the APK, grant
*Settings → Apps → PixelPet → ⋮ → Allow restricted settings* if the system
prompts you — required for sideloaded apps requesting special access.

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/vn/pixelpet/
│   ├── MainActivity.kt        # pet stage, 4 stats, feed/play/pet/doomscroll
│   ├── SpeciesActivity.kt     # 12-species roster, lock states, evolve FX
│   ├── PremiumActivity.kt     # HMAC license-key activation
│   ├── GoalsActivity.kt       # goal list (add / complete / delete, streaks)
│   ├── AboutActivity.kt       # how-to-play, ✦ VN ✦ signature, platform notes
│   ├── Species.kt             # roster enum: palettes, features, unlock text
│   ├── PetView.kt             # canvas pet + particle VFX engine
│   ├── PetRenderer.kt         # parametric species renderer (shared everywhere)
│   ├── PetState.kt / PetRepository.kt   # single source of truth
│   ├── UnlockManager.kt       # streak/achievement evaluation
│   ├── PremiumManager.kt      # key validation, entitlements
│   ├── SfxEngine.kt           # 17 synthesized sounds, SoundPool
│   ├── Haptics.kt             # per-event vibration patterns
│   ├── Goal.kt                # goal model (org.json, no libraries)
│   ├── wallpaper/             # lock-screen live wallpaper (tap = pet)
│   ├── widget/                # glanceable widget + quick-feed button
│   ├── doomscroll/            # optional UsageStats auto-detector
│   └── util/                  # boot rescheduler, screen on/off, notifications
└── res/                       # layouts, drawables, values, widget/wallpaper info
tools/genkey.py                # mint premium license keys (seller-side)
```

**Design decisions worth knowing:**

- **Zero external dependencies** — framework APIs only (no AndroidX, no libraries).
  Fewer moving parts, faster CI, nothing to break on version bumps.
- **Time model** — decay runs on `elapsedRealtime` deltas with a wall-clock fallback
  and a 48-hour cap, so reboots and clock changes can't corrupt Pip.
- **Widget face = one bitmap** — RemoteViews can't host custom views, so the provider
  renders Pip + bars + label into a single cached bitmap per mood.
- **Audio never blocks UI** — WAVs synthesize on a background thread; `play()` is a
  no-op until loading completes.

Full architecture, bug analysis, and honest platform notes: [PLAN.md](PLAN.md).
Flow charts (Mermaid): [FLOW.md](FLOW.md). Animation bible: [ART.md](ART.md).

## Roadmap

Goal XP levels · pet evolution stages · "Pip misses you" reminders · Wear OS tile ·
per-app pickers for auto-detect · cloud backup.

## License

MIT — do whatever you want with it. If Pip guilt-trips you into going to the gym,
the system works.
