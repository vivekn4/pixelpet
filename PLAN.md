# PixelPet — Developer Plan (v2)

> Status: v2 spec. Author: Squirt (agent) for Vivek Nair (vivekn4).
> Standing rules honored: zero-budget (no paid services, no API keys), VN signature,
> maximalist-but-tasteful SFX/VFX, professional README + screenshot, GitHub Actions
> debug-APK build, test-before-claiming.
> Companion docs: [FLOW.md](FLOW.md) (Mermaid flow charts), [ART.md](ART.md) (animation bible).

## 1. What it is (v2)

**PixelPet** — a pixel-art virtual pet that lives on your **lock screen** (via live
wallpaper), your **home screen** (widget), and in a full app. You raise **Pip** through
three pet categories — **Normal → Wild → Mystical** — unlocked by consistent care,
long-term achievements, or a premium license key.

The core loop is unchanged in spirit and expanded in depth:

- **Feeding** = completing real-life goals (gym, study, ship a video). Plus snack feeding.
- **Playing** = tap-to-play interaction (energy cost, happiness gain, giggle SFX).
- **Attention** = petting (in-app or *right on the lock-screen wallpaper* via touch).
  A new **attention meter** drains when Pip is ignored — Pip shows "needs attention"
  states and nudges you *before* stats suffer.
- **Doomscrolling** = Pip sulks (manual log + optional on-device UsageStats auto-detect).
  Documented approximation: YouTube is counted wholesale — UsageStats cannot distinguish
  Shorts from long-form, so long-form YouTube time also counts toward the threshold.
- **Sleep** = screen off / AOD active → Pip sleeps, decay drops to ~10%.
  Screen on → wake animation + "while you were away…" recap.

### 1.1 Honest platform notes (documented, not hidden)

- **Lock-screen widgets were removed in Android 5.0.** There is no supported API to put
  an interactive widget on the lock screen. The real mechanism is a **live wallpaper**
  (`WallpaperService`): on Samsung devices (incl. the user's S26 Ultra) the system lets
  you set a *separate* lock-screen wallpaper, so Pip is visible every time the phone wakes.
  On Pixels the same wallpaper covers home + lock unless the user picks otherwise.
- **Third-party apps cannot draw on Always-On Display.** This is a hard system limitation.
  Design answer: `ACTION_SCREEN_OFF` (which is also when AOD takes over) → Pip enters
  SLEEP: stat decay drops to ~10%, and on next wake the pet shows the sleeping animation
  briefly, then the wake animation + recap. The pet never pretends to be on the AOD.
- **`ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON` cannot be received via manifest receivers.**
  They must be registered dynamically at runtime. Sleep/wake tracking therefore works
  while the live-wallpaper engine or the app process is alive — documented in-app and here.

## 2. Pet roster & unlock progression

### 2.1 Categories

| Category | Species (v2) | Palette | Signature features |
|---|---|---|---|
| **Normal** (starter) | Cat, Dog, Bunny, Hamster | soft pastels | ears / whiskers / long ears / cheek pouches |
| **Wild** (streaks) | Wolf, Fox, Tiger, Owl | saturated naturals | pointy ears, stripes, big owl eyes + beak |
| **Mystical** (achievements / premium) | Dragon, Phoenix, Unicorn, Kraken | vivid + glow accents | horns, wings, crest, horn+ mane, tentacles |

The renderer stays parametric: one blob body + per-species feature set + per-species
palette + per-mood face (shared mood system). Adding a species = one enum entry with
colors + feature flags — no new art pipeline.

### 2.2 Unlock progression

- **Normal:** all 4 available from the start. First-run picker ("choose your Pip").
- **Wild** — unlocked by **care streaks** (a "care day" = ≥1 goal completed that day):
  - Wolf: 7-day care streak · Fox: 14-day · Tiger: 21-day · Owl: 30-day
- **Mystical** — **achievements AND/OR premium** (either path works):
  - Dragon: 60-day care streak **or** premium
  - Phoenix: 100 lifetime goals completed **or** premium
  - Unicorn: 90-day care streak **or** premium
  - Kraken: premium only (the "white whale" — aspirational)
- Unlocking a species triggers the **evolve celebration**: fanfare SFX + particle storm +
  species-select confetti. Switching active pet is free and instant; each pet keeps its
  own stats (per-pet state map — neglect has consequences, but no permadeath in v2).

### 2.3 Premium tier

- **Free:** all Normal pets + the full core care loop (feed/play/attention/sleep).
- **Premium:** Wild + Mystical categories unlocked immediately, exclusive skins
  (per-species alt palettes) and exclusive animations (aura particles, evolution trails),
  extra pet slots (raise up to 3 simultaneously — v2 ships 1 active + slots unlock).
- **Activation: license-key entry (Gumroad pattern).** Manual key delivery by the seller;
  the app validates **locally** with an HMAC-SHA256 scheme:
  - Key format: `VNPP-XXXXXX-XXXXXX-XXXXXX` (Crockford base32, no ambiguous chars).
  - Payload: 80-bit key id + 48-bit HMAC-SHA256(key id, embedded secret), verified on device.
  - The secret is embedded obfuscated (split + XOR). **Honest note:** local validation is
    obfuscation, not DRM — appropriate for indie Gumroad-style sales, not for stopping
    determined piracy. Play Billing is the documented future path.
  - Entitlements re-checked on every premium-gated access; key id stored, key itself not.
- **Price: TODO — user's call.** (Suggested thinking in README: one-time, not subscription.)

## 3. Architecture (v2)

**Stack:** Kotlin, single `:app` module, minSdk 26 / targetSdk 34 / compileSdk 34.
**Still zero external dependencies** — framework APIs only.

```
app/src/main/
├── AndroidManifest.xml            # + wallpaper service, screen receiver notes
├── java/com/vn/pixelpet/
│   ├── MainActivity.kt            # pet stage + 4 stats + feed/play/pet/doomscroll
│   ├── SpeciesActivity.kt         # roster grid: locked/unlocked, select, evolve FX
│   ├── PremiumActivity.kt         # key entry, entitlement display
│   ├── GoalsActivity.kt           # (v1, feeds pet on completion)
│   ├── AboutActivity.kt           # VN signature, honest platform notes
│   ├── PetView.kt                 # canvas pet + full VFX particle engine
│   ├── PetRenderer.kt             # parametric species renderer (12 species × 8 moods)
│   ├── Species.kt                 # enum: palette, features, unlock rules
│   ├── PetState.kt                # + speciesId, attention, streaks, achievements
│   ├── PetRepository.kt           # single source of truth (see §3.1)
│   ├── UnlockManager.kt           # streak/achievement evaluation, unlock events
│   ├── PremiumManager.kt          # HMAC key validation, entitlement checks
│   ├── SfxEngine.kt               # + eat/play/pet/evolve/unlock/key SFX
│   ├── Haptics.kt                 # vibration patterns per event (VibrationEffect)
│   ├── Goal.kt                    # (v1)
│   ├── wallpaper/
│   │   └── PixelWallpaperService.kt  # lock-screen pet, touch = petting
│   ├── widget/
│   │   ├── PixelPetWidgetProvider.kt # face bitmap + QUICK FEED button
│   │   └── WidgetUpdateJob.kt
│   ├── doomscroll/                # (v1)
│   └── util/
│       ├── BootReceiver.kt
│       └── ScreenReceiver.kt      # dynamic SCREEN_OFF/ON → sleep/wake
└── res/                           # layouts + drawables + values (as v1, extended)
```

### 3.1 Single source of truth

`PetRepository` remains the **only** writer of pet state. The live wallpaper engine, the
widget provider/job, and the activities all read snapshots and post intents/actions back
through the repository — never holding their own stat copies. See FLOW.md §(f).

### 3.2 Stats (v2)

| Stat | Decay | Restores | At zero… |
|---|---|---|---|
| Hunger 0–100 | −4/hr | goal +20 · snack +10 (15-min cooldown) | SICK state after 6h at 0 |
| Happiness 0–100 | −3/hr (×2 while attention = 0) | goal +15 · play +20 · pet +8 | SAD / SULKING |
| Energy 0–100 | −2/hr · +6/hr at night (22–7) | sleep, goal +5 | SLEEPING (<15) |
| **Attention 0–100** | **−6/hr while ignored** | **pet +25 · play +10** | "needs attention" <25 → nudge; 0 → happiness ×2 decay |

"Needs attention": exclamation bubble over the pet (wallpaper/widget/app) + one
notification nudge per 4h (channel `attention`, API 26+ channels; runtime permission
requested on API 33+).

### 3.3 Care actions

- **Feed (goal):** existing — goal complete → +20 hunger/+15 happiness/+5 energy, confetti.
- **Feed (snack):** widget quick-feed button + app button — +10 hunger, 15-min cooldown,
  munch SFX, hearts.
- **Play:** app button (and wallpaper double-tap) — pet jump/spin, −10 energy, +20
  happiness, +10 attention, giggle SFX, 5-min cooldown.
- **Pet (attention):** tap/drag on pet in app, or **touch the live wallpaper** —
  +25 attention, +8 happiness, purr SFX, heart particles, light haptic tick.
- **Doomscroll log:** −15 happiness, −5 energy, 30-min sulk (as v1).

### 3.4 Sleep/wake

- `ScreenReceiver` (dynamically registered by the wallpaper engine and by MainActivity):
  - `ACTION_SCREEN_OFF` → `PetRepository.enterSleep()`: decay multiplier 0.1×, record
    `sleepStart`; pet shows SLEEPING on next render.
  - `ACTION_SCREEN_ON` → `wake()`: wake animation, SFX shimmer, and a recap line —
    "While you were away… hunger −2, Pip missed you (+attention nudge if low)."
- While asleep the wallpaper engine stops its draw loop (battery); widget shows the
  sleeping face.

### 3.5 Live wallpaper

`PixelWallpaperService : WallpaperService`, engine:
- Draws the pet (same `PetRenderer`) centered, subtle starfield/gradient bg, at ~30fps
  only while visible (`onVisibilityChanged` gates the loop).
- `onTouchEvent`: tap/drag on pet → `PetRepository.pet()` (attention + hearts + purr).
  Double-tap → play action.
- `onOffsetsChanged`: gentle parallax of background layers (maximalist, cheap).
- Battery: no draw loop when not visible or when screen off.
- Settings hook: `WallpaperService` settings activity → opens the app's species picker.

## 4. Likely bugs / problems and mitigations (v2 additions)

| # | Problem | Mitigation |
|---|---|---|
| 13 | Wallpaper engine keeps drawing with screen off (battery) | Gate loop on `isVisible && screenOn`; also stop on `onSurfaceDestroyed`. |
| 14 | Touch coordinates on wallpaper misaligned (scroll offsets) | Convert with `x - offset*width` using `onOffsetsChanged` values; generous hit radius (pet bounds × 1.4). |
| 15 | 12 species × 8 moods = 96 sprite variants — renderer bugs | Parametric features, one code path; per-species golden bitmap test via `PetRenderer.render` in QA script (pixel-diff against checked-in references). |
| 16 | Premium key sharing / trivial bypass | Accept as obfuscation-tier; rate-limit key entry (5 tries → 1h lockout); Play Billing on roadmap. |
| 17 | Attention-notification spam | One nudge per 4h max; auto-cancelled when attention recovers; channel lets user silence. |
| 18 | Streak cheating via clock change | Streaks use wall-clock *dates* (user-visible concept); decay uses elapsedRealtime — documented split. |
| 19 | Multiple pets' state bloat | Per-pet state stored as compact JSON map; only active pet ticks (others frozen — documented as "pet daycare"). |
| 20 | Widget quick-feed spam | 15-min cooldown persisted; button disabled state rendered into face bitmap. |

(v1 items 1–12 still apply.)

## 5. UI/UX thinking (v2)

- **Species picker:** horizontal card carousel; locked species show silhouette + unlock
  requirement ("🔥 21-day streak" / "👑 Premium"); tapping an unlocked species plays the
  evolve celebration inline.
- **Widget v2:** face bitmap (pet + 4 mini bars + attention bubble if needed) + a gold
  **"Feed 🍪"** quick button beneath; tap pet → app, tap feed → snack (cooldown-aware).
- **App home:** pet stage (tap = pet, double-tap = play), 4 stat bars, action row:
  Feed / Play / Pet / Doomscroll. Attention bar pulses gold when < 25.
- **Lock screen:** the pet, idle-bobbing over a slow starfield; touch ripples on petting.
- **Onboarding v2:** choose species → meet Pip → add goal → "set as lock-screen
  wallpaper" deep-link (`ACTION_LIVE_WALLPAPER_CHOOSER`) → widget pin prompt.
- **Haptics:** tick on pet, thud on feed, buzz pattern on sulk, fanfare triple-buzz on
  unlock — see ART.md. All haptics respect system settings and the mute toggle.

## 6. Build / CI / QA plan (unchanged +)

- Same Gradle wrapper + GitHub Actions debug-APK pipeline.
- QA additions: `qa_species.py` renders all 12 species × 8 moods and pixel-diffs
  against `assets/species_reference/` golden bitmaps (regenerate with `--regen`).
- Manual-build fallback documented in v1 still applies if Gradle daemon won't start.

## 7. Roadmap (v2+)

Play Billing · pet evolution stages · 3 simultaneous pets UI · Wear OS tile ·
per-app auto-detect pickers · cloud backup · seasonal species.
