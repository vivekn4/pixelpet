# PixelPet — Developer Plan (v3): 108 Pets, Walking, Personality & On-Device Brain

> Status: v3 spec. Extends [PLAN.md](PLAN.md) (v2: 12 species, live wallpaper, widget,
> goals, premium keys). Author: Squirt (agent) for Vivek Nair (vivekn4).
> Standing rules honored: zero-budget (no paid services, no API keys), VN signature,
> maximalist-but-tasteful SFX/VFX, professional README + screenshot, GitHub Actions
> debug-APK build, test-before-claiming.
> Companion docs: [FLOW.md](FLOW.md), [ART.md](ART.md) (extend both for v3).

## 0. How professional virtual-pet apps actually do this

Tamagotchi, Pou, Finch, Peridot and friends do NOT run an LLM on your phone.
The professional playbook is:

- **State machines** — the pet is always in one state (idle, walk, eat, play, sleep,
  sulk) with rules for switching. Believability comes from transitions, not brains.
- **Parametric art** — one renderer + data (palette, features, accessories) produces
  every variant. Nobody hand-draws 108 sprites; they draw one system and feed it data.
- **Canned dialogue pools** — lines are written per mood/personality and picked by
  weighted random. Variety without generation.
- **Utility-score behavior** — every possible action gets a score from current stats
  + personality + time of day; the highest score wins. This *is* the "AI" in most
  pet games: a few dozen numbers, no tokens, no network.
- **Motion principles** — squash & stretch, anticipation, follow-through, easing.
  That is what makes a pet feel "alive" and "realistic", not photorealism.

This plan copies that playbook honestly. Where v3 says "AI", it means an on-device
adaptive rule system. Where it says "realistic", it means believable motion.
Nothing here needs an API key, a server, or a single token.

## 1. What v3 adds (on top of v2)

1. **108 pet variations** across 3 categories — cats (12), dogs by breed (15), and
   every other family the user asked for: pigs, ox, bison, dragons, and more (§2).
2. **Walking** — the pet roams the stage, with a real walk cycle, smooth
   delta-time movement, turn-arounds, pauses, and idle variety (§3).
3. **Personalities** — 5 trait axes that shape how each pet behaves (§4).
4. **Native Brain** — on-device learning from the user behaviour: schedule, favorites,
   neglect memory. No API, no tokens, fully offline (§5).
5. **Powers** — one cute, gimmicky, cosmetic-first power per family (§6).

## 2. Roster: 108 variations

A **variation** = one data entry: base species + palette + feature deltas + one
accessory + trait bias. The parametric renderer stays ONE code path. Adding a
variation is a row in `Variant.kt`, not a new art pipeline. Total: **108**.

### Normal (57) — available from the start

| Family | Count | Variations |
|---|---|---|
| Cats | 12 | Tabby, Tuxedo, Siamese, Persian, Bengal, Calico, Black, White, Ginger, Sphynx, Maine Coon, Ragdoll |
| Dogs | 15 | Labrador, Golden Retriever, German Shepherd, Pug, Husky, Corgi, Beagle, Poodle, Bulldog, Dalmatian, Chihuahua, Shiba Inu, Border Collie, Dachshund, Great Dane |
| Bunnies | 5 | White, Grey, Brown, Lop, Angora |
| Hamsters | 4 | Golden, Panda, Robo, Winter White |
| Pigs | 6 | Pink, Spotted, Mini, Mud, Wild Boar, Piglet |
| Ducks | 3 | Yellow, Mallard, White |
| Turtles | 3 | Green, Red-Eared, Tortoise |
| Frogs | 3 | Green, Tree, Poison Dart |
| Penguins | 3 | Emperor, Rockhopper, Baby |
| Pandas | 3 | Giant, Red, Baby |

### Wild (27) — unlocked by care streaks (v2 rules, extended)

| Family | Count | Variations |
|---|---|---|
| Wolves | 4 | Grey, Arctic, Black, Timber |
| Foxes | 4 | Red, Arctic, Fennec, Silver |
| Tigers | 4 | Bengal, White, Siberian, Cub |
| Owls | 4 | Barn, Snowy, Horned, Tawny |
| Ox & kin | 6 | Ox, Bison, Buffalo, Cow, Yak, Highland Cow |
| Snakes | 3 | Green, Python, Coral |
| Bats | 2 | Fruit Bat, Vampire Bat |

### Mystical (24) — achievements and/or premium (v2 rules, extended)

| Family | Count | Variations |
|---|---|---|
| Dragons | 10 | Fire, Ice, Storm, Forest, Shadow, Gold, Water, Ember, Crystal, Baby |
| Phoenix | 3 | Flame, Ember, Dawn |
| Unicorns | 3 | White, Starlight, Rainbow |
| Kraken | 3 | Teal, Deep Sea, Baby |
| Axolotl | 2 | Pink, Golden |
| Griffins | 3 | Gold, Storm, Forest |

Unlock ladder (same shape as v2): Normal = day one. Wild = care streaks
(7/14/21/30-day tiers, mapped across the 27). Mystical = long achievements
(60/90-day streaks, 100 lifetime goals) or premium. Kraken stays premium-only.
Each unlock still fires the evolve celebration (fanfare + particle storm).

## 3. Animation: walking, smooth, believable

### WalkEngine — a state machine, not a script

`IDLE → LOOK → WALK → PAUSE → IDLE …` with overlays for EAT / PLAY / SLEEP /
SULK / POWER. Every transition has an ease; nothing snaps.

- **Walk cycle:** 2-frame leg alternation + vertical body bob + tail/ear lag
  (follow-through) + dust puff on step landing. Legs are drawn procedurally
  (rects), so all 108 variants walk with zero extra art.
- **Movement:** delta-time based (pixels/second × dt), NOT frame-count based —
  same speed at 30fps or 60fps. Ease-out on arrival, squash & stretch on
  step landing, crouch (anticipation) before a jump.
- **Roam:** pet picks a random reachable point in the stage bounds, walks there,
  pauses 2–6s (look-around / blink / tail flick), repeats. Turn-around = flip +
  120ms turn hop. Roam pauses during EAT/PLAY/SLEEP states.
- **Frame budget:** 60fps in-app, ~30fps wallpaper. No allocation in the draw
  loop (pooled particles, pre-sized lists — v2 rule, kept).
- **Widget:** RemoteViews still cannot host a live view — the widget renders the
  CURRENT walk frame into one bitmap (v2 limitation, unchanged).
- **Wallpaper:** same roam bounded to the wallpaper canvas; loop gated on
  visibility + screen-on (v2 battery rule, kept). Tap = pet, double-tap = play.

### "Realistic" — honest definition

Pixel art is stylized by design; nobody will mistake Pip for a photograph.
"Realistic" in v3 means **believable motion**: weight (squash on landing),
anticipation (crouch before jump), follow-through (ears/tail trail the body),
and idle variety (no two idle seconds look identical). That is what pro apps do,
and it is what this plan specs. No photorealism claims anywhere in README/UI.

## 4. Personality — 5 trait axes, 0–100 each

| Axis | Low end | High end |
|---|---|---|
| Playful ↔ Calm | naps often, slow roam | seeks play, fast roam, more giggles |
| Bold ↔ Shy | hides on doomscroll, slow to approach | walks to screen center, greets on wake |
| Greedy ↔ Picky | eats anything, begs | refuses snacks, wants goal-feeds |
| Social ↔ Independent | attention drains fast, nudges often | content alone, rare nudges |
| Neat ↔ Messy | sparkles, tidy idle | mud/sparkle mess idle (pig!), more doodles |

- **Seed:** each variation has a trait bias (e.g. Husky +Playful, Owl +Calm) plus
  a small random spread, so two same-species pets still feel different.
- **Nudge:** interactions move traits ±1–3 (pet → +Social, play → +Playful,
  doomscroll → +Shy). Traits **decay toward baseline** over ~7 days — no
  runaway extremes, no permanently "ruined" pet.
- **Visible effects:** walk speed, roam frequency, attention-seeking, favorite
  actions, idle animations, speech-line pool selection.
- **UI:** Personality card — 5 trait bars + one-line summary
  ("Pip is feeling Playful today") + a "what Pip learned" line (§5).

## 5. Native Brain — on-device learning, no API, no tokens

**Explicit: this is NOT a language model.** It is an adaptive rule system —
weighted scores + decay + time buckets — the same class of system pro pet games
use. It runs 100% on the device, works offline, stores < 100KB of JSON.

### What it learns

| Signal | How it is stored | What the pet does with it |
|---|---|---|
| **Schedule** | interaction counts in 6 time buckets (morning/day/evening/night…) | Pet anticipates: evening player → play-invite animation around that hour; morning feeder → hungry face at breakfast time |
| **Favorites** | per-action + per-food/toy counts | Pet reacts stronger to favorites (bigger hearts, special line); asks for them when bored |
| **Neglect memory** | ring buffer of last 100 events + longest gap | Pet remembers: long absence → shy greeting, then warms up; consistent care → bolder greeting |
| **Streak sensitivity** | care-day history | Pet celebrates streak milestones itself (hop + line) instead of only the UI doing it |

### Cold start

New pet = sensible defaults (average traits, no favorites, no schedule).
Learning becomes visible after ~3 days of use — the Personality card says so
plainly ("Pip is still getting to know you") instead of faking it.

### Speech

Canned line pools per personality × mood (e.g. Playful+Happy, Shy+Sad), picked
by weighted random with a no-repeat window of 8 lines. Variety without
generation, zero network, zero cost. Line count target: ≥ 15 per pool.

### Privacy

Nothing leaves the device. No analytics, no account, no cloud. The Brain state
is local JSON; deleting app data resets it. Say this in README and in-app.

## 6. Powers — one cute gimmick per family

Rules: **cosmetic-first**, cooldowns via `elapsedRealtime` (clock-change proof),
no pay-to-win, premium only changes the LOOK (aura/particles), never the power.

| Family | Power | Effect | Cooldown |
|---|---|---|---|
| Cat | Purr Heal | purr + clears SICK faster, big hearts | 1/day |
| Dog | Fetch Gift | runs off-screen, returns a random gift (coin / cosmetic) | 1/day |
| Bunny | Lucky Hop | hop trail of clovers + tiny coin bonus | 1/day |
| Hamster | Cheek Stash | stores a snack, auto-feeds +5 hunger later | 6h |
| Pig | Mud Bath | joyful mud splat + happiness burst | 1/day |
| Duck | Quack Echo | three quacks + ripple rings | 1/day |
| Turtle | Shell Shield | pauses stat decay for 1h | 1/day |
| Frog | Tongue Catch | catches a passing fly (tiny animation) | 1/day |
| Penguin | Snow Flurry | mini snowstorm over the stage | 1/day |
| Panda | Bamboo Snack | munches bamboo, +hunger | 6h |
| Wolf | Moon Howl | howls at a moon bubble, aura glow | 1/day |
| Fox | Sneak | turns semi-transparent, sneaks across stage | 1/day |
| Tiger | Pounce | playful pounce + screen shake (tiny) | 1/day |
| Owl | Night Watch | shows tomorrow first goal as a reminder | 1/day |
| Ox & kin | Stampede Stomp | happy stomp + dust ring | 1/day |
| Snake | Coil Nap | coils up, fast energy restore | 1/day |
| Bat | Echo Ping | sonar rings reveal a hidden sparkle | 1/day |
| Dragon | Ember Spark | breathes tiny sparkles, screen glitters | 1/day |
| Phoenix | Rebirth | restores ONE missed streak day | 1/week |
| Unicorn | Rainbow Trail | rainbow particles while walking, 30s | 1/day |
| Kraken | Ink Doodle | draws a tiny ink doodle on the stage | 1/day |
| Axolotl | Bubble Parade | floats bubbles upward, 20s | 1/day |
| Griffin | Sky Dive | flies a loop across the stage | 1/day |

UI: power button with a cooldown ring + SFX chime per family. Powers never
gate progression; they are delight, not currency.

## 7. Architecture (v3 additions — stack unchanged)

**Stack:** Kotlin, single `:app` module, minSdk 26 / targetSdk 34 / compileSdk 34.
**Zero external dependencies** — framework APIs only (v2 rule, kept).

```
app/src/main/java/com/vn/pixelpet/
├── Variant.kt             # NEW: 108-entry roster data (species, palette, features, accessory, trait bias, unlock)
├── Personality.kt         # NEW: 5 trait axes, seed/nudge/decay, summary line
├── NativeBrain.kt         # NEW: interaction log, time buckets, favorites, anticipation scores
├── WalkEngine.kt          # NEW: roam state machine, walk cycle, delta-time movement
├── PowerManager.kt        # NEW: power definitions, cooldowns (elapsedRealtime), triggers
├── SpeechBank.kt          # NEW: canned line pools per personality x mood, no-repeat window
├── PetRenderer.kt         # EXTEND: legs + walk frames, accessory layer, power FX hooks
├── PetState.kt            # EXTEND: v3 schema (variantId, traits, brain state, power cooldowns)
├── PetRepository.kt       # EXTEND: v2 -> v3 migration (schema version + defaults)
├── PetView.kt             # EXTEND: WalkEngine host, speech bubbles, power button wiring
├── SpeciesActivity.kt     # EXTEND: 108-variant roster browser (tabs, filter, search)
├── PersonalityActivity.kt # NEW: trait bars + learned-behavior lines
└── wallpaper/             # EXTEND: roam in wallpaper engine (bounded, visibility-gated)
```

### State & migration

- `PetState` gains `schemaVersion = 3`. v2 saves migrate: existing pet keeps
  species/stats; traits default from species bias; brain starts empty; powers
  unlocked. Migration is tested (v2 fixture → v3 → values asserted).
- Per-pet state stays compact JSON; only the ACTIVE pet ticks (v2 "daycare" rule).

## 8. Likely bugs / problems and mitigations

| # | Problem | Mitigation |
|---|---|---|
| 21 | Walk cycle clips on small / narrow screens | Stage bounds = measured view size; roam points clamped with pet-width margin; test on 3 densities |
| 22 | Wallpaper roam drains battery | Same gates as v2: no loop when invisible/screen-off; roam pauses after 30s idle in wallpaper |
| 23 | Personality drifts to an extreme (all-Shy, all-Greedy) | Decay toward baseline + hard clamps (10–90); nudge magnitudes capped |
| 24 | Brain cold-start feels dead (no learning yet) | Defaults + honest "still getting to know you" copy; visible learning after ~3 days |
| 25 | 108 variants × moods = QA explosion | Parametric renderer = one code path; golden-bitmap test renders all 108 and pixel-diffs against checked-in references (CI) |
| 26 | Power cooldown cheating via clock change | Cooldowns on `elapsedRealtime` (monotonic), never wall clock |
| 27 | v2 → v3 state migration corrupts a save | Schema version + migration unit test on a real v2 fixture; fallback = fresh pet with stats preserved |
| 28 | Speech lines repeat annoyingly | No-repeat window of 8 + pool size ≥ 15 per pool; weight recent lines down |
| 29 | Walk looks robotic (identical loops) | Roam pauses vary 2–6s; bob phase + blink phase randomized per pet; accessory sway |
| 30 | Low-end devices drop frames at 60fps | Frame-time budget test in CI (95th percentile < 16ms on reference); particle budget ≤ 80 (v2 rule) |
| 31 | Widget shows stale / frozen walk frame | Widget re-renders bitmap on every stat tick + on tap; documented as snapshot, not live |
| 32 | Power spam (tap power every minute) | Cooldown persisted + button disabled state rendered; no cooldown reset on reinstall (keyed to elapsedRealtime) |

## 9. UI / UX + SFX / VFX

- **Roster browser:** category tabs (Normal / Wild / Mystical), family filter,
  search, unlock-progress ring per family, tap a card → 150ms pop + preview
  (pet walks a lap in the card). Locked cards show the unlock rule, not a grey box.
- **Pet stage:** roam area with soft ground shadow, speech bubble above pet
  (auto-dismiss 3s), power button with cooldown ring, personality shortcut.
- **Personality card:** 5 trait bars + summary line + "Pip learned: …" lines
  (e.g. "Pip noticed you play every evening"). This is where the Brain is VISIBLE.
- **SFX (synthesized, zero audio files — v2 rule):** walk patter (soft ticks),
  turn hop, power chimes (one per family, 18+ new), speech pop, unlock fanfare
  (reused). All respect the mute toggle + system haptics setting.
- **VFX:** dust puffs on steps, rainbow trail, ember sparks, ink doodle stroke,
  snow flurry, sonar rings, gift sparkle. Every effect answers an event (v2 rule).
- **Accessibility:** mute toggle, reduced-motion setting (roam → gentle bob),
  content descriptions on all buttons.

## 10. Build phases + QA gates

- **Phase 1 — Roster + renderer:** `Variant.kt` (108 rows), renderer legs /
  accessories, golden-bitmap test for all 108. Gate: CI renders 108/108, zero diffs.
- **Phase 2 — Walk + personality + speech:** `WalkEngine`, roam, `Personality`,
  `SpeechBank`, Personality card UI. Gate: walk-cycle + roam unit tests pass.
- **Phase 3 — Brain + powers:** `NativeBrain`, `PowerManager`, anticipation
  behaviors, power button + cooldowns. Gate: brain unit tests with fixed seeds.
- **Phase 4 — Wallpaper / widget / premium:** roam in wallpaper, widget walk
  frame, premium aura skins for v3. Gate: battery test (no loop when invisible).
- **Phase 5 — QA + release:** full golden suite, migration test, frame-time test,
  README + screenshot refresh, CI debug APK, real-device install test on S26 Ultra.
  Test-before-claiming: no feature is listed in README until its test passes.

## 11. Honest limits (documented, not hidden)

- No photorealism: pixel art is stylized; "realistic" = believable motion (§3).
- No LLM: the Brain is an adaptive rule system (§5). It will never chat, write,
  or reason — it notices patterns and reacts to them.
- 108 variations are parametric data, not 108 hand-drawn sprites (§2).
- Wallpaper roam works while the wallpaper engine is alive; AOD still cannot be
  drawn on by third-party apps (v2 limitation, unchanged).
- Local license-key validation remains obfuscation-tier (v2 note, unchanged).
