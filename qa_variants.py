#!/usr/bin/env python3
"""QA for the v3 108-variant roster (PLAN_V3.md Phase 1).

Static verification, same pattern as qa_species.py (this repo has no Android
runtime in CI for pixel rendering): parses Variant.kt, checks all 108 entries
against the plan's roster, and confirms that every feature / accessory each
variant uses has a real draw branch in PetRenderer.kt — i.e. renderVariant()
can draw every variant without hitting an unhandled feature.

Run: python3 qa_variants.py  (exit 0 = all 108 variants renderable)
"""
import re
import sys

BASE = "/home/hatch/workspace/repos/pixelpet/app/src/main/java/com/vn/pixelpet"
errors = []

variant_src = open(f"{BASE}/Variant.kt").read()
species_src = open(f"{BASE}/Species.kt").read()
renderer_src = open(f"{BASE}/PetRenderer.kt").read()

# ---- expected roster, straight from PLAN_V3.md section 2 ----
EXPECTED_IDS = [
    # Normal (57)
    "cat-tabby", "cat-tuxedo", "cat-siamese", "cat-persian", "cat-bengal",
    "cat-calico", "cat-black", "cat-white", "cat-ginger", "cat-sphynx",
    "cat-maine-coon", "cat-ragdoll",
    "dog-labrador", "dog-golden", "dog-german-shepherd", "dog-pug", "dog-husky",
    "dog-corgi", "dog-beagle", "dog-poodle", "dog-bulldog", "dog-dalmatian",
    "dog-chihuahua", "dog-shiba", "dog-border-collie", "dog-dachshund",
    "dog-great-dane",
    "bunny-white", "bunny-grey", "bunny-brown", "bunny-lop", "bunny-angora",
    "hamster-golden", "hamster-panda", "hamster-robo", "hamster-winter-white",
    "pig-pink", "pig-spotted", "pig-mini", "pig-mud", "pig-wild-boar", "pig-piglet",
    "duck-yellow", "duck-mallard", "duck-white",
    "turtle-green", "turtle-red-eared", "turtle-tortoise",
    "frog-green", "frog-tree", "frog-poison-dart",
    "penguin-emperor", "penguin-rockhopper", "penguin-baby",
    "panda-giant", "panda-red", "panda-baby",
    # Wild (27)
    "wolf-grey", "wolf-arctic", "wolf-black", "wolf-timber",
    "fox-red", "fox-arctic", "fox-fennec", "fox-silver",
    "tiger-bengal", "tiger-white", "tiger-siberian", "tiger-cub",
    "owl-barn", "owl-snowy", "owl-horned", "owl-tawny",
    "ox-ox", "ox-bison", "ox-buffalo", "ox-cow", "ox-yak", "ox-highland",
    "snake-green", "snake-python", "snake-coral",
    "bat-fruit", "bat-vampire",
    # Mystical (24)
    "dragon-fire", "dragon-ice", "dragon-storm", "dragon-forest",
    "dragon-shadow", "dragon-gold", "dragon-water", "dragon-ember",
    "dragon-crystal", "dragon-baby",
    "phoenix-flame", "phoenix-ember", "phoenix-dawn",
    "unicorn-white", "unicorn-starlight", "unicorn-rainbow",
    "kraken-teal", "kraken-deep-sea", "kraken-baby",
    "axolotl-pink", "axolotl-golden",
    "griffin-gold", "griffin-storm", "griffin-forest",
]
EXPECTED_FAMILY_COUNTS = {
    "CAT": 12, "DOG": 15, "BUNNY": 5, "HAMSTER": 4, "PIG": 6, "DUCK": 3,
    "TURTLE": 3, "FROG": 3, "PENGUIN": 3, "PANDA": 3,
    "WOLF": 4, "FOX": 4, "TIGER": 4, "OWL": 4, "OX_KIN": 6, "SNAKE": 3, "BAT": 2,
    "DRAGON": 10, "PHOENIX": 3, "UNICORN": 3, "KRAKEN": 3, "AXOLOTL": 2,
    "GRIFFIN": 3,
}

# ---- 1. parse Variant.kt entries ----
entry_re = re.compile(
    r'Variant\("([a-z-]+)",\s*"([^"]+)",\s*Fam\.(\w+),\s*C\.(\w+),\s*'
    r'"(#[0-9A-Fa-f]{6})",\s*"(#[0-9A-Fa-f]{6})",\s*'
    r'setOf\(([^)]*)\),\s*A\.(\w+),\s*'
    r'TraitBias\(([^)]*)\),\s*"([^"]+)"'
)
entries = entry_re.findall(variant_src)
print(f"variant entries: {len(entries)}")
if len(entries) != 108:
    errors.append(f"expected 108 variants, found {len(entries)}")

ids = [e[0] for e in entries]
if len(set(ids)) != len(ids):
    errors.append(f"duplicate variant ids: {[i for i in ids if ids.count(i) > 1]}")
if set(ids) != set(EXPECTED_IDS):
    errors.append(f"id mismatch vs PLAN_V3: missing={set(EXPECTED_IDS) - set(ids)} "
                  f"extra={set(ids) - set(EXPECTED_IDS)}")

# ---- 2. category + family counts ----
from collections import Counter
cats = Counter(e[3] for e in entries)
print(f"  Normal={cats.get('NORMAL', 0)} Wild={cats.get('WILD', 0)} "
      f"Mystical={cats.get('MYSTICAL', 0)}")
if (cats.get("NORMAL"), cats.get("WILD"), cats.get("MYSTICAL")) != (57, 27, 24):
    errors.append(f"category counts != 57/27/24: {dict(cats)}")
fams = Counter(e[2] for e in entries)
bad_fams = {f: (fams.get(f, 0), n) for f, n in EXPECTED_FAMILY_COUNTS.items()
            if fams.get(f, 0) != n}
if bad_fams:
    errors.append(f"family counts mismatch (found, expected): {bad_fams}")

# ---- 3. palettes, accessories, trait bias, unlock text ----
enum_accessories = set(re.findall(r'enum class Accessory \{([^}]*)\}', variant_src, re.S))
enum_accessories = set(re.findall(r'[A-Z_]+', re.sub(r'//.*', '', list(enum_accessories)[0]))) if enum_accessories else set()
for e in entries:
    if e[7] not in enum_accessories:
        errors.append(f"{e[0]}: accessory {e[7]} not in Accessory enum")
    for kv in re.findall(r'(playful|bold|greedy|social|neat)\s*=\s*(-?\d+)', e[8]):
        if not -20 <= int(kv[1]) <= 20:
            errors.append(f"{e[0]}: trait {kv[0]}={kv[1]} outside -20..20")
    if not e[9].strip():
        errors.append(f"{e[0]}: empty unlock text")

# ---- 4. features: exist in Species.Feature enum AND drawn in PetRenderer ----
enum_features = set(re.findall(r'enum class Feature \{([^}]*)\}', species_src, re.S))
enum_features = set(re.findall(r'[A-Z_]+', re.sub(r'//.*', '', list(enum_features)[0]))) if enum_features else set()
used_features = set()
for e in entries:
    used_features.update(re.findall(r'F\.([A-Z_]+)', e[6]))
unknown = used_features - enum_features
if unknown:
    errors.append(f"features used but not in Species.Feature enum: {unknown}")
undrawn = [f for f in used_features if f"Feature.{f}" not in renderer_src]
if undrawn:
    errors.append(f"features never referenced in PetRenderer: {undrawn}")
print(f"features: {len(enum_features)} defined, {len(used_features)} used, "
      f"{len(used_features) - len(undrawn)} drawn")

# ---- 5. accessories: drawn in PetRenderer ----
used_accessories = {e[7] for e in entries}
undrawn_acc = [a for a in used_accessories
               if f"Accessory.{a}" not in renderer_src]
if undrawn_acc:
    errors.append(f"accessories never referenced in PetRenderer: {undrawn_acc}")
print(f"accessories: {len(enum_accessories)} defined, {len(used_accessories)} used, "
      f"{len(used_accessories) - len(undrawn_acc)} drawn")

# ---- 6. renderer entry points ----
for token in ["fun renderVariant", "walkPhase", "legs: Boolean", "accessory: VariantRoster.Accessory"]:
    if token not in renderer_src:
        errors.append(f"PetRenderer missing: {token}")

# ---- 7. render simulation: every variant's feature set + accessory has a draw branch ----
rendered = 0
for e in entries:
    feats = re.findall(r'F\.([A-Z_]+)', e[6])
    if all(f"Feature.{f}" in renderer_src for f in feats) \
            and f"Accessory.{e[7]}" in renderer_src:
        rendered += 1
    else:
        errors.append(f"{e[0]}: missing draw branch")
print(f"rendered (static branch coverage): {rendered}/108 variants")

print(f"\nERRORS: {len(errors)}")
for e in errors:
    print("  E:", e)
sys.exit(1 if errors else 0)
