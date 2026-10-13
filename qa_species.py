#!/usr/bin/env python3
"""QA for the v2 species roster: verifies Species.kt defines exactly the 12
speciesspec'd in PLAN.md with unique ids, valid palettes/categories, and that
every Feature referenced exists in the Feature enum and is drawn in PetRenderer.
"""
import re
import sys

BASE = "/home/hatch/workspace/repos/pixelpet/app/src/main/java/com/vn/pixelpet"
errors = []

species_src = open(f"{BASE}/Species.kt").read()
renderer_src = open(f"{BASE}/PetRenderer.kt").read()

# 1. enum entries: ID("id", "Name", Category.X, "#hex", "#hex", setOf(...), "unlock")
entries = re.findall(r'(\w+)\("([a-z]+)",\s*"([^"]+)",\s*Category\.(\w+),\s*"(#[0-9A-Fa-f]{6})",\s*"(#[0-9A-Fa-f]{6})",\s*setOf\(([^)]*)\),\s*"([^"]+)"', species_src)
print(f"species entries: {len(entries)}")
if len(entries) != 12:
    errors.append(f"expected 12 species, found {len(entries)}")

ids = [e[1] for e in entries]
if len(set(ids)) != len(ids):
    errors.append(f"duplicate species ids: {ids}")
expected_ids = {"cat", "dog", "bunny", "hamster", "wolf", "fox", "tiger", "owl",
                "dragon", "phoenix", "unicorn", "kraken"}
if set(ids) != expected_ids:
    errors.append(f"id mismatch: missing={expected_ids - set(ids)} extra={set(ids) - expected_ids}")

cats = {e[3] for e in entries}
if cats != {"NORMAL", "WILD", "MYSTICAL"}:
    errors.append(f"categories wrong: {cats}")
by_cat = {}
for e in entries:
    by_cat.setdefault(e[3], []).append(e[1])
print(f"  Normal={len(by_cat.get('NORMAL', []))} Wild={len(by_cat.get('WILD', []))} Mystical={len(by_cat.get('MYSTICAL', []))}")
if not (len(by_cat.get("NORMAL", [])) == 4 and len(by_cat.get("WILD", [])) == 4 and len(by_cat.get("MYSTICAL", [])) == 4):
    errors.append("category counts != 4/4/4")

# 2. features referenced exist in the Feature enum
enum_features = set(re.findall(r'enum class Feature \{([^}]*)\}', species_src, re.S))
enum_features = set(re.findall(r'[A-Z_]+', list(enum_features)[0])) if enum_features else set()
used = set()
for e in entries:
    used.update(re.findall(r'Feature\.([A-Z_]+)', e[6]))
unknown = used - enum_features
if unknown:
    errors.append(f"features used but not in enum: {unknown}")
print(f"features: {len(enum_features)} defined, {len(used)} used")

# 3. renderer handles each feature (drawn somewhere)
undrawn = [f for f in used if f"Feature.{f}" not in renderer_src]
if undrawn:
    errors.append(f"features never referenced in PetRenderer: {undrawn}")

# 4. unlock texts present
for e in entries:
    if not e[7].strip():
        errors.append(f"{e[1]}: empty unlock text")

# 5. mood coverage in renderer
for m in ["HAPPY", "CONTENT", "HUNGRY", "SAD", "SULKING", "SLEEPING", "SICK"]:
    if f"Mood.{m}" not in renderer_src:
        errors.append(f"Mood.{m} not handled in PetRenderer")

print(f"\nERRORS: {len(errors)}")
for e in errors:
    print("  E:", e)
sys.exit(1 if errors else 0)
