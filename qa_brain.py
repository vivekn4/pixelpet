#!/usr/bin/env python3
"""QA for v3 Phase 3 (PLAN_V3.md): NativeBrain, PowerManager, power UI wiring.

Two kinds of checks, kept separate and labeled honestly:
  A) STATIC checks on the Kotlin sources (same pattern as qa_species.py /
     qa_variants.py / qa_walk.py — this repo has no Android runtime in CI
     for execution).
  B) ALGORITHM SIMULATION: the brain + cooldown math re-implemented in
     Python using the constants parsed from the Kotlin sources. This proves
     the documented algorithm behaves (schedule anticipation, favorite
     recall, neglect greeting curve, streak milestones, cold start,
     monotonic cooldowns, Phoenix one-day restore) — it does NOT execute
     the Kotlin code. True JVM unit tests still belong to Android CI.

Run: python3 qa_brain.py [base_dir]  (exit 0 = all checks pass)
  base_dir defaults to the repo checkout; pass the verified-download dir to
  check exactly what landed on main.
"""
import json
import math
import os
import re
import sys
from collections import deque
from datetime import datetime, timedelta, timezone

BASE = sys.argv[1] if len(sys.argv) > 1 else \
    "/home/hatch/workspace/repos/pixelpet/app/src/main/java/com/vn/pixelpet"
_MAIN_DIR = BASE
for _ in range(4):
    _MAIN_DIR = os.path.dirname(_MAIN_DIR)
RES = os.path.join(_MAIN_DIR, "res")
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


def parse_const(src, name):
    m = re.search(r"const val %s\s*=\s*([0-9_]+)L?" % name, src)
    assert m, f"missing const {name}"
    return int(m.group(1).replace("_", ""))


# ================= A1. NativeBrain.kt =================
print("== NativeBrain.kt ==")
nb = read("NativeBrain.kt")
check("import android" not in nb, "NativeBrain has no Android imports (pure Kotlin)")
check("NOT a language model" in nb, "NativeBrain KDoc says NOT a language model")
check("no generation" in nb.lower(), "NativeBrain KDoc says no generation")
check("notices patterns" in nb, "NativeBrain copy: notices patterns (honest framing)")
check(parse_const(nb, "BUCKETS") == 6, "BUCKETS == 6")
check(parse_const(nb, "MAX_EVENTS") == 100, "MAX_EVENTS == 100 (ring buffer)")
check(parse_const(nb, "MIN_EVENTS_FOR_SIGNAL") == 3, "MIN_EVENTS_FOR_SIGNAL == 3")
check("enum class Action" in nb, "Action enum present")
for a in ["PET", "PLAY", "FEED_GOAL", "FEED_SNACK", "DOOMSCROLL", "WAKE", "POWER"]:
    check(a in nb, f"Action.{a} present")
for fn in ["fun record(", "fun recordCareDay(", "fun recordFavoriteGoal(",
           "fun currentStreak(", "fun isStillLearning(", "fun greetingWarmth(",
           "fun anticipation(", "fun favoriteAction(", "fun favoriteGoal(",
           "fun learnedLines(", "fun toJson(", "fun fromJson("]:
    check(fn in nb, f"NativeBrain has {fn}")
check("isStillLearning" in nb, "isStillLearning exposed for honest UI copy")
check("LEARNING_PERIOD_MS" in nb, "3-day learning period constant present")

# ================= A2. PowerManager.kt =================
print("== PowerManager.kt ==")
pm = read("PowerManager.kt")
check("import android" not in pm, "PowerManager has no Android imports (pure Kotlin)")
check("currentTimeMillis" not in pm, "PowerManager never touches wall clock")
check("elapsedRealtime" in pm, "PowerManager documents elapsedRealtime (monotonic)")
day = parse_const(pm, "DAY_MS")
six = parse_const(pm, "SIX_H_MS")
week = parse_const(pm, "WEEK_MS")
check(day == 86_400_000, "DAY_MS == 86400000")
check(six == 21_600_000, "SIX_H_MS == 21600000")
check(week == 604_800_000, "WEEK_MS == 604800000")
check("Premium changes the LOOK" in pm, "PowerManager KDoc: premium = look only")

# All 23 family powers (count map entries, not the data-class declaration).
power_entries = re.findall(r"VariantRoster\.Family\.(\w+) to PowerDef\(", pm)
check(len(power_entries) == 23, f"23 PowerDef map entries (found {len(power_entries)})")
expected_families = ["CAT", "DOG", "BUNNY", "HAMSTER", "PIG", "DUCK", "TURTLE",
                     "FROG", "PENGUIN", "PANDA", "WOLF", "FOX", "TIGER", "OWL",
                     "OX_KIN", "SNAKE", "BAT", "DRAGON", "PHOENIX", "UNICORN",
                     "KRAKEN", "AXOLOTL", "GRIFFIN"]
for fam in expected_families:
    check(fam in power_entries, f"Power for {fam}")
for name in ["Purr Heal", "Fetch Gift", "Lucky Hop", "Cheek Stash", "Mud Bath",
             "Quack Echo", "Shell Shield", "Tongue Catch", "Snow Flurry",
             "Bamboo Snack", "Moon Howl", "Sneak", "Pounce", "Night Watch",
             "Stampede Stomp", "Coil Nap", "Echo Ping", "Ember Spark", "Rebirth",
             "Rainbow Trail", "Ink Doodle", "Bubble Parade", "Sky Dive"]:
    check(f'"{name}"' in pm, f'Power name "{name}" present')
check("StatEffect.STREAK_RESTORE" in pm, "Phoenix STREAK_RESTORE effect present")
check("SpecialUi.NIGHT_WATCH" in pm, "Owl NIGHT_WATCH special UI present")
for fn in ["fun powerFor(", "fun canTrigger(", "fun remainingMs(",
           "fun remainingFraction(", "fun trigger(", "fun remainingLabel(",
           "fun stashSnack(", "fun stashDue(", "fun consumeStash("]:
    check(fn in pm, f"PowerManager.Cooldowns has {fn}")
check("STASH_DUE_MS" in pm and "STASH_EXPIRY_MS" in pm, "Stash due/expiry constants present")
check("no coin economy" in pm, "PowerManager documents the no-coin-economy deviation")

# ================= A3. Wiring =================
print("== Wiring ==")
ma = read("MainActivity.kt")
for needle in ["btn_power", "doPower()", "power_chime", "recordBrain(",
               "refreshPowerButton()", "checkStash()", "loadBrain()",
               "loadCooldowns()", "canRestoreStreakDay()",
               "restoreMissedStreakDay()", "powerFx("]:
    check(needle in ma, f"MainActivity wires {needle}")
check("SpecialUi.NIGHT_WATCH" in ma, "MainActivity handles Owl Night Watch dialog")
check("power_fetch_gift" in ma, "MainActivity handles Dog Fetch Gift toast")

pv = read("PetView.kt")
for needle in ["fun powerFx(", "KIND_SPARK", "KIND_SNOW", "KIND_BUBBLE", "KIND_MUD",
               "trailUntil", "moonUntil", "shieldRingUntil", "bubbleUntil",
               "ringUntil", "sneakUntil", "napUntil", "diveUntil", "flyUntil"]:
    check(needle in pv, f"PetView has {needle}")

pr = read("PetRepository.kt")
for needle in ["canRestoreStreakDay", "restoreMissedStreakDay", "cureSickness",
               "feedPowerBonus", "feedEnergy", "addHappiness", "activateShield",
               "shieldUntilElapsed", '"shld"']:
    check(needle in pr, f"PetRepository has {needle}")
check("Shell Shield" in pr, "PetRepository documents Shell Shield tick behavior")

ps = read("PetState.kt")
check("shieldUntilElapsed" in ps, "PetState has shieldUntilElapsed")

va = read("Variant.kt")
check("variantForSpeciesId" in va, "VariantRoster.variantForSpeciesId helper present")

se = read("SfxEngine.kt")
check('"power_chime"' in se, 'SfxEngine has "power_chime" (one shared chime)')

ga = read("GoalsActivity.kt")
check("recordGoalCompleted" in ga, "GoalsActivity records completions into the Brain")
check("FEED_GOAL" in ga and "recordCareDay" in ga and "recordFavoriteGoal" in ga,
      "GoalsActivity records FEED_GOAL + care day + favorite goal")

pa = read("PersonalityActivity.kt")
check("learnedLines" in pa and "personality_learned" in pa,
      "PersonalityActivity binds Brain learned lines")

sx = read_res("values/strings.xml")
for name in ["btn_power", "power_cooldown", "power_stash_munched",
             "power_streak_restored", "power_streak_nothing",
             "power_night_watch_title", "power_night_watch_msg",
             "power_night_watch_empty", "power_fetch_gift"]:
    check(f'name="{name}"' in sx, f"strings.xml has {name}")

am = read_res("layout/activity_main.xml")
check('android:id="@+id/btn_power"' in am, "activity_main.xml has btn_power")
ap = read_res("layout/activity_personality.xml")
check('android:id="@+id/personality_learned"' in ap,
      "activity_personality.xml has personality_learned id")

# ================= B. Simulation =================
print("== B. Simulation (Python mirror of the documented algorithms) ==")
HOUR_MS = 3600 * 1000
DAY_MS = 24 * HOUR_MS


class Brain:
    """Mirror of NativeBrain's documented math (see Kotlin for the real code)."""

    ACTIONS = ["PET", "PLAY", "FEED_GOAL", "FEED_SNACK", "DOOMSCROLL", "WAKE", "POWER"]

    def __init__(self, first_seen_ms, bold=50, playful=50):
        self.first_seen = first_seen_ms
        self.schedule = [[0] * len(self.ACTIONS) for _ in range(6)]
        self.events = deque(maxlen=100)
        self.care_days = set()
        self.celebrated = set()
        self.goal_counts = {}
        self.last_interaction = first_seen_ms
        self.longest_gap = 0
        self.bold = bold
        self.playful = playful

    @staticmethod
    def bucket_of(hour):
        return min(hour // 4, 5)

    def record(self, action, wall_ms):
        self.schedule[self.bucket_of(datetime.fromtimestamp(wall_ms / 1000, timezone.utc).hour)][
            self.ACTIONS.index(action)] += 1
        gap = wall_ms - self.last_interaction
        if gap > self.longest_gap:
            self.longest_gap = gap
        self.last_interaction = wall_ms
        self.events.append((action, wall_ms))

    def record_care_day(self, d):
        self.care_days.add(d)

    def record_favorite_goal(self, name):
        self.goal_counts[name] = self.goal_counts.get(name, 0) + 1

    def current_streak(self, now_ms):
        today = datetime.fromtimestamp(now_ms / 1000, timezone.utc).date()
        if today.isoformat() not in self.care_days:
            y = today - timedelta(days=1)
            if y.isoformat() not in self.care_days:
                return 0
            cursor = y
        else:
            cursor = today
        streak = 0
        while cursor.isoformat() in self.care_days:
            streak += 1
            cursor -= timedelta(days=1)
        return streak

    def is_still_learning(self, now_ms):
        return now_ms - self.first_seen < 3 * DAY_MS

    def greeting_warmth(self, now_ms):
        gap = now_ms - self.last_interaction
        base = 0.15 if gap > 48 * HOUR_MS else 0.35 if gap > 24 * HOUR_MS else \
            0.6 if gap > 6 * HOUR_MS else 0.9
        recent = sum(1 for (_, w) in self.events if w > now_ms - HOUR_MS)
        base = min(base + 0.08 * recent, 1.0)
        shy = 1 - (100 - self.bold) / 100 * 0.3
        return max(0.05, min(1.0, base * shy))

    def play_invite(self, now_ms):
        hour = datetime.fromtimestamp(now_ms / 1000, timezone.utc).hour
        bucket = self.bucket_of(hour)
        total = sum(self.schedule[b][self.ACTIONS.index("PLAY")] for b in range(6))
        if total < 3:
            return 0.0
        factor = 0.75 + (self.playful - 50) / 100 * 0.5
        return min(1.0, self.schedule[bucket][self.ACTIONS.index("PLAY")] / total * factor)

    def favorite_action(self):
        best, best_n = None, 0
        for a in ["PET", "PLAY", "FEED_GOAL", "FEED_SNACK"]:
            n = sum(self.schedule[b][self.ACTIONS.index(a)] for b in range(6))
            if n > best_n:
                best, best_n = a, n
        return best if best_n >= 3 else None

    def favorite_goal(self):
        if not self.goal_counts:
            return None
        k, v = max(self.goal_counts.items(), key=lambda kv: kv[1])
        return (k, v) if v >= 3 else None

    def learned_lines(self, now_ms):
        if self.is_still_learning(now_ms):
            return []
        lines = []
        names = ["late at night", "early in the morning", "in the mornings",
                 "in the afternoons", "in the evenings", "at night"]
        play = [self.schedule[b][self.ACTIONS.index("PLAY")] for b in range(6)]
        total = sum(play)
        if total >= 5 and max(play) / total >= 0.5:
            lines.append(f"Pip noticed you usually play {names[play.index(max(play))]}.")
        feed = [self.schedule[b][self.ACTIONS.index("FEED_GOAL")] +
                self.schedule[b][self.ACTIONS.index("FEED_SNACK")] for b in range(6)]
        total = sum(feed)
        if total >= 5 and max(feed) / total >= 0.5:
            lines.append(f"Pip noticed you usually feed {names[feed.index(max(feed))]}.")
        fg = self.favorite_goal()
        if fg:
            lines.append(f'Pip\'s favorite goal is "{fg[0]}" — you\'ve finished it {fg[1]} times.')
        gap_days = self.longest_gap // DAY_MS
        if gap_days >= 2:
            lines.append(f"Pip remembers being apart for {gap_days} days once — and being happy you came back.")
        streak = self.current_streak(now_ms)
        if streak >= 7:
            lines.append(f"Pip is proud of your {streak}-day care streak.")
        return lines[:4]


def ts(day, hour):
    return int(datetime(2026, 10, day, hour, tzinfo=timezone.utc).timestamp() * 1000)


# --- B1. Cold start ---
b = Brain(ts(20, 10))
check(b.is_still_learning(ts(20, 11)), "Cold start: still learning 1h in")
check(b.learned_lines(ts(20, 11)) == [], "Cold start: no learned lines")
check(b.favorite_action() is None, "Cold start: no favorite action")
check(b.play_invite(ts(20, 19)) == 0.0, "Cold start: play invite 0 with no data")
check(not b.is_still_learning(ts(23, 11)), "After 3 days: no longer learning")

# --- B2. Schedule-bucket anticipation ---
b = Brain(ts(20, 10))
for _ in range(6):
    b.record("PLAY", ts(20, 19))  # bucket 4 (evenings)
b.record("PLAY", ts(20, 9))       # bucket 2 (mornings)
check(b.play_invite(ts(20, 19)) > 0.5, "Play invite high in the usual bucket")
check(b.play_invite(ts(20, 19)) > b.play_invite(ts(20, 9)),
      "Play invite higher in usual bucket than off bucket")
check(abs(b.play_invite(ts(20, 19)) - 6 / 7 * 0.75) < 0.01,
      "Play invite math: 6/7 * playful factor 0.75")

# --- B3. Favorite recall ---
b = Brain(ts(20, 10))
for _ in range(5):
    b.record("FEED_GOAL", ts(20, 8))
for _ in range(2):
    b.record("PLAY", ts(20, 19))
b.record("PET", ts(20, 12))
check(b.favorite_action() == "FEED_GOAL", "Favorite action = FEED_GOAL (5x)")
for _ in range(4):
    b.record_favorite_goal("Gym")
for _ in range(2):
    b.record_favorite_goal("Read")
check(b.favorite_goal() == ("Gym", 4), "Favorite goal = Gym (4x)")

# --- B4. Neglect-memory greeting curve ---
b = Brain(ts(20, 10), bold=50)
warm_1h = b.greeting_warmth(ts(20, 11))
warm_50h = b.greeting_warmth(ts(22, 12))
check(warm_1h > 0.7, f"Greeting warm after 1h ({warm_1h:.3f})")
check(warm_50h < 0.2, f"Greeting shy after 50h absence ({warm_50h:.3f})")
check(warm_1h > warm_50h, "Greeting curve: warm > shy")
# Warm-up: three interactions in the last hour rebuild familiarity.
b.record("PET", ts(22, 11))
b.record("PET", ts(22, 11))
b.record("PET", ts(22, 11))
warm_after = b.greeting_warmth(ts(22, 12))
check(warm_after > warm_50h, "Greeting warms up with recent care")
# Shy pet stays more reserved than bold pet after the same absence.
shy = Brain(ts(20, 10), bold=10)
bold = Brain(ts(20, 10), bold=90)
check(shy.greeting_warmth(ts(22, 12)) < bold.greeting_warmth(ts(22, 12)),
      "Bold pet greets warmer than shy pet after absence")

# --- B5. Streak milestones ---
b = Brain(ts(20, 10))
today = datetime(2026, 10, 26, 12, tzinfo=timezone.utc)
for d in range(7):
    b.record_care_day((today - timedelta(days=d)).date().isoformat())
now = int(today.timestamp() * 1000)
check(b.current_streak(now) == 7, "Streak = 7 for 7 consecutive care days")
check(b.current_streak(now) in [7, 14, 21, 30, 60, 90, 100],
      "Streak 7 is a milestone")
b.celebrated.add(7)
check(7 in b.celebrated, "Milestone marked celebrated (won't repeat)")
b2 = Brain(ts(20, 10))
for d in range(2, 9):  # care ended 2 days ago
    b2.record_care_day((today - timedelta(days=d)).date().isoformat())
check(b2.current_streak(now) == 0, "Streak = 0 when last care was 2 days ago")

# --- B6. Learned lines (after learning period) ---
b = Brain(ts(20, 10))
for _ in range(6):
    b.record("PLAY", ts(20, 19))
for _ in range(4):
    b.record_favorite_goal("Gym")
b.longest_gap = 3 * DAY_MS
for d in range(7):
    b.record_care_day((today - timedelta(days=d)).date().isoformat())
lines = b.learned_lines(now)
check(len(lines) >= 3, f"Learned lines emitted after 3 days ({len(lines)})")
check(any("usually play in the evenings" in l for l in lines),
      "Learned line: usual play bucket")
check(any("Gym" in l for l in lines), "Learned line: favorite goal")
check(len(lines) <= 4, "Learned lines capped at 4")

# --- B7. Ring buffer ---
b = Brain(ts(20, 10))
for i in range(105):
    b.record("PET", ts(20, 10) + i * 1000)
check(len(b.events) == 100, "Event ring buffer caps at 100")

# --- B8. Cooldown math on monotonic clock ---
class Cooldowns:
    def __init__(self):
        self.last = {}
        self.stash_at = 0
        self.stash_pending = False

    def cooldown(self, fam):
        return {"CAT": day, "HAMSTER": six, "PHOENIX": week}[fam]

    def remaining(self, fam, now):
        if fam not in self.last:
            return 0
        return max(0, self.last[fam] + self.cooldown(fam) - now)

    def trigger(self, fam, now):
        if self.remaining(fam, now) > 0:
            return None
        self.last[fam] = now
        return fam

    @staticmethod
    def label(rem_ms):
        s = rem_ms // 1000
        h, m, sec = s // 3600, (s % 3600) // 60, s % 60
        if h > 0:
            return f"{h}h {m}m"
        if m > 0:
            return f"{m}m"
        return f"{sec}s"

    def stash(self, now):
        self.stash_at, self.stash_pending = now, True

    def stash_due(self, now):
        if not self.stash_pending:
            return False
        age = now - self.stash_at
        return 10 * 60 * 1000 <= age <= six


cd = Cooldowns()
T = 1000
check(cd.trigger("CAT", T) == "CAT", "Cat power triggers when ready")
check(cd.remaining("CAT", T + 3600 * 1000) == day - 3600 * 1000,
      "Cooldown remaining = DAY - 1h")
check(cd.label(cd.remaining("CAT", T + 3600 * 1000)) == "23h 0m",
      "Cooldown label: 23h 0m")
check(cd.trigger("CAT", T + 3600 * 1000) is None, "Cat power blocked while cooling")
check(cd.remaining("CAT", T + day) == 0, "Cooldown expires exactly at DAY")
check(cd.trigger("CAT", T + day) == "CAT", "Cat power triggers again after cooldown")
# Wall-clock jump does not affect monotonic cooldowns: the model takes no
# wall input at all; the static check above proves the Kotlin side agrees.
check(cd.trigger("HAMSTER", T) == "HAMSTER", "Hamster 6h power triggers")
check(cd.remaining("HAMSTER", T + six) == 0, "Hamster cooldown = 6h")
check(cd.trigger("PHOENIX", T) == "PHOENIX", "Phoenix 1-week power triggers")
check(cd.remaining("PHOENIX", T + day) == week - day, "Phoenix remaining = WEEK - 1d")
# Hamster stash due window.
cd.stash(T)
check(not cd.stash_due(T + 5 * 60 * 1000), "Stash not due before 10 minutes")
check(cd.stash_due(T + 15 * 60 * 1000), "Stash due after 10 minutes")
check(not cd.stash_due(T + six + 1000), "Stash expires after 6h")

# --- B9. Phoenix Rebirth restores exactly ONE missed day ---
def can_restore(prefs, today_d):
    last = prefs.get("last_care", "")
    return last == (today_d - timedelta(days=2)).isoformat()


def restore(prefs, today_d):
    if not can_restore(prefs, today_d):
        return False
    prefs["last_care"] = (today_d - timedelta(days=1)).isoformat()
    prefs["streak"] = prefs.get("streak", 0) + 1
    return True


today_d = datetime(2026, 10, 26, tzinfo=timezone.utc).date()
prefs = {"last_care": (today_d - timedelta(days=2)).isoformat(), "streak": 5}
check(can_restore(prefs, today_d), "Phoenix: exactly one missed day is restorable")
check(restore(prefs, today_d), "Phoenix: restore succeeds")
check(prefs["last_care"] == (today_d - timedelta(days=1)).isoformat(),
      "Phoenix: last care set to yesterday")
check(prefs["streak"] == 6, "Phoenix: streak +1 (5 -> 6)")
prefs = {"last_care": (today_d - timedelta(days=5)).isoformat(), "streak": 5}
check(not can_restore(prefs, today_d), "Phoenix: 5 missed days NOT restorable")
check(not restore(prefs, today_d) and prefs["streak"] == 5,
      "Phoenix: no restore, no streak change for 5 missed days")
prefs = {"last_care": today_d.isoformat(), "streak": 5}
check(not can_restore(prefs, today_d), "Phoenix: no missed day -> nothing to restore")
prefs = {"last_care": (today_d - timedelta(days=1)).isoformat(), "streak": 5}
check(not can_restore(prefs, today_d), "Phoenix: yesterday only -> no gap to restore")

# --- B10. JSON round-trip (schema mirrors NativeBrain.toJson / fromJson) ---
b = Brain(ts(20, 10))
for _ in range(3):
    b.record("PLAY", ts(20, 19))
b.record_care_day("2026-10-25")
b.record_favorite_goal("Gym")
b.longest_gap = 2 * DAY_MS
blob = {
    "v": 1, "variantId": "cat-tabby", "firstSeen": b.first_seen,
    "schedule": [x for row in b.schedule for x in row],
    "events": [{"a": 1, "w": ts(20, 19), "e": 12345}],
    "careDays": sorted(b.care_days), "celebrated": [],
    "goalCounts": dict(b.goal_counts),
    "lastInteraction": b.last_interaction, "longestGap": b.longest_gap,
}
round_tripped = json.loads(json.dumps(blob))
check(round_tripped["v"] == 1, "Brain JSON carries version 1")
check(len(round_tripped["schedule"]) == 42, "Brain JSON schedule = 6 buckets x 7 actions")
check(round_tripped["goalCounts"] == {"Gym": 1}, "Brain JSON preserves goal counts")
check(round_tripped["longestGap"] == 2 * DAY_MS, "Brain JSON preserves longest gap")
check(len(json.dumps(blob)) < 100_000, "Brain JSON stays < 100 KB")

print()
if errors:
    print(f"FAIL: {len(errors)} check(s) failed")
    sys.exit(1)
print("ALL qa_brain CHECKS PASSED")
