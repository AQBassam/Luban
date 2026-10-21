#!/usr/bin/env python3
"""Fill photos/ with landscape photographs of Oman (1920x1080 JPEG) and write content/photos.json and
docs/PHOTO-CREDITS.md. Run by .github/workflows/photos.yml (it needs the network).

Source: Wikimedia Commons, "Quality images of Oman" (peer-reviewed for quality), only free licences (CC0, public
domain, CC BY, CC BY-SA), each credited on screen and in the credits file with author, licence and link.
With UNSPLASH_ACCESS_KEY set, the hand-picked list in content/photo-picks.json is fetched from Unsplash's API instead.
Nothing is guessed: a photo without a clear free licence or author is skipped.
"""
import html
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "photos"
UA = {"User-Agent": "LubanPhotoFetcher/1.0 (https://github.com/AQBassam/Luban)"}
API = "https://commons.wikimedia.org/w/api.php"
CATEGORIES = ["Category:Quality images of Oman"]
MAX = 40
FREE = re.compile(r"^(CC0|Public domain|PD|CC BY(-SA)? [0-9.]+)", re.I)
# not a landscape for a TV background
SKIP = re.compile(r"insect|bird|butterfly|beetle|moth|lizard|gecko|spider|flower|portrait|interior|ceiling|"
                  r"detail|close-?up|macro|food|dish|coin|stamp|map|logo|exhibit|\bcar\b|aircraft", re.I)
PLACES = [  # file-title words -> where it is, in Arabic and English
    ("mutrah|muttrah", "مطرح", "Muttrah"), ("muscat|qurum|al alam", "مسقط", "Muscat"),
    ("nizwa", "نزوى", "Nizwa"), ("bahla", "بهلاء", "Bahla"), ("jabrin|jibreen", "حصن جبرين", "Jabrin Castle"),
    ("misfat", "مسفاة العبريين", "Misfat al Abriyeen"), ("jebel akhdar|jabal akhdar|saiq", "الجبل الأخضر", "Jebel Akhdar"),
    ("jebel shams|jabal shams", "جبل شمس", "Jebel Shams"), ("wahiba|sharqiya sands|sharqiyah sands", "رمال الشرقية", "Sharqiyah Sands"),
    ("wadi shab", "وادي شاب", "Wadi Shab"), ("wadi bani khalid", "وادي بني خالد", "Wadi Bani Khalid"),
    ("bimmah|sinkhole", "حوية نجم", "Bimmah Sinkhole"), (r"\bsur\b|ras al jinz", "صور", "Sur"),
    ("salalah|dhofar|mirbat|taqah|mughsail", "ظفار", "Dhofar"), ("musandam|khasab", "مسندم", "Musandam"),
    ("sohar", "صحار", "Sohar"), ("rustaq|nakhal", "جنوب الباطنة", "South Al Batinah"), ("ibra|sinaw", "الشرقية", "Ash Sharqiyah"),
]


def get(url, timeout=90):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
        return r.read()


def api(**q):
    q.update(format="json", formatversion="2")
    return json.loads(get(API + "?" + urllib.parse.urlencode(q)))


def plain(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()


def place(title):
    for pat, ar, en in PLACES:
        if re.search(pat, title, re.I):
            return ar, en
    return "عُمان", "Oman"


def commons_candidates():
    titles = []
    for cat in CATEGORIES:
        cont = {}
        while True:
            j = api(action="query", list="categorymembers", cmtitle=cat, cmtype="file", cmlimit="500", **cont)
            titles += [m["title"] for m in j["query"]["categorymembers"]]
            if "continue" not in j:
                break
            cont = {"cmcontinue": j["continue"]["cmcontinue"]}
    print(f"{len(titles)} files in the category")
    out = []
    for i in range(0, len(titles), 40):
        j = api(action="query", titles="|".join(titles[i:i + 40]), prop="imageinfo",
                iiprop="url|size|extmetadata|mime", iiurlwidth="2400")
        for p in j["query"]["pages"]:
            ii = (p.get("imageinfo") or [{}])[0]
            meta = ii.get("extmetadata", {})
            lic = plain(meta.get("LicenseShortName", {}).get("value"))
            author = plain(meta.get("Artist", {}).get("value"))
            w, h = ii.get("width", 0), ii.get("height", 0)
            if ii.get("mime") != "image/jpeg" or not FREE.match(lic) or not author or w < 1920 or w < h * 1.3:
                continue
            if SKIP.search(p["title"]):
                continue
            ar, en = place(p["title"])
            out.append({"id": f"c{p['pageid']}", "url": ii.get("thumburl") or ii["url"], "by": author[:80],
                        "licence": lic, "link": ii.get("descriptionurl", ""), "place_ar": ar, "place_en": en,
                        "known": en != "Oman"})
    out.sort(key=lambda x: (not x["known"], x["place_en"]))   # named places first
    return out


def unsplash_candidates(key):
    out = []
    for p in json.loads((ROOT / "content/photo-picks.json").read_text(encoding="utf-8")):
        try:
            meta = json.loads(get(f"https://api.unsplash.com/photos/{p['id']}?client_id={key}"))
            get(meta["links"]["download_location"] + f"&client_id={key}")   # the API guidelines ask for this ping
            out.append({"id": p["id"], "url": meta["urls"]["raw"] + "&w=2400&fm=jpg&q=85&fit=max", "by": p["by"],
                        "licence": "Unsplash License", "link": f"https://unsplash.com/photos/{p['id']}",
                        "place_ar": p["place_ar"], "place_en": p["place_en"]})
        except Exception as e:  # noqa: BLE001
            print(f"!! {p['id']}: {e}")
    return out


def main():
    OUT.mkdir(exist_ok=True)
    key = os.environ.get("UNSPLASH_ACCESS_KEY")
    candidates = unsplash_candidates(key) if key else commons_candidates()
    print(f"{len(candidates)} usable candidates")
    kept, problems = [], []
    for c in candidates:
        if len(kept) >= MAX:
            break
        name = f"{c['id']}.jpg"
        dst = OUT / name
        try:
            if not dst.exists():
                im = ImageOps.exif_transpose(Image.open(io.BytesIO(get(c["url"])))).convert("RGB")
                tw = min(im.width, int(im.height * 16 / 9)); th = int(tw * 9 / 16)
                left, top = (im.width - tw) // 2, (im.height - th) // 2
                im = im.crop((left, top, left + tw, top + th)).resize((1920, 1080), Image.LANCZOS)
                im.save(dst, "JPEG", quality=80, optimize=True, progressive=True)
            kept.append({"f": name, "by": c["by"], "licence": c["licence"], "link": c["link"],
                         "place_ar": c["place_ar"], "place_en": c["place_en"]})
        except Exception as e:  # noqa: BLE001
            problems.append(f"{c['id']}: {e}")
    for f in OUT.glob("*.jpg"):
        if f.name not in {k["f"] for k in kept}:
            f.unlink()
    (ROOT / "content/photos.json").write_text(json.dumps(kept, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    lines = ["# Photo credits", "", "Every photo keeps its own licence, linked below. Cropped to 16:9 and resized; nothing else changed.", ""]
    lines += [f"- {k['place_en']} · {k['place_ar']} — {k['by']}, [{k['licence']}]({k['link']})" for k in kept]
    (ROOT / "docs/PHOTO-CREDITS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"kept {len(kept)}")
    for x in problems:
        print("!!", x)
    if len(kept) < 12:
        sys.exit("fewer than 12 photos: not enough for the rotation")


if __name__ == "__main__":
    main()
