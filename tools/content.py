#!/usr/bin/env python3
"""Build content/verses.json and content/hadith.json from public-domain sources. Nothing is typed by hand.

Verses: the Uthmani Hafs text (King Fahd Complex, via github.com/fawazahmed0/quran-api, Unlicense) for a curated
list of references below. Each entry keeps its reference so anyone can check it against a printed mushaf.
Hadith: the Forty Hadith of an-Nawawi (via github.com/fawazahmed0/hadith-api, Unlicense), each with its number.
Long texts that do not fit the card are kept out (MAX_WORDS).

    python3 tools/content.py            # download and write
    python3 tools/content.py --offline  # rebuild from the cached downloads in tools/.cache
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "tools" / ".cache"
QURAN = "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/ara-quranuthmanihaf.json"
NAWAWI = "https://raw.githubusercontent.com/fawazahmed0/hadith-api/1/editions/ara-nawawi.json"
MAX_WORDS = 60

# (surah, first ayah, last ayah). Chosen as complete thoughts that read well on their own.
VERSES = [
    (1, 1, 7), (2, 152, 152), (2, 153, 153), (2, 186, 186), (2, 201, 201), (2, 255, 255), (2, 286, 286),
    (3, 8, 8), (3, 26, 26), (3, 139, 139), (3, 173, 173), (3, 190, 191), (3, 200, 200),
    (6, 162, 163), (7, 23, 23), (9, 51, 51), (11, 88, 88), (12, 87, 87), (12, 101, 101),
    (13, 28, 28), (14, 7, 7), (14, 40, 41), (16, 97, 97), (16, 128, 128), (17, 23, 23), (17, 24, 24),
    (17, 80, 80), (18, 10, 10), (18, 46, 46), (20, 25, 28), (20, 114, 114), (21, 87, 87), (21, 89, 89),
    (23, 118, 118), (25, 63, 63), (25, 74, 74), (28, 24, 24), (29, 69, 69), (31, 17, 17), (33, 41, 42),
    (39, 10, 10), (39, 53, 53), (40, 44, 44), (40, 60, 60), (41, 34, 34), (42, 19, 19), (49, 10, 10),
    (49, 13, 13), (55, 1, 4), (55, 60, 60), (57, 4, 4), (59, 10, 10), (64, 16, 16), (65, 2, 3),
    (67, 1, 2), (73, 20, 20), (87, 1, 3), (93, 3, 5), (94, 5, 8), (97, 1, 5), (103, 1, 3),
    (108, 1, 3), (110, 1, 3), (112, 1, 4), (113, 1, 5), (114, 1, 6),
]

# Arabic surah names, in order (1..114)
SURAHS = ("الفاتحة البقرة آل_عمران النساء المائدة الأنعام الأعراف الأنفال التوبة يونس هود يوسف الرعد إبراهيم الحجر "
          "النحل الإسراء الكهف مريم طه الأنبياء الحج المؤمنون النور الفرقان الشعراء النمل القصص العنكبوت الروم لقمان "
          "السجدة الأحزاب سبأ فاطر يس الصافات ص الزمر غافر فصلت الشورى الزخرف الدخان الجاثية الأحقاف محمد الفتح "
          "الحجرات ق الذاريات الطور النجم القمر الرحمن الواقعة الحديد المجادلة الحشر الممتحنة الصف الجمعة "
          "المنافقون التغابن الطلاق التحريم الملك القلم الحاقة المعارج نوح الجن المزمل المدثر القيامة الإنسان "
          "المرسلات النبأ النازعات عبس التكوير الانفطار المطففين الانشقاق البروج الطارق الأعلى الغاشية الفجر "
          "البلد الشمس الليل الضحى الشرح التين العلق القدر البينة الزلزلة العاديات القارعة التكاثر العصر الهمزة "
          "الفيل قريش الماعون الكوثر الكافرون النصر المسد الإخلاص الفلق الناس").split()
assert len(SURAHS) == 114, len(SURAHS)


def fetch(url, name, offline):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / name
    if not offline or not path.exists():
        with urllib.request.urlopen(url, timeout=120) as r:
            path.write_bytes(r.read())
    return json.loads(path.read_text(encoding="utf-8"))


def words(s):
    return len(s.split())


def build_verses(offline):
    data = fetch(QURAN, "quran.json", offline)
    by_ref = {(v["chapter"], v["verse"]): v["text"].strip() for v in data["quran"]}
    out = []
    for s, a, b in VERSES:
        parts = []
        for n in range(a, b + 1):
            # this edition writes a space between an open tanween (U+08F0-08F2) and the alif after it: rejoin them
            t = re.sub(r"([\u08F0-\u08F2]) (?=[\u0627\u0649\u06E5])", r"\1", by_ref[(s, n)])
            if n == 1 and s not in (1, 9):  # the basmala is printed before the first ayah; it is not part of it
                t = re.sub(r"^بِسْمِ\s+[ٱا]للَّهِ\s+[ٱا]لرَّحْمَ[ٰـ]?نِ\s+[ٱا]لرَّحِيمِ\s*", "", t)
            parts.append(f"{t} ﴿{n}﴾" if b > a else t)
        text = " ".join(parts)
        if words(text) > MAX_WORDS:
            continue
        out.append({"ref": f"{s}:{a}" + (f"-{b}" if b > a else ""),
                    "surah": SURAHS[s - 1].replace("_", " "), "ayah": a if a == b else f"{a}–{b}", "text": text})
    return out


def build_hadith(offline):
    data = fetch(NAWAWI, "nawawi.json", offline)
    out = []
    for h in data["hadiths"]:
        text = " ".join(h["text"].split())
        if words(text) > MAX_WORDS + 25:
            continue
        out.append({"n": h["hadithnumber"], "text": text, "source": f"الأربعون النووية، الحديث {h['hadithnumber']}"})
    return out


def main():
    offline = "--offline" in sys.argv
    verses, hadith = build_verses(offline), build_hadith(offline)
    (ROOT / "content/verses.json").write_text(json.dumps(verses, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    (ROOT / "content/hadith.json").write_text(json.dumps(hadith, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"verses: {len(verses)}/{len(VERSES)}, hadith: {len(hadith)}/42")


if __name__ == "__main__":
    main()
