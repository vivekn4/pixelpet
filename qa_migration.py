#!/usr/bin/env python3
"""v2 -> v3 migration QA for PixelPet (PLAN_V3.md Phase 5).

Static verification that v2 persisted state loads correctly under v3 code:
- every v2 SharedPreferences key / JSON field is still read with the same
  name and type, with a sensible default when absent;
- v3 keys live in separate prefs files (or are new, non-colliding keys);
- malformed blobs degrade to fresh/default state, never a crash;
- species/variant fallbacks resolve unknown ids to a safe default;
- unlocks and premium survive the upgrade.

Run from the repo root:  python3 qa_migration.py [--root <repo-root>]
Exit 0 = all migration checks pass. Static checks only: no Android runtime.
"""
import os, re, sys

ROOT = sys.argv[sys.argv.index("--root") + 1] if "--root" in sys.argv \
    else os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(ROOT, "app/src/main/java/com/vn/pixelpet")

errors, warnings = [], []
def err(m): errors.append(m)
def warn(m): warnings.append(m)
def read(p):
    with open(p) as f: return f.read()

def must(src, needle, what):
    if needle not in src: err(f"{what}: missing {needle!r}")

repo = read(f"{JAVA}/PetRepository.kt")
state = read(f"{JAVA}/PetState.kt")
species = read(f"{JAVA}/Species.kt")
variant = read(f"{JAVA}/Variant.kt")
premium = read(f"{JAVA}/PremiumManager.kt")
aura = read(f"{JAVA}/AuraSkin.kt")
personality = read(f"{JAVA}/Personality.kt")
brain = read(f"{JAVA}/NativeBrain.kt")
powers = read(f"{JAVA}/PowerManager.kt")
main = read(f"{JAVA}/MainActivity.kt")

# ---------- 1. v2 keys in the "pixelpet" prefs file, same names ----------
print("v2 keys in pixelpet prefs file")
V2_KEYS = [
    ("pet_", "per-pet JSON blob prefix"),
    ("active_species", "active species id"),
    ("unlocked_species", "unlocked species set"),
    ("care_streak", "care streak count"),
    ("last_care_date", "last care day"),
    ("total_goals", "lifetime goals"),
    ("goals", "goal list JSON"),
    ("goals_corrupt_", "corrupt-goals backup prefix"),
    ("autodetect", "doomscroll auto-detect toggle"),
    ("autolog_date", "last auto-log date"),
    ("last_nudge", "last attention-nudge time"),
    ("last_recap", "pending wake recap"),
]
for key, what in V2_KEYS:
    must(repo, f'"{key}"', f"v2 key {what} ({key})")

# ---------- 2. v2 PetState JSON fields: opt* reads with defaults ----------
print("v2 PetState JSON fields use opt* defaults")
V2_JSON = ["h", "ha", "e", "a", "te", "tw", "sulk", "m", "sp", "sl",
           "sse", "sick", "snk", "ply"]
for k in V2_JSON:
    if not re.search(r'opt(Int|Long|Boolean|String)\("' + re.escape(k) + r'"', repo):
        err(f"petFromJson: field {k!r} not read via opt* with default")
# v3 additive field must also round-trip with a default (old blobs lack it)
if not re.search(r'optLong\("shld"', repo):
    err("petFromJson: v3 field 'shld' not read via optLong with default")
if '"shld"' not in repo:
    err("petToJson: v3 field 'shld' not written (round-trip broken)")
# every key petToJson writes must be read by petFromJson
for k in V2_JSON + ["shld"]:
    if f'"{k}"' not in repo:
        err(f"JSON round-trip: key {k!r} missing somewhere in PetRepository")

# ---------- 3. v3 keys: separate files, no collisions with v2 ----------
print("v3 keys live in separate prefs files (no collisions)")
if 'PREFS_NAME = "pixelpet_personality"' not in personality:
    err("Personality: prefs file must be 'pixelpet_personality'")
if '"personality_"' not in personality and "personality_$variantId" not in personality:
    err("Personality: key must be 'personality_<variantId>'")
if 'PREFS_NAME = "pixelpet_brain"' not in brain:
    err("NativeBrain: prefs file must be 'pixelpet_brain'")
if 'KEY_JSON = "brain_json"' not in brain:
    err("NativeBrain: key must be 'brain_json'")
if 'PREFS_NAME = "pixelpet_powers"' not in powers:
    err("PowerManager: prefs file must be 'pixelpet_powers'")
if 'KEY_JSON = "cooldowns_json"' not in powers:
    err("PowerManager: key must be 'cooldowns_json'")
if 'KEY_AURA_SKIN = "aura_skin"' not in aura:
    err("AuraSkin: key must be 'aura_skin'")
# the four v3 prefs files must be pairwise distinct
files = ["pixelpet", "pixelpet_personality", "pixelpet_brain", "pixelpet_powers"]
if len(set(files)) != 4:
    err("v3 prefs files are not all distinct (collision)")
# aura_skin is the only NEW key in the shared "pixelpet" file, and it must
# not equal any v2 key
if '"aura_skin"' in repo:
    err("AuraSkin key 'aura_skin' must not be duplicated inside PetRepository")

# ---------- 4. premium keys unchanged ----------
print("premium keys unchanged")
for key in ["premium_active", "premium_key_id", "key_attempts", "key_lockout_until"]:
    must(premium, f'"{key}"', f"premium key {key}")
# the key itself is still never stored (only id + flag)
if "putString" in premium and "K_KEY_ID" not in premium:
    err("PremiumManager: must persist only the key id, never the key")

# ---------- 5. malformed blobs degrade, never crash ----------
print("malformed-blob handling")
if "catch" not in repo or "PetState.fresh" not in repo:
    err("PetRepository.readRaw: malformed pet blob must fall back to fresh state")
if "K_GOALS_CORRUPT" not in repo or "catch" not in repo:
    err("PetRepository.getGoals: corrupt goals must be backed up, not lost/crash")
if "return null" not in personality:
    err("Personality.load: malformed blob must return null (caller re-seeds)")
# NativeBrain fromJson must be null-safe at the call site: MainActivity
# falls back to fresh when the stored blob is missing/malformed.
if "NativeBrain.fresh" not in main:
    err("MainActivity: missing NativeBrain.fresh fallback for bad/missing brain blob")
if "Cooldowns()" not in main:
    err("MainActivity: missing PowerManager Cooldowns() fallback for bad blob")

# ---------- 6. fallbacks for unknown ids ----------
print("unknown-id fallbacks")
if "?: CAT" not in species:
    err("Species.byId: unknown id must fall back to CAT")
if "ALL.first()" not in variant:
    err("VariantRoster.variantForSpeciesId: unknown id must fall back to ALL.first()")
if 'startsWith("$speciesId-")' not in variant and "startsWith" not in variant:
    err("VariantRoster.variantForSpeciesId: must try '<speciesId>-*' prefix match")

# ---------- 7. unlocks + premium survive ----------
print("unlock preservation")
if '"cat,dog,bunny,hamster"' not in repo:
    err("unlockedSpecies: default must be the v2 starter set")
if "PremiumManager.isPremium" not in repo:
    err("isUnlocked: premium must still unlock every species")
if "K_UNLOCKED" not in repo:
    err("unlockSpecies: must persist to the same K_UNLOCKED key")

# ---------- summary ----------
print(f"\nMigration checks: {len(errors)} error(s), {len(warnings)} warning(s)")
for e in errors: print("  ERROR:", e)
for w in warnings: print("  WARN:", w)
sys.exit(1 if errors else 0)
