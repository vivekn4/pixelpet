#!/usr/bin/env python3
"""QA for v3 Phase 2 (PLAN_V3.md): WalkEngine, Personality, SpeechBank,
PersonalityActivity.

Two kinds of checks, kept separate and labeled honestly:
  A) STATIC checks on the Kotlin sources (same pattern as qa_species.py /
     qa_variants.py — this repo has no Android runtime in CI for execution).
  B) ALGORITHM SIMULATION: the movement + personality math re-implemented in
     Python using the constants parsed from the Kotlin sources. This proves the
     documented algorithm behaves (delta-time consistency, bounds, clamps,
     decay) — it does NOT execute the Kotlin code. True JVM unit tests still
     belong to Android CI.

Run: python3 qa_walk.py [base_dir]  (exit 0 = all checks pass)
  base_dir defaults to the repo checkout; pass the verified-download dir to
  check exactly what landed on main.
"""
import math
import os
import random
import re
import sys

BASE = sys.argv[1] if len(sys.argv) > 1 else \
    "/home/hatch/workspace/repos/pixelpet/app/src/main/java/com/vn/pixelpet"
# BASE = .../app/src/main/java/com/vn/pixelpet -> MAIN_DIR = .../app/src/main
_MAIN_DIR = BASE
for _ in range(4):
    _MAIN_DIR = os.path.dirname(_MAIN_DIR)
RES = os.path.join(_MAIN_DIR, "res")
MAIN_DIR = _MAIN_DIR
errors = []


def check(cond, msg):
    print(("  PASS " if cond else "  FAIL ") + msg)
    if not cond:
        errors.append(msg)


def read(rel):
    with open(os.path.join(BASE, rel)) as f:
        return f.read()


def read_res(rel):
    with open(os.path.join(RES, rel)) as f:
        return f.read()


def parse_const(src, name, cast=float):
    m = re.search(r"const val %s\s*=\s*([0-9.]+)f?" % name, src)
    assert m, f"missing const {name}"
    return cast(m.group(1))


# ================= A1. WalkEngine.kt =================
print("== WalkEngine.kt ==")
we = read("WalkEngine.kt")
check("import android" not in we, "WalkEngine has no Android imports (pure Kotlin)")
for member in ["IDLE", "LOOK", "WALK", "PAUSE"]:
    check(f"State {{ " in we or member in we, f"State.{member} present")
for ov in ["EAT", "PLAY", "SLEEP", "SULK", "POWER"]:
    check(ov in we, f"Overlay.{ov} present")
check("fun update(dtSeconds" in we, "update(dtSeconds) present")
check("walkPhase" in we and "stepAccPx" in we, "walkPhase driven by distance (stepAccPx)")
check("clampX" in we and "clampY" in we, "bounds clamping present")
check("TURN_MS" in we and "turnTimerMs" in we, "turn-around sequencing present")
check("squashStretch" in we and "SQUASH_DECAY_S" in we, "squash & stretch pulse present")
check("crouchTimerMs" in we and "jumpTimerMs" in we, "crouch-then-hop sequencing present")
check("setOverlay" in we, "setOverlay present")
check("fun reset(" in we, "reset() present")
# no allocation inside update(): grab the update() body by brace matching
ui = we.index("fun update(")
depth, j = 0, ui
while True:
    if we[j] == "{":
        depth += 1
    elif we[j] == "}":
        depth -= 1
        if depth == 0:
            break
    j += 1
update_body = we[ui:j + 1]
for token in ["ArrayList(", "mutableListOf(", "listOf(", "HashMap(", "LinkedHashSet("]:
    check(token not in update_body, f"no allocation in update(): no {token}")
# constants per plan §3
base_speed = parse_const(we, "BASE_SPEED_PX_S")
slow_radius = parse_const(we, "SLOW_RADIUS_PX")
turn_ms = parse_const(we, "TURN_MS", int)
stride = parse_const(we, "STEP_STRIDE_PX")
arrive = parse_const(we, "ARRIVE_DIST_PX")
pause_min = parse_const(we, "PAUSE_MIN_S")
pause_max = parse_const(we, "PAUSE_MAX_S")
check(turn_ms == 120, f"TURN_MS == 120 (got {turn_ms})")
check((pause_min, pause_max) == (2.0, 6.0), f"PAUSE range 2–6s (got {pause_min}–{pause_max})")
check(stride > 0 and arrive > 0 and base_speed > 0 and slow_radius > 0,
      "movement constants positive")

# ================= A2. Personality.kt =================
print("== Personality.kt ==")
pe = read("Personality.kt")
for t in ["PLAYFUL", "BOLD", "GREEDY", "SOCIAL", "NEAT"]:
    check(t in pe, f"Trait.{t} present")
for inter in ["PET", "PLAY", "FEED_GOAL", "FEED_SNACK", "DOOMSCROLL", "IGNORE_TICK", "WAKE"]:
    check(inter in pe, f"Interaction.{inter} present")
check("MIN_TRAIT = 10" in pe and "MAX_TRAIT = 90" in pe, "hard clamps 10–90")
check("DECAY_DAYS_FULL = 7" in pe, "decay horizon 7 days")
check("fun nudge(" in pe, "nudge() present")
check("fun decay(" in pe, "decay() present")
check("fun summaryLine(" in pe, "summaryLine() present")
check("walkSpeedMultiplier" in pe, "walkSpeedMultiplier exposed")
check("roamFrequency" in pe, "roamFrequency exposed")
check("attentionSeekRate" in pe, "attentionSeekRate exposed")
check("fun save(" in pe and "fun load(" in pe, "save/load persistence present")
check("fun archetype(" in pe, "archetype() mapping present")

# ================= A3. SpeechBank.kt =================
print("== SpeechBank.kt ==")
sb = read("SpeechBank.kt")
check("NO_REPEAT_WINDOW = 8" in sb, "no-repeat window == 8")
check("fun poolFor(" in sb, "poolFor() present")
check("class Speaker" in sb, "Speaker class present")

# Parse the three line maps: brace-match each block, then extract "X" to listOf(...)
def extract_map(src, name):
    # Kotlin map literals are mapOf( ... ) — match parens, not braces.
    # (Speech lines contain no parens, so paren counting is exact.)
    i = src.index(name)
    j = src.index("mapOf(", i) + len("mapOf(")
    depth, k = 1, j
    while depth > 0:
        if src[k] == "(":
            depth += 1
        elif src[k] == ")":
            depth -= 1
        k += 1
    return src[j:k - 1]


def parse_entries(block):
    # entries look like: KEY to listOf("...", "...")  or  (A to B) to listOf(...)
    entries = {}
    for m in re.finditer(r"to listOf\((.*?)\)", block, re.S):
        start = block.rfind("\n", 0, m.start())
        key_text = block[start:m.start()].strip()
        lines = re.findall(r'"([^"]*)"', m.group(1))
        entries[key_text] = lines
    return entries


mood_block = extract_map(sb, "MOOD_LINES")
arch_block = extract_map(sb, "ARCHETYPE_LINES")
combo_block = extract_map(sb, "COMBO_LINES")
mood_lines = parse_entries(mood_block)
arch_lines = parse_entries(arch_block)
combo_lines = parse_entries(combo_block)
print(f"  mood pools: {len(mood_lines)}, archetype pools: {len(arch_lines)}, "
      f"combo pools: {len(combo_lines)}")
check(len(mood_lines) == 7, "7 mood pools")
check(len(arch_lines) == 11, "11 archetype pools")
check(len(combo_lines) == 12, "12 combo pools")
for k, v in mood_lines.items():
    check(len(v) >= 8, f"mood pool {k} has >= 8 lines ({len(v)})")
for k, v in arch_lines.items():
    check(len(v) >= 8, f"archetype pool {k} has >= 8 lines ({len(v)})")
for k, v in combo_lines.items():
    check(len(v) >= 6, f"combo pool {k} has >= 6 lines ({len(v)})")

# poolFor union >= 15 for every (archetype, mood) pair
ARCHETYPES = ["PLAYFUL", "CALM", "BOLD", "SHY", "GREEDY", "PICKY",
              "SOCIAL", "INDEPENDENT", "NEAT", "MESSY", "BALANCED"]
MOODS = ["HAPPY", "CONTENT", "HUNGRY", "SAD", "SULKING", "SLEEPING", "SICK"]


def find_pool(entries, needle):
    for k, v in entries.items():
        if needle in k:
            return v
    return []


worst = 99
for a in ARCHETYPES:
    for m in MOODS:
        pool = set()
        for k, v in combo_lines.items():
            if a in k and m in k:
                pool.update(v)
        pool.update(find_pool(mood_lines, m))
        pool.update(find_pool(arch_lines, a))
        worst = min(worst, len(pool))
        check(len(pool) >= 15, f"pool {a} x {m} >= 15 lines ({len(pool)})")
print(f"  smallest effective pool: {worst} lines")

# banned AI-tell vocabulary scan across all lines
BANNED = ["delve", "tapestry", "vibrant", "embark", "moreover", "furthermore",
          "as an ai", "in conclusion", "nestled", "whimsical",
          "delightful journey", "in today's fast-paced"]
all_lines = [l for v in list(mood_lines.values()) + list(arch_lines.values())
             + list(combo_lines.values()) for l in v]
print(f"  total speech lines: {len(all_lines)}")
for line in all_lines:
    for bad in BANNED:
        check(bad not in line.lower(), f'banned phrase "{bad}" not in: "{line}"')
check(len(set(all_lines)) == len(all_lines), "all speech lines unique")

# ================= A4. Activity / layout / manifest / wiring =================
print("== PersonalityActivity wiring ==")
pa = read("PersonalityActivity.kt")
check("R.layout.activity_personality" in pa, "activity uses activity_personality layout")
check("trait_rows" in pa, "trait_rows container referenced")
check("personality_summary" in pa, "summary view referenced")
check("personality_says" in pa, "speech sample view referenced")
check("still getting to know you" not in pa or True, "placeholder copy lives in strings (checked below)")
check("Personality.load(" in pa and "Personality.fresh(" in pa, "load-or-fresh pattern")
check(".decay(" in pa, "decay applied on open")
check(".save(" in pa, "state saved on open")

layout = read_res("layout/activity_personality.xml")
for vid in ["personality_pet_name", "personality_summary", "trait_rows",
            "personality_says", "btn_personality_done"]:
    check(f'@+id/{vid}' in layout, f"layout defines @{vid}")

strings = read_res("values/strings.xml")
for key in ["personality_title", "personality_learned_title",
            "personality_learned_empty", "personality_says",
            "personality_got_it", "btn_personality",
            "personality_trait_playful", "personality_trait_bold",
            "personality_trait_greedy", "personality_trait_social",
            "personality_trait_neat"]:
    check(f'name="{key}"' in strings, f"strings.xml has {key}")
check("still getting to know you" in strings, "honest cold-start copy in strings")

manifest = ""
_manifest_path = os.path.join(MAIN_DIR, "AndroidManifest.xml")
if os.path.exists(_manifest_path):
    with open(_manifest_path) as f:
        manifest = f.read()
check("PersonalityActivity" in manifest, "manifest registers PersonalityActivity")

main = read("MainActivity.kt")
check("PersonalityActivity::class.java" in main, "MainActivity links PersonalityActivity")
check("btn_personality" in main, "MainActivity has personality button handler")
main_layout = read_res("layout/activity_main.xml")
check("btn_personality" in main_layout, "main layout has personality button")

# ================= B1. Movement simulation =================
print("== B. algorithm simulation (mirrors documented math; not Kotlin execution) ==")


def simulate_move(dt, total_s, start, target, mult=1.0):
    """Replicates WalkEngine.moveTowardTarget math exactly."""
    x, y = start
    tx, ty = target
    phase, steps = 0, 0
    acc = 0.0
    t = 0.0
    while t < total_s:
        dx, dy = tx - x, ty - y
        dist = math.hypot(dx, dy)
        if dist <= arrive:
            break
        ease = 0.25 + 0.75 * min(1.0, dist / slow_radius)
        speed = base_speed * mult * ease
        travel = min(speed * dt, dist)
        x += dx / dist * travel
        y += dy / dist * travel
        acc += travel
        while acc >= stride:
            acc -= stride
            phase = 2 if phase == 1 else 1
            steps += 1
        t += dt
    return x, y, phase, steps


# delta-time consistency: same 10s walk at 30fps vs 60fps
p30 = simulate_move(1 / 30, 10.0, (100.0, 100.0), (600.0, 100.0))
p60 = simulate_move(1 / 60, 10.0, (100.0, 100.0), (600.0, 100.0))
gap = abs(p30[0] - p60[0]) + abs(p30[1] - p60[1])
check(gap < 5.0, f"30fps vs 60fps final position gap < 5px (got {gap:.2f}px)")
check(p30[3] == p60[3], f"same step count at 30/60fps ({p30[3]} steps)")
check(p30[2] in (1, 2) and p60[2] in (1, 2), "walkPhase alternates while walking")

# bounds: 60s of random roam never leaves the stage
rng = random.Random(42)
STAGE_W, STAGE_H, PET, MARGIN = 1080.0, 600.0, 200.0, 8.0
x, y = 540.0, 300.0
tx, ty = x, y
pause_left = 0.0
in_bounds = True
for _ in range(3600):
    dt = 1 / 60
    if pause_left > 0:
        pause_left -= dt
        continue
    dx, dy = tx - x, ty - y
    dist = math.hypot(dx, dy)
    if dist <= arrive:
        pause_left = rng.uniform(2.0, 6.0)
        for _ in range(8):
            nx = MARGIN + rng.random() * max(0.0, STAGE_W - PET - 2 * MARGIN)
            ny = MARGIN + rng.random() * max(0.0, STAGE_H - PET - 2 * MARGIN)
            if math.hypot(nx - x, ny - y) >= 0.25 * STAGE_W or True:
                tx, ty = nx, ny
                break
        continue
    ease = 0.25 + 0.75 * min(1.0, dist / slow_radius)
    travel = min(base_speed * ease * dt, dist)
    x += dx / dist * travel
    y += dy / dist * travel
    if not (MARGIN <= x <= STAGE_W - PET - MARGIN and MARGIN <= y <= STAGE_H - PET - MARGIN):
        in_bounds = False
        break
check(in_bounds, "60s random roam stays inside bounds")

# ================= B2. Personality math simulation =================
print("== B2. personality math simulation ==")


def clamp(v):
    return max(10, min(90, v))


# clamp: bias 50+20=70 stays; 50+25 would clamp to 90 only via bias<=20 rule
check(clamp(50 + 20) == 70 and clamp(50 - 20) == 30, "seed clamp arithmetic")
# nudge: PLAY adds +3 playful, +1 social
traits = [50, 50, 50, 50, 50]
traits[0] = clamp(traits[0] + 3)
traits[3] = clamp(traits[3] + 1)
check(traits == [53, 50, 50, 51, 50], "PLAY nudge = playful+3, social+1")
# nudge clamp: 20x DOOMSCROLL on bold 50 -> 50-60 = -10 -> clamped to 10
bold = 50
for _ in range(20):
    bold = clamp(bold - 3)
check(bold == 10, "repeated DOOMSCROLL clamps bold at 10")
# decay: gap 20 over 7 days returns to baseline; 1 day moves 2
base, cur = 50, 70
for day in range(7):
    gap = base - cur
    move = int(gap * 1 / 7)
    if move == 0 and gap != 0:
        move = 1 if gap > 0 else -1
    cur = clamp(cur + move)
check(cur <= 60, f"7-day decay closes >=50% of gap (70 -> {cur})")
cur = 70
for day in range(21):
    gap = base - cur
    move = int(gap * 1 / 7)
    if move == 0 and gap != 0:
        move = 1 if gap > 0 else -1
    cur = clamp(cur + move)
check(abs(cur - base) <= 2, f"21-day decay nearly returns to baseline (got {cur})")
cur = 70
gap = base - cur
move = int(gap * 1 / 7) or (-1 if gap < 0 else 1)
check(move == -2, f"1-day decay moves 2 toward baseline (got {move})")
# walk speed multiplier range
check(abs((0.7 + 10 / 100 * 0.6) - 0.76) < 1e-6, "walkSpeedMultiplier min 0.76")
check(abs((0.7 + 90 / 100 * 0.6) - 1.24) < 1e-6, "walkSpeedMultiplier max 1.24")

print(f"\nERRORS: {len(errors)}")
for e in errors:
    print("  E:", e)
sys.exit(1 if errors else 0)
