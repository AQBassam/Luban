#!/usr/bin/env python3
"""web/ -> dist/

dist/screen/index.html     the screen as the apps serve it (fonts and photos answered by the app from its own files)
dist/screen/settings.html  the phone settings page the TV serves on the local network
dist/luban.html            stand-alone: fonts inlined, photos from GitHub. Open it in any browser, F11 for full screen.
dist/bundle.zip            the two app pages, for signed over-the-air updates (tools/sign.py signs it in CI)

Deterministic: the same sources give byte-identical files (tests check dist/ is up to date).
"""
import base64
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WEB, DIST, CONTENT, FONTS = ROOT / "web", ROOT / "dist", ROOT / "content", ROOT / "assets" / "fonts"
REPO = "AQBassam/Luban"
PHOTO_BASE = f"https://raw.githubusercontent.com/{REPO}/main/photos/"
SCRIPTS = ["prayer.js", "settings.js", "i18n.js", "app.js"]


def read(p):
    return Path(p).read_text(encoding="utf-8")


def data_json():
    load = lambda n: json.loads(read(CONTENT / n))
    cities = load("cities.json")
    cities.pop("_note", None)
    data = {
        "cities": cities,
        "verses": [{k: v[k] for k in ("text", "surah", "ayah")} for v in load("verses.json")],
        "hadith": [{k: v[k] for k in ("text", "source")} for v in load("hadith.json")],
        "photos": load("photos.json"),
        "teamsAr": {k: v for k, v in load("teams-ar.json").items() if not k.startswith("_")},
    }
    # </script> can never appear inside the inlined JSON
    return json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")


def css(font_prefix, inline):
    s = read(WEB / "css" / "screen.css")
    if inline:
        for f in sorted(FONTS.glob("*.woff2")):
            b64 = base64.b64encode(f.read_bytes()).decode()
            s = s.replace(f"__FONT__{f.name}", f"data:font/woff2;base64,{b64}")
    return s.replace("__FONT__", font_prefix)


def page(font_prefix, inline_fonts, extra_js=""):
    html = read(WEB / "index.html")
    js = "".join(read(WEB / "js" / n) + "\n" for n in SCRIPTS)
    style = f"<style>\n{css(font_prefix, inline_fonts)}</style>"
    script = f"<script>window.LUBAN_DATA={data_json()};{extra_js}</script>\n<script>\n{js}</script>"
    return html.replace("<!--STYLE-->", style).replace("<!--SCRIPT-->", script)


def settings_page():
    html = read(WEB / "settings.html")
    js = read(WEB / "js" / "prayer.js") + read(WEB / "js" / "settings.js") + read(WEB / "js" / "phone.js")
    data = json.dumps({"cities": json.loads(data_json())["cities"]}, ensure_ascii=False, separators=(",", ":"))
    return html.replace("<!--SCRIPT-->", f"<script>window.LUBAN_DATA={data};</script>\n<script>\n{js}</script>")


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def main():
    screen = page("fonts/", False)
    settings = settings_page()
    write(DIST / "screen" / "index.html", screen)
    write(DIST / "screen" / "settings.html", settings)
    write(DIST / "luban.html", page("", True, f"window.LUBAN_PHOTO_BASE={json.dumps(PHOTO_BASE)};"))
    # fixed timestamps and no compression, so the zip is byte-identical on every machine (zlib versions differ)
    with zipfile.ZipFile(DIST / "bundle.zip", "w", zipfile.ZIP_STORED) as z:
        for name, text in (("index.html", screen), ("settings.html", settings)):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = 0o644 << 16
            info.create_system = 3
            z.writestr(info, text.encode("utf-8"))
    for p in ("screen/index.html", "screen/settings.html", "luban.html", "bundle.zip"):
        print(f"{p}: {(DIST / p).stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
