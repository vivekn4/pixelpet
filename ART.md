# PixelPet — ART.md · VFX / SFX / Animation Bible (v2)

> The single reference for how Pip **moves, sounds, and feels**. Maximalist but
> coherent: every state has one signature motion, one signature sound, one
> signature particle. Nothing is decoration-only — every effect answers a
> gameplay event. Spec: [PLAN.md](PLAN.md) · Flows: [FLOW.md](FLOW.md).

## 0. Global rules

- **Pixel truth:** all pet art is rects on a coarse grid, anti-alias OFF, nearest-neighbor
  upscale. If it isn't crisp, it's wrong.
- **Palette discipline:** pet body color is per-species; effects use the fixed FX palette
  (gold `#FFD54A`, pink `#FF9DC6`, sky `#6AC8FF`, mint `#5AFF8A`, red `#FF5A5A`).
- **Timing discipline:** UI feedback < 100 ms after touch; celebration < 1.2 s total;
  ambient loops are slow (≥1 s period) so they read as "alive", not "busy".
- **Mute & haptics** are user-respecting: the mute toggle kills SFX *and* haptics;
  haptics also yield to system settings (check `Settings.System.HAPTIC_FEEDBACK_ENABLED`
  where applicable).
- **No binary assets.** Every sound is synthesized at first launch (SoundPool);
  every visual is drawn in code.

## 1. Per-state animation specs

| State | Signature motion | Face | Ambient extras |
|---|---|---|---|
| **Idle** (any mood) | 2 px vertical sine bounce, 1.2 s period | mood face | blink every 2.6–4.4 s (eyes → 1 px lines, 130 ms) |
| **Happy** | bounce amplitude ×1.6, slight side-to-side sway | `^ ^` arcs, open smile, cheeks | occasional sparkle particle |
| **Eating** (transient, 1 s) | rapid small chomps (scaleY 1→0.85, 3×) | open mouth, crumbs particles | 3 munch SFX bites in sync |
| **Playing** (transient, 1.2 s) | jump + 360° spin-shake, squash & stretch on land | `^ ^` eyes, tongue | motion streaks, giggle SFX |
| **Sleeping** | still (bounce → 0), slow breathing scale (4 s) | closed lines | `Z` sprites float up, dim overlay |
| **Sulking** | turned 1-cell away, horizontal shiver (rare) | flat frown, pupils to edge | rain cloud + drizzle above |
| **Sick** | slow droop (sine at 0.4 Hz, low amplitude), pale body tint | half-mast eyes, wavy mouth | green-ish puff particles, heartbeat SFX |
| **Evolving** (unlock, 2 s) | rise + spin + scale 1→1.3→1 | `^ ^`, star eyes | **particle storm**: 60 confetti + ring shockwave + aura |
| **Needs attention** | idle + periodic hop toward viewer (every 5 s) | normal + **!** bubble above | gold pulse ring on attention bar |

Mood → state mapping lives in `PetRenderer.moodFor` (+ `SICK` when hunger = 0 for 6h,
`ATTENTION_NEEDED` overlay when attention < 25).

## 2. Species rendering notes (12 species, parametric)

One blob body + feature set + palette per species. Features are drawn *around* the
shared face so moods stay readable on every species.

| Species | Palette (body/dark) | Features |
|---|---|---|
| Cat | `#B9A7FF` / `#7A63D8` | triangle ears, 3 whisker lines/side |
| Dog | `#D8A76A` / `#9A6B3F` | floppy ears (side rounded rects), tongue on happy |
| Bunny | `#F2F2FA` / `#B9B9D6` | long ears (2×5 cells), pink inner ear |
| Hamster | `#E8B04B` / `#A8762A` | oversized cheek pouches, tiny round ears |
| Wolf | `#8A9BB5` / `#5A6B85` | sharp ears, pale muzzle patch |
| Fox | `#E8823F` / `#A8541F` | sharp ears, white tail-tip patch (side), snout |
| Tiger | `#E8A33D` / `#9A6218` | ears + 3 vertical stripes |
| Owl | `#9A7B5A` / `#6B543F` | **big eyes** (3×4 whites), beak, ear tufts |
| Dragon | `#5AC86A` / `#2F8A3F` | horns, wing nubs, back spikes |
| Phoenix | `#FF7A3F` / `#B83A1A` | crest feathers, wing nubs, ember glow outline |
| Unicorn | `#FAFAFF` / `#C6C6E8` | center horn (gold), mane stripe, sparkles |
| Kraken | `#4FD8C8` / `#2A8A80` | 4 tentacles (bottom), extra side eyes |

Mystical species get a subtle animated **aura** (premium): 8 orbiting glow dots, 3 s period.
Kraken is premium-only — its tentacles wave on a sine offset per tentacle.

## 3. Particle effects

| Effect | Trigger | Composition |
|---|---|---|
| Confetti burst | goal complete | 26 rects, 5 FX colors, gravity, 900 ms |
| Particle storm | species unlock / evolve | 60 confetti + expanding ring + screen flash 120 ms |
| Hearts | feed / pet | 6 pink hearts (2 circles + rect), rise 1.1 s |
| Crumbs | eating | 8 brown rects, short hop + fall |
| Drizzle | sulking | 3 slanted sky lines looping under cloud |
| Zzz | sleeping | `Z` glyphs rising, alpha fade |
| Sparkles | happy idle / unicorn | 4-point star rects, twinkle |
| Aura orbit | mystical + premium | 8 glow dots orbiting, additive-ish (screen blend via alpha) |
| Touch ripple | wallpaper pet | expanding ring from touch point, 400 ms |
| Screen flash | evolve/key success | full-screen gold alpha 0→90→0, 300 ms |

Particle budget: ≤ 80 live particles; pooled list, no allocation in the draw loop
(pre-sized `ArrayList`, swap-remove).

## 4. Screen transitions

- Activity → Activity: slide + fade 220 ms (overridePendingTransition, framework anims).
- Species select: tapped card does a 150 ms pop (scale 1→1.12→1) then evolve storm.
- Dialogs (add goal, doomscroll confirm): fade + scale-up 180 ms from 0.92.
- Widget → app: standard launch; pet does a greeting hop on `onResume` if mood improved.
- Wallpaper apply: 400 ms crossfade handled by the system; engine starts with a rise-in.

## 5. SFX list (all synthesized — SoundPool, 44.1 kHz 16-bit mono)

| # | Name | Design | Trigger |
|---|---|---|---|
| 1 | `ui_click` | 1200 Hz square blip, 40 ms | every tap |
| 2 | `goal_complete` | C5–E5–G5–C6 square arpeggio, 450 ms | goal done |
| 3 | `feed_munch` | 3 descending square bites | feeding |
| 4 | `happy_chirp` | 2 rising sine chirps | mood → HAPPY |
| 5 | `sulk_slide` | 600→200 Hz sine slide, 500 ms | doomscroll logged |
| 6 | `sad_blip` | 220 Hz square, 150 ms | low-stat warning |
| 7 | `widget_boop` | 880 Hz soft sine, 80 ms | widget tap |
| 8 | `sleep_shimmer` | 440→660 Hz triangle swell, 400 ms | entering sleep |
| 9 | `wake_chime` | 660→880→1320 sine, 350 ms | wake |
| 10 | `play_giggle` | 5 quick 900–1400 Hz sine hops, 400 ms | playing |
| 11 | `pet_purr` | 90 Hz triangle tremolo (AM 12 Hz), 700 ms | petting |
| 12 | `evolve_fanfare` | C–E–G–C–E–G up + shimmer, 900 ms | species unlock |
| 13 | `unlock_chime` | 1320→1760 sine + sparkle noise, 300 ms | Wild unlock toast |
| 14 | `key_success` | major triad + octave, 500 ms | premium key accepted |
| 15 | `key_error` | 200→150 Hz square buzz, 250 ms | key rejected |
| 16 | `heartbeat` | 55 Hz thump ×2, 600 ms loop | sick state (interval) |
| 17 | `attention_ping` | 1568 Hz sine ping ×2, 300 ms | needs-attention nudge |

Master gain 0.8. Longest < 1 s (except purr/heartbeat loops, which are state-gated).
Synthesis details: 5 ms attack, 0.7 decay slope, 10 ms inter-note gaps — see `SfxEngine`.

## 6. Haptic patterns (`VibrationEffect`, API 26+)

| Event | Pattern |
|---|---|
| tap / pet | `oneShot(15 ms, 80)` tick |
| feed / goal | `oneShot(40 ms, 160)` soft thud |
| play | `waveform([0, 20, 40, 30], -1)` double-tap bounce |
| sulk | `waveform([0, 60, 80, 60], -1)` low double-buzz |
| evolve / unlock | `waveform([0, 30, 60, 30, 60, 60], -1)` triple fanfare |
| key error | `oneShot(120 ms, 200)` long buzz |
| heartbeat (sick) | repeating `oneShot(50 ms)` every 900 ms while sick |

All haptics route through `Haptics.kt` (`maybeVibrate` checks mute + system setting).

## 7. Coherence checklist (review before shipping any new effect)

1. One signature motion + one signature sound per state — no duplicates.
2. Effect answers a gameplay event (no pure decoration).
3. Respects mute; never the *only* feedback channel (always paired visual).
4. Particle count within budget; no draw-loop allocation.
5. Readable at widget size (arm's length, < 1 s).
