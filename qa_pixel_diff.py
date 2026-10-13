#!/usr/bin/env python3
"""Pixel-diff golden-bitmap gate for PixelPet (PLAN_V3.md Phase 5).

HONEST SCOPE — read this before trusting a "pass" from this file:
This environment has no Kotlin compiler and no Android SDK, so this script
CANNOT render a single pixel and CANNOT compare bitmaps. It verifies the
two preconditions that make a future pixel-diff run meaningful:

  1. DETERMINISM: PetRenderer's render path must be a pure function of its
     explicit inputs (variant, mood, blink, aura, wavePhase, walkPhase,
     legs). No Random, no clock, no wall time, no hash-map iteration order
     inside the renderer. If the renderer is deterministic, a golden bitmap
     captured once stays valid until the renderer changes.
  2. KEY COVERAGE: every input that changes the output must be part of the
     render key the wallpaper/widget use for cache invalidation — otherwise
     two different pictures could share one cached bitmap.

True pixel comparison (render all 108 variants, diff against golden PNGs)
requires an Android runtime and is CI/device-only. NO golden bitmaps exist
in this repo yet, and this script does not pretend otherwise: its verdict
for the pixel-diff itself is always NOT_RUN until a golden capture exists.

Golden-capture protocol (for the future capture run, on an emulator or
device — NOT runnable here):
  1. On a Pixel 9 API 36 emulator (or the S26 Ultra), run a debug build
     with a one-off capture harness that calls
     PetRenderer.renderVariant(variant, CONTENT, cellPx=16, blink=false,
     aura=false, wavePhase=0, walkPhase=0, legs=false) for all 108
     variants and writes one PNG per variant id to tests/golden/.
  2. Commit tests/golden/*.png. CI renders the same 108 inputs on the
     same emulator image and diffs pixel-by-pixel; threshold: 0 differing
     pixels (deterministic renderer => exact match expected).
  3. Any renderer change requires re-capturing goldens in the same run
     (review the visual diff before committing new goldens).

Run:  python3 qa_pixel_diff.py [--root <repo-root>]
Exit 0 = determinism + key-coverage preconditions hold (pixel diff itself
still NOT_RUN). Exit 1 = a precondition fails.
"""
import os, re, sys

ROOT = sys.argv[sys.argv.index("--root") + 1] if "--root" in sys.argv \
    else os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(ROOT, "app/src/main/java/com/vn/pixelpet")

errors = []
def err(m): errors.append(m)
def read(p):
    with open(p) as f: return f.read()

renderer = read(f"{JAVA}/PetRenderer.kt")
wallpaper = read(f"{JAVA}/wallpaper/PixelWallpaperService.kt")

# ---------- 1. determinism: no entropy / time inside the renderer ----------
print("renderer determinism")
for needle, what in [
    ("Random", "Random number generator"),
    ("SystemClock", "elapsed-realtime clock"),
    ("currentTimeMillis", "wall clock"),
    ("nanoTime", "nano clock"),
    ("HashMap", "unordered map (iteration order)"),
    ("HashSet", "unordered set (iteration order)"),
]:
    if needle in renderer:
        err(f"PetRenderer uses {what} ({needle}) — render output is not a pure function of inputs")

# render entry points must take every varying input as an explicit parameter
for fn in ["fun renderVariant", "fun render("]:
    if fn not in renderer:
        err(f"PetRenderer: missing entry point {fn}")
for param in ["blink", "auraStyle", "wavePhase", "walkPhase", "legs"]:
    if param not in renderer:
        err(f"PetRenderer: render inputs must include explicit '{param}' parameter")

# ---------- 2. cache-key coverage: key must include every varying input ----------
print("cache-key coverage")
# wallpaper render key: find the `key = ...` construction in drawOnce
m = re.search(r'val key = ([^\n]+)', wallpaper)
key_expr = m.group(1) if m else ""
for token, what in [
    ("variant.id", "variant"),
    ("mood", "mood"),
    ("walk.walkPhase", "walk phase"),
    ("blinking", "blink"),
    ("auraSkin", "aura skin"),
    ("squashStretch", "squash bucket"),
]:
    if token not in key_expr:
        err(f"wallpaper render key missing {what} ({token}) — stale bitmap risk")

# ---------- 3. golden bitmaps: assert absence (never claim they exist) ----------
print("golden-bitmap state")
golden_dir = os.path.join(ROOT, "tests/golden")
goldens = []
if os.path.isdir(golden_dir):
    goldens = [f for f in os.listdir(golden_dir) if f.endswith(".png")]
if goldens:
    err(f"tests/golden holds {len(goldens)} PNGs but this script cannot verify "
        "they match the current renderer — re-capture after any renderer change")
else:
    print("  no golden bitmaps in repo — pixel diff NOT_RUN (expected)")

# ---------- verdict ----------
print(f"\nPixel-diff gate: {len(errors)} error(s)")
for e in errors: print("  ERROR:", e)
print("Pixel-diff comparison itself: NOT_RUN (needs Android runtime; see protocol above)")
sys.exit(1 if errors else 0)
