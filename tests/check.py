#!/usr/bin/env python3
"""Static checks: the rules that keep Luban safe and consistent, enforced on every push."""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
fails = 0


def check(name, ok, detail=""):
    global fails
    print(f"{'PASS' if ok else 'FAIL'} {name}" + ("" if ok or not detail else f" — {detail}"))
    fails += 0 if ok else 1


def files(pattern):
    return [p for p in ROOT.glob(pattern) if ".git/" not in str(p)]


def grep(pattern, globs):
    hits = []
    for g in globs:
        for p in files(g):
            for n, line in enumerate(p.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                if re.search(pattern, line):
                    hits.append(f"{p.relative_to(ROOT)}:{n}")
    return hits


# --- the screen never turns data into markup
check("web: no innerHTML / outerHTML / insertAdjacentHTML / document.write",
      not grep(r"innerHTML|outerHTML|insertAdjacentHTML|document\.write", ["web/js/*.js"]))
check("web: no eval or new Function", not grep(r"\beval\(|new Function\(", ["web/js/*.js"]))
# the screen runs in TV WebViews that may be years old (Android 8 era, Chrome ~61): no newer syntax or APIs there
screen_js = ["web/js/app.js", "web/js/prayer.js", "web/js/settings.js", "web/js/i18n.js"]
check("web: screen code avoids features older TV WebViews lack (?. ?? fromEntries flat replaceAll hourCycle …)",
      not grep(r"\?\.[A-Za-z_(\[]|\?\?|Object\.fromEntries|\.flat\(|\.flatMap\(|\.replaceAll\(|structuredClone|\.at\(-?\d|hourCycle|Promise\.allSettled|\{\s*\.\.\.", screen_js))

# --- the apps: no file:// pages, no universal access, no JS bridge, https only
java = ["android/*/src/main/java/**/*.java"]
check("android: no file access from pages", not grep(r"setAllow(Universal|File)AccessFromFileURLs\(true\)|setAllowFileAccess\(true\)", java))
check("android: no file:// URLs", not grep(r"file://", java))
check("android: no addJavascriptInterface", not grep(r"addJavascriptInterface", java))
check("android: String.format always with Locale.ROOT (Arabic TVs would print ٠-٩)",
      not [h for h in grep(r"String\.format\(", java) if "Locale.ROOT" not in (ROOT / h.split(":")[0]).read_text().splitlines()[int(h.split(":")[1]) - 1]])
check("android: no cleartext traffic", all('usesCleartextTraffic="false"' in p.read_text() for p in files("android/*/src/main/AndroidManifest.xml")))
check("android: ADB receivers need the DUMP permission",
      all(re.search(r'AdbReceiver"\s+android:exported="true"\s+android:permission="android.permission.DUMP"', p.read_text())
          for p in files("android/*/src/main/AndroidManifest.xml")))
check("android: no LOCKED_BOOT_COMPLETED without directBootAware", not grep(r"LOCKED_BOOT_COMPLETED", ["android/**/*.xml"]))

# --- the prayer methods are the same in JS and Java
js_methods = set(re.findall(r"^\s{4}(\w+):\s*\{ fajr", (ROOT / "web/js/prayer.js").read_text(), re.M))
java_methods = set(re.findall(r'METHODS\.put\("(\w+)"', (ROOT / "android/launcher/src/main/java/om/luban/home/PrayerTimes.java").read_text()))
check("prayer: JS and Java list the same methods", js_methods == java_methods and len(js_methods) >= 10, f"{js_methods ^ java_methods}")

# --- content
cities = json.loads((ROOT / "content/cities.json").read_text(encoding="utf-8"))
ids = [c["id"] for c in cities["cities"]]
check("cities: unique ids", len(ids) == len(set(ids)))
check("cities: every country has a zone and a known method",
      all(v["tz"] and v["method"] in js_methods for v in cities["countries"].values()))
check("cities: every city's country is listed", all(c["c"] in cities["countries"] for c in cities["cities"]))
govs = {c["gov"] for c in cities["cities"] if c["c"] == "OM"}
check("cities: all 11 Omani governorates have a city", govs == set(cities["governorates"]) and len(govs) == 11, sorted(set(cities["governorates"]) - govs))
check("cities: coordinates in range", all(-66 <= c["lat"] <= 66 and -180 <= c["lon"] <= 180 for c in cities["cities"]))
verses = json.loads((ROOT / "content/verses.json").read_text(encoding="utf-8"))
hadith = json.loads((ROOT / "content/hadith.json").read_text(encoding="utf-8"))
check("content: at least 50 verses and 25 hadith", len(verses) >= 50 and len(hadith) >= 25, f"{len(verses)}, {len(hadith)}")
check("content: every verse has a reference", all(re.fullmatch(r"\d+:\d+(-\d+)?", v["ref"]) for v in verses))
photos = json.loads((ROOT / "content/photos.json").read_text(encoding="utf-8"))
check("photos: every listed photo is in photos/", all((ROOT / "photos" / p["f"]).is_file() for p in photos))
check("photos: every photo is credited with a licence", all(p.get("by") and p.get("licence") for p in photos))

# --- secrets and supply chain
tracked = subprocess.run(["git", "-C", str(ROOT), "ls-files"], capture_output=True, text=True).stdout.split()
private = [f for f in tracked if (ROOT / f).is_file() and (ROOT / f).stat().st_size < 2_000_000
           and re.search(rb"-----BEGIN (EC |RSA |ENCRYPTED )?PRIVATE KEY-----", (ROOT / f).read_bytes())
           and not f.startswith("tests/")]
check("repo: no private keys committed", not private, private)
check("repo: no keystores committed", not [f for f in tracked if re.search(r"\.(p12|jks|keystore|pk8)$", f)])
uses = grep(r"^\s*-?\s*uses:", [".github/workflows/*.yml"])
unpinned = [u for u in uses if not re.search(r"@[0-9a-f]{40}", (ROOT / u.split(":")[0]).read_text().splitlines()[int(u.split(":")[1]) - 1])]
check("ci: every action pinned to a commit", not unpinned, unpinned)
check("fonts: licences shipped", (ROOT / "assets/fonts/OFL-Tajawal.txt").is_file() and (ROOT / "assets/fonts/OFL-Amiri.txt").is_file())
check("repo: LICENSE present", (ROOT / "LICENSE").is_file())

sys.exit(1 if fails else 0)
