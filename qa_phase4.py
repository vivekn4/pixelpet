#!/usr/bin/env python3
"""Phase 4 static QA for PixelPet: wallpaper roam, widget walk frame,
premium aura skins, README honesty, manifest<->class mapping for the new
classes. Static checks only — no Kotlin compiler / Android SDK here.
Run from the repo root: python3 qa_phase4.py"""
import os, re, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
APP = ROOT + "/app/src/main"
JAVA = APP + "/java/com/vn/pixelpet"
errors, warnings = [], []

def err(m): errors.append(m)
def warn(m): warnings.append(m)

def read(p):
    with open(p) as f: return f.read()

# ---------- 1. new Phase 4 classes exist ----------
for p in [
    "wallpaper/PixelWallpaperService.kt",
    "widget/PixelPetWidgetProvider.kt",
    "widget/WidgetUpdateJob.kt",
    "doomscroll/DoomscrollMonitor.kt",
    "AuraSkin.kt",
]:
    if not os.path.isfile(f"{JAVA}/{p}"): err(f"missing Phase 4 class: {p}")
print("Phase 4 classes present")

wp = read(f"{JAVA}/wallpaper/PixelWallpaperService.kt")
wg = read(f"{JAVA}/widget/PixelPetWidgetProvider.kt")
wj = read(f"{JAVA}/widget/WidgetUpdateJob.kt")
ds = read(f"{JAVA}/doomscroll/DoomscrollMonitor.kt")
au = read(f"{JAVA}/AuraSkin.kt")
pr = read(f"{JAVA}/PetRenderer.kt")
ma = read(f"{JAVA}/MainActivity.kt")

# ---------- 2. wallpaper roam gates ----------
for needle, what in [
    ("WalkEngine", "wallpaper uses WalkEngine"),
    ("onVisibilityChanged", "wallpaper visibility gate"),
    ("ACTION_SCREEN_OFF", "wallpaper screen-off gate"),
    ("IDLE_PAUSE_MS", "wallpaper 30s idle roam pause"),
    ("FRAME_MS = 33", "wallpaper ~30fps"),
    ("onSingleTapConfirmed", "wallpaper tap = pet"),
    ("onDoubleTap", "wallpaper double-tap = play"),
    ("ScreenReceiver", "wallpaper sleep/wake via ScreenReceiver"),
]:
    if needle not in wp: err(f"wallpaper: missing {what} ({needle})")
# No draw loop when invisible: startLoop must check visibility + screenOn.
if "if (!visible || !ScreenReceiver.screenOn) return" not in wp:
    err("wallpaper: startLoop must gate on visible + screenOn")
print("wallpaper roam gates ok")

# ---------- 3. widget walk frame ----------
for needle, what in [
    ("renderVariant", "widget renders via renderVariant"),
    ("walkPhase", "widget uses a walk frame"),
    ("refreshAll", "widget refreshAll exists"),
    ("ACTION_QUICK_FEED", "widget quick-feed action"),
    ("SNAPSHOT", "widget documents snapshot (not live)"),
]:
    if needle not in wg: err(f"widget: missing {what} ({needle})")
if "RemoteViews" not in wg: err("widget: must use RemoteViews")
if "live view" not in wg.lower(): warn("widget: snapshot honesty note wording changed")
# WidgetUpdateJob: schedule + refresh
if "fun schedule" not in wj or "refreshAll" not in wj:
    err("widget job: must schedule + refreshAll")
if "JobService" not in wj: err("widget job: must extend JobService")
print("widget walk frame ok")

# ---------- 4. aura skins ----------
for skin in ["GOLD_ORBIT", "RAINBOW", "EMBER", "STARLIGHT", "NONE"]:
    if skin not in au: err(f"aura: missing skin {skin}")
if "fun effective" not in au: err("aura: missing effective() gate")
if "isPremium" not in au: err("aura: effective() must gate on premium")
if "auraStyle" not in pr: err("renderer: missing auraStyle param")
if "AuraSkin.GOLD_ORBIT" not in pr: err("renderer: default auraStyle must be GOLD_ORBIT")
# PremiumManager key validation untouched: no key logic in AuraSkin.
if "HMAC" in au or "MessageDigest" in au or "activate" in au:
    err("aura: must not touch key validation")
# MainActivity: aura on all variants for premium (no Mystical-only gate).
if "state.species.category == Species.Category.MYSTICAL" in ma:
    err("MainActivity: v2 Mystical-only aura gate must be gone")
if "AuraSkin.effective" not in ma: err("MainActivity: must use AuraSkin.effective")
print("aura skins ok")

# ---------- 5. doomscroll monitor (compile dependency) ----------
for needle, what in [
    ("hasPermission", "DoomscrollMonitor.hasPermission"),
    ("requestPermission", "DoomscrollMonitor.requestPermission"),
    ("maybeAutoLog", "DoomscrollMonitor.maybeAutoLog"),
    ("UsageStatsManager", "on-device UsageStats"),
]:
    if needle not in ds: err(f"doomscroll: missing {what}")
if "http" in ds.lower() or "URL(" in ds:
    err("doomscroll: must never use the network")
print("doomscroll monitor ok")

# ---------- 6. Goal.completed (Phase 3 compile-break fix) ----------
if "val completed" not in read(f"{JAVA}/Goal.kt"):
    err("Goal: missing completed property")
print("Goal.completed ok")

# ---------- 7. README honesty ----------
rm = read(f"{ROOT}/README.md")
for needle, what in [
    ("108 variants", "README: 108 variants"),
    ("roam", "README: wallpaper roam"),
    ("Personality", "README: personality"),
    ("Native Brain", "README: Native Brain"),
    ("not a language", "README: honest not-a-language-model line"),
    ("23 family powers", "README: 23 powers"),
    ("snapshot bitmap", "README: widget is a snapshot"),
    ("aura skins", "README: premium aura skins"),
    ("look only", "README: premium look-only rule"),
]:
    if needle not in rm: err(f"README: missing {what} ({needle})")
for bad in ["live widget", "photorealistic", "realistic graphics", "LLM", "language model that"]:
    if bad in rm: err(f"README: dishonest claim present: {bad!r}")
if "Every push builds" in rm and "workflow file is being added" not in rm:
    err("README: must not claim CI builds until the workflow lands")
print("README honesty ok")

# ---------- 8. manifest maps to the new classes ----------
man = read(f"{APP}/AndroidManifest.xml")
for comp in [".wallpaper.PixelWallpaperService", ".widget.PixelPetWidgetProvider", ".widget.WidgetUpdateJob"]:
    if comp not in man: err(f"manifest: missing {comp}")
print("manifest mapping ok")

print(f"\nERRORS: {len(errors)}")
for e in errors: print("  E:", e)
print(f"WARNINGS: {len(warnings)}")
for w in warnings: print("  W:", w)
sys.exit(1 if errors else 0)
