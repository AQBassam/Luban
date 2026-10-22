#!/usr/bin/env python3
"""Fill photos/ with landscape photographs of Oman (1920x1080 JPEG) and write content/photos.json and
docs/PHOTO-CREDITS.md. Run by .github/workflows/photos.yml (it needs the network).

Source: Wikimedia Commons, the Featured, Quality and Valued images of Oman (peer-reviewed), only free licences (CC0, public
domain, CC BY, CC BY-SA), each credited on screen and in the credits file with author, licence and link.
Nothing is guessed: a photo without a clear free licence or author is skipped.
"""
import html
import io
import json
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
CATEGORIES = ["Category:Quality images of Oman", "Category:Featured pictures of Oman", "Category:Valued images of Oman"]
PER_PLACE = 5   # variety: no place fills the rotation
MAX = 40
# Chosen by the owner on 22 Oct 2026 from a Commons search per place, for places the categories above barely cover. Taken
# after the category photos (the first eight, which the APKs carry, stay as they are), under the same rules: a free licence,
# a named author, a landscape JPEG at least 1920 wide. File title, place in Arabic and English, and a credit to use when
# the file names its author only as a link.
PICKS = [
    ("File:Al Mughsail Beach 3.jpg", "المغسيل", "Mughsail", "Flickr 130649623@N05"),
    ("File:Al Mughsail Beach 2.jpg", "المغسيل", "Mughsail", "Flickr 130649623@N05"),
    ("File:Sur, Oman (7).jpg", "صور", "Sur", None),
    ("File:Sur Oman kz01.jpg", "صور", "Sur", None),
    ("File:Sur seen from the East.jpg", "صور", "Sur", None),
    ("File:Ras Al Jinz turtle reserve (14930327261).jpg", "رأس الجنز", "Ras al Jinz", None),
    ("File:Jebel Shams and goat.jpg", "جبل شمس", "Jebel Shams", None),
    ("File:Jebel Shams (2).jpg", "جبل شمس", "Jebel Shams", None),
    ("File:Ghul village and Wadi Nakhar.jpg", "وادي النخر", "Wadi an Nakhur", None),
    ("File:Wadi an Nakhur (36984475161).jpg", "وادي النخر", "Wadi an Nakhur", None),
    ("File:Wadi an Nakhur (36313125243).jpg", "وادي النخر", "Wadi an Nakhur", None),
    ("File:Khasab, Musandam, Oman - panoramio.jpg", "مسندم", "Musandam", None),
    ("File:Khasab, Musandam, Oman - panoramio (1).jpg", "مسندم", "Musandam", None),
    ("File:Bahla Fort.jpg", "حصن بهلاء", "Bahla Fort", None),
    ("File:Bahla fort and mountais.jpg", "حصن بهلاء", "Bahla Fort", None),
    ("File:Bahla Fort, Oman, 12th-15th centuries (31).jpg", "حصن بهلاء", "Bahla Fort", None),
    ("File:Nakhal Fort (2).jpg", "حصن نخل", "Nakhal Fort", None),
    ("File:Nakhal Fort (3).jpg", "حصن نخل", "Nakhal Fort", None),
    ("File:Nakhal Fort (1).jpg", "حصن نخل", "Nakhal Fort", None),
    ("File:Jebel Akhdar view.jpg", "الجبل الأخضر", "Jebel Akhdar", None),
    ("File:Jebel Akhdar – rocks at sunrise.jpg", "الجبل الأخضر", "Jebel Akhdar", None),
    ("File:Sunrise on Jebel Akhdar, Oman (36872522392).jpg", "الجبل الأخضر", "Jebel Akhdar", None),
    ("File:Sunrise on Jebel Akhdar, Oman (36646580840).jpg", "الجبل الأخضر", "Jebel Akhdar", None),
    ("File:Misfat Al Abriyeen (6817490443).jpg", "مسفاة العبريين", "Misfat al Abriyeen", None),
    ("File:Wadi Shab 2.jpg", "وادي شاب", "Wadi Shab", None),
    ("File:Wadi Shab, Oman.jpg", "وادي شاب", "Wadi Shab", None),
]
FREE = re.compile(r"^(CC0|Public domain|PD|CC BY(-SA)? [0-9.]+)", re.I)
# not a landscape for a TV background
SKIP = re.compile(r"insect|bird|butterfly|beetle|moth|lizard|gecko|spider|flower|portrait|interior|ceiling|"
                  r"detail|close-?up|macro|food|dish|coin|stamp|map|logo|exhibit|\bcar\b|aircraft|shell|specimen|conus|trochus|"
                  r"cypraea|clanculus|gastropod|fossil|mineral|crab|fish|snake|plant|leaf|seed", re.I)
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
        for p, ii, lic, author in described(titles[i:i + 40]):
            if SKIP.search(p["title"]):
                continue
            ar, en = place(p["title"])
            out.append(candidate(p, ii, lic, author, ar, en))
    seen, uniq = set(), []
    for c in out:   # a file can sit in more than one category
        if c["id"] not in seen:
            seen.add(c["id"]); uniq.append(c)
    # round-robin across places so every place gets its turn, named places first
    by_place = {}
    for c in sorted(uniq, key=lambda x: (not x["known"], x["place_en"])):
        by_place.setdefault(c["place_en"], []).append(c)
    picked, depth = [], 0
    while depth < PER_PLACE and len(picked) < MAX:
        for lst in by_place.values():
            if depth < len(lst) and len(picked) < MAX:
                picked.append(lst[depth])
        depth += 1
    # then the owner's picks, each once, with the place and credit given above
    chosen = {t: (ar, en, by) for t, ar, en, by in PICKS}
    have = {c["id"] for c in picked}
    for p, ii, lic, author in described(list(chosen)):
        ar, en, by = chosen[p["title"]] if p["title"] in chosen else (None, None, None)
        if ar is None or f"c{p['pageid']}" in have:
            continue
        picked.append(candidate(p, ii, lic, by or author, ar, en))
    return picked


def described(titles):
    """Each file's page and image info, kept only under the rules: JPEG, free licence, named author, a wide landscape."""
    j = api(action="query", titles="|".join(titles), prop="imageinfo", iiprop="url|size|extmetadata|mime", iiurlwidth="2400")
    for p in j["query"]["pages"]:
        ii = (p.get("imageinfo") or [{}])[0]
        meta = ii.get("extmetadata", {})
        lic = plain(meta.get("LicenseShortName", {}).get("value"))
        author = plain(meta.get("Artist", {}).get("value"))
        w, h = ii.get("width", 0), ii.get("height", 0)
        if ii.get("mime") != "image/jpeg" or not FREE.match(lic) or not author or w < 1920 or w < h * 1.3:
            continue
        yield p, ii, lic, author


def candidate(p, ii, lic, author, ar, en):
    return {"id": f"c{p['pageid']}", "url": ii.get("thumburl") or ii["url"], "by": author[:80], "licence": lic,
            "link": ii.get("descriptionurl", ""), "place_ar": ar, "place_en": en, "known": en != "Oman"}


def studio_backdrop(im):
    """True for an object shot on black: the whole border is near-black."""
    g = im.convert("L").resize((160, 90))
    w, h = g.size
    border = [g.getpixel((x, y)) for x in range(w) for y in (0, 1, h - 2, h - 1)] + \
             [g.getpixel((x, y)) for y in range(h) for x in (0, 1, w - 2, w - 1)]
    return sum(border) / len(border) < 28


def main():
    OUT.mkdir(exist_ok=True)
    candidates = commons_candidates()
    print(f"{len(candidates)} usable candidates")
    kept, problems = [], []
    for c in candidates:
        if len(kept) >= MAX + len(PICKS):
            break
        name = f"{c['id']}.jpg"
        dst = OUT / name
        try:
            if not dst.exists():
                im = ImageOps.exif_transpose(Image.open(io.BytesIO(get(c["url"])))).convert("RGB")
                if studio_backdrop(im):
                    problems.append(f"{c['id']}: plain dark backdrop (a specimen, not a landscape)")
                    continue
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
