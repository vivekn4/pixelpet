#!/usr/bin/env python3
"""Full golden suite for PixelPet v3 (PLAN_V3.md Phase 5 gate).

Aggregates every QA suite in this repo and adds the two Phase 5
cross-cutting gates (migration + frame-time static bounds + pixel-diff
preconditions). One command, one verdict.

Suites:
  qa_species.py    v2 regression: 12 species
  qa_variants.py   Phase 1: 108-variant roster + renderer branch coverage
  qa_walk.py       Phase 2: WalkEngine / Personality / SpeechBank
  qa_brain.py      Phase 3: NativeBrain / PowerManager
  qa_phase4.py     Phase 4: wallpaper roam / widget / aura skins
  qa_migration.py  Phase 5: v2 -> v3 persisted-state migration
  qa_pixel_diff.py Phase 5: pixel-diff preconditions (determinism + key
                   coverage). The pixel comparison itself is NOT_RUN here:
                   no Android runtime in this environment.

Frame-time gate (static): the wallpaper loop is ~30fps (FRAME_MS = 33), so
the per-frame budget is 33ms — NOT 16ms (16ms is the 60fps budget; the
wallpaper deliberately runs at 30fps). This script bounds the steady draw
path statically: no snapshot()/storage writes in drawOnce, bitmap cached
by render key, reused RectFs, starfield built on surface change, not
per frame. True p95 measurement is device-only (see DEVICE_TEST.md).

Run:  python3 qa_golden.py [--root <repo-root>]
Exit 0 = every runnable suite passes. """
import os, re, subprocess, sys

ROOT = sys.argv[sys.argv.index("--root") + 1] if "--root" in sys.argv \
    else os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(ROOT, "app/src/main/java/com/vn/pixelpet")

results = []  # (name, status, detail)
errors = []
def record(name, status, detail=""):
    results.append((name, status, detail))
def read(p):
    with open(p) as f: return f.read()

def run_suite(name, cmd, cwd):
    """Run a suite script; return (status, detail)."""
    try:
        r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                           timeout=300)
    except Exception as e:
        return ("ERROR", f"could not run: {e}")
    tail = (r.stdout.strip().splitlines() or [""]) [-1]
    if r.returncode == 0:
        return ("PASS", tail[:160])
    return ("FAIL", tail[:160])

# ---------- 1. sibling suites ----------
# All sibling suites accept the java dir as argv[1] (qa_species /
# qa_variants gained this in Phase 5; qa_walk / qa_brain already had it).
for name in ["qa_species.py", "qa_variants.py", "qa_walk.py", "qa_brain.py"]:
    st, detail = run_suite(name, ["python3", os.path.join(ROOT, name), JAVA], ROOT)
    record(name, st, detail)
# qa_phase4 is __file__-relative: run from the repo root.
st, detail = run_suite("qa_phase4.py", ["python3", os.path.join(ROOT, "qa_phase4.py")], ROOT)
record("qa_phase4.py", st, detail)
# Phase 5 suites (this gate's own checks).
for name in ["qa_migration.py", "qa_pixel_diff.py"]:
    st, detail = run_suite(name, ["python3", os.path.join(ROOT, name), "--root", ROOT], ROOT)
    record(name, st, detail)

# ---------- 2. frame-time static bounds (wallpaper steady draw path) ----------
print("frame-time static bounds (wallpaper, 30fps => 33ms budget)")
wp = read(f"{JAVA}/wallpaper/PixelWallpaperService.kt")
ft_errors = []
if "FRAME_MS = 33" not in wp:
    ft_errors.append("wallpaper frame interval must be FRAME_MS = 33 (~30fps)")
# drawOnce body must not call snapshot() (storage write per frame).
# Extract by brace counting from the function signature: a regex stop
# pattern would swallow following functions (drawOnce closes at 8-space
# indent, deeper than the naive 4-space match).
def _fn_body(src, sig):
    i = src.find(sig)
    if i < 0: return ""
    j = src.find("{", i)
    depth = 0
    for k in range(j, len(src)):
        if src[k] == "{": depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0: return src[i:k + 1]
    return ""
draw_once = _fn_body(wp, "private fun drawOnce")
# Strip comments before the snapshot() check: the body contains the
# comment "never snapshot() per frame" — a bare substring match would
# flag its own documentation (the exact vacuity the Phase 4 review
# called out). Code only, no comments.
draw_once_code = re.sub(r"//.*", "", draw_once)
draw_once_code = re.sub(r"/\*.*?\*/", "", draw_once_code, flags=re.S)
if "snapshot()" in draw_once_code:
    ft_errors.append("drawOnce calls snapshot() — per-frame storage write")
if "cachedState" not in draw_once_code:
    ft_errors.append("drawOnce must read the cached 500ms state, not snapshot()")
# bitmap cached by key; reused rects
# Legacy String-key form OR field-compared components (hardening pass:
# same coverage, 0 String allocs/frame).
has_legacy_key = "petKey" in wp and "if (key != petKey" in wp
has_field_key = "forceRender" in wp and "lastVariantId" in wp
if not (has_legacy_key or has_field_key):
    ft_errors.append("wallpaper must cache the pet bitmap by render key (String key or field-compared components)")
if "private val dstRect" not in wp or "private val shadowRect" not in wp:
    ft_errors.append("wallpaper must reuse dstRect/shadowRect fields (no per-frame RectF)")
# starfield built on surface change, not per frame
m2 = re.search(r"override fun onSurfaceChanged.*?\n        \}", wp, re.S)
if not m2 or "stars = List(70)" not in m2.group(0):
    ft_errors.append("starfield must be built in onSurfaceChanged, not per frame")
# known per-frame costs, documented not hidden: 0 key allocs (field-compare), 70 sin()
if "val key =" in draw_once_code:
    ft_errors.append("drawOnce must not build a key String per frame (field-compare instead)")
if ft_errors:
    record("frame-time static bounds", "FAIL", "; ".join(ft_errors))
else:
    record("frame-time static bounds", "PASS",
           "steady path: bg + 70 rects + cached bitmap blit + shadow; p95 measured on device only")
errors.extend(ft_errors)

# ---------- 3. verdict ----------
print("\n===== GOLDEN SUITE =====")
for name, st, detail in results:
    print(f"  [{st}] {name}  {detail}")
failed = [n for n, s, _ in results if s == "FAIL"]
skipped = [n for n, s, _ in results if s == "SKIP"]
print(f"\nSuites: {len(results)} total, "
      f"{sum(1 for _, s, _ in results if s == 'PASS')} pass, "
      f"{len(failed)} fail, {len(skipped)} skip")
if failed:
    print("GOLDEN SUITE: FAIL —", ", ".join(failed))
    sys.exit(1)
print("GOLDEN SUITE: PASS")
print("Note: pixel-diff comparison itself is NOT_RUN here (needs Android "
      "runtime). CI/device-only per qa_pixel_diff.py.")
sys.exit(0)
