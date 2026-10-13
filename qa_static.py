#!/usr/bin/env python3
"""Static QA for PixelPet: XML well-formedness, resource reference resolution,
manifest<->class mapping, Kotlin<->R.id mapping, YAML validity."""
import os, re, sys, xml.etree.ElementTree as ET

ROOT = "/home/hatch/workspace/repos/pixelpet"
APP = ROOT + "/app/src/main"
RES = APP + "/res"
JAVA = APP + "/java/com/vn/pixelpet"
errors, warnings = [], []

def err(m): errors.append(m)
def warn(m): warnings.append(m)

# ---------- 1. XML well-formedness ----------
xml_files = []
for dp, _, fns in os.walk(RES):
    for fn in fns:
        if fn.endswith(".xml"): xml_files.append(os.path.join(dp, fn))
xml_files.append(APP + "/AndroidManifest.xml")
for f in xml_files:
    try: ET.parse(f)
    except ET.ParseError as e: err(f"XML parse error in {f}: {e}")
print(f"parsed {len(xml_files)} XML files")

# ---------- 2. collect defined resources ----------
strings, colors, dimens, drawables, layouts, mipmaps, xmls, ids = set(), set(), set(), set(), set(), set(), set(), set()
def collect_values():
    for fn in os.listdir(RES + "/values"):
        r = ET.parse(f"{RES}/values/{fn}").getroot()
        for el in r:
            n = el.get("name")
            if not n: continue
            if el.tag == "string": strings.add(n)
            elif el.tag == "color": colors.add(n)
            elif el.tag == "dimen": dimens.add(n)
for d, s in [("drawable", drawables), ("layout", layouts), ("mipmap-anydpi-v26", mipmaps), ("xml", xmls)]:
    p = f"{RES}/{d}"
    if os.path.isdir(p):
        for fn in os.listdir(p):
            if fn.endswith(".xml"): s.add(fn[:-4])
collect_values()
for dp, _, fns in os.walk(RES + "/layout"):
    for fn in fns:
        for m in re.finditer(r'android:id="@\+id/([^"]+)"', open(f"{dp}/{fn}").read()):
            ids.add(m.group(1))
print(f"defined: strings={len(strings)} colors={len(colors)} dimens={len(dimens)} drawables={len(drawables)} layouts={len(layouts)} mipmaps={len(mipmaps)} xmls={len(xmls)} ids={len(ids)}")

# ---------- 3. resource reference resolution ----------
refmap = {"string": strings, "color": colors, "dimen": dimens, "drawable": drawables,
          "layout": layouts, "mipmap": mipmaps, "xml": xmls}
ANDROID_NS_OK = {"background", "progress"}  # android:id refs that are framework
for f in xml_files:
    txt = open(f).read()
    for m in re.finditer(r'@(string|color|dimen|drawable|layout|mipmap|xml)/([A-Za-z0-9_]+)', txt):
        kind, name = m.groups()
        if name not in refmap[kind]:
            err(f"{f}: @{kind}/{name} does not resolve")
    for m in re.finditer(r'@android:id/([A-Za-z0-9_]+)', txt):
        if m.group(1) not in ANDROID_NS_OK:
            warn(f"{f}: @android:id/{m.group(1)} (verify framework id)")

# ---------- 4. manifest components -> classes ----------
man = ET.parse(APP + "/AndroidManifest.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
classes_needed = set()
for tag in ["activity", "receiver", "service", "provider"]:
    for el in man.iter(tag):
        n = el.get(f"{ns}name", "")
        if n.startswith("."): classes_needed.add("com/vn/pixelpet/" + n[1:].replace(".", "/") + ".kt")
        elif n: classes_needed.add(n.replace(".", "/") + ".kt")
for c in classes_needed:
    p = APP + "/java/" + c
    if not os.path.isfile(p): err(f"manifest component missing class: {c}")
    else:
        # package + class name sanity
        txt = open(p).read()
        cls = c.split("/")[-1][:-3]
        if not re.search(rf"(class|object)\s+{cls}\b", txt):
            err(f"{c}: no class/object {cls} declared")
print(f"manifest components checked: {len(classes_needed)}")

# meta-data / resource refs in manifest
for m in re.finditer(r'android:resource="@(xml|mipmap|drawable|string)/([^"]+)"', open(APP + "/AndroidManifest.xml").read()):
    kind, name = m.groups()
    if name not in refmap[{"xml":"xml","mipmap":"mipmap","drawable":"drawable","string":"string"}[kind]]:
        err(f"manifest: @{kind}/{name} missing")

# ---------- 5. Kotlin R references ----------
kt_files = []
for dp, _, fns in os.walk(JAVA):
    for fn in fns:
        if fn.endswith(".kt"): kt_files.append(os.path.join(dp, fn))
rk = {"id": ids, "layout": layouts, "string": strings, "drawable": drawables,
      "mipmap": mipmaps, "xml": xmls}
for f in kt_files:
    txt = open(f).read()
    for m in re.finditer(r'(?<!android\.)R\.(id|layout|string|drawable|mipmap|xml)\.([A-Za-z0-9_]+)', txt):
        kind, name = m.groups()
        if name not in rk[kind]:
            # allow framework android.R references written as R. (we don't use any)
            err(f"{os.path.basename(f)}: R.{kind}.{name} does not resolve")
    # delimiter balance (crude but catches typos)
    for a, b in [("{", "}"), ("(", ")"), ("[", "]")]:
        if txt.count(a) != txt.count(b):
            err(f"{os.path.basename(f)}: unbalanced {a}{b} ({txt.count(a)} vs {txt.count(b)})")
print(f"kotlin files checked: {len(kt_files)}")

# ---------- 6. workflow YAML ----------
try:
    import yaml
    wf = yaml.safe_load(open(ROOT + "/.github/workflows/android.yml"))
    steps = wf["jobs"]["build"]["steps"]
    assert any("assembleDebug" in str(s.get("run", "")) for s in steps), "no assembleDebug step"
    assert any("upload-artifact" in str(s.get("uses", "")) for s in steps), "no artifact upload"
    print("workflow YAML ok")
except ImportError:
    warn("pyyaml missing — workflow not parsed")
except Exception as e:
    err(f"workflow problem: {e}")

# ---------- 7. gradle files exist ----------
for f in ["settings.gradle", "build.gradle", "gradle.properties", "app/build.gradle",
          "gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
          "app/proguard-rules.pro", "local.properties"]:
    if not os.path.isfile(ROOT + "/" + f): err(f"missing: {f}")
print("gradle files ok")

print(f"\nERRORS: {len(errors)}")
for e in errors: print("  E:", e)
print(f"WARNINGS: {len(warnings)}")
for w in warnings: print("  W:", w)
sys.exit(1 if errors else 0)
