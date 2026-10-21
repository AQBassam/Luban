#!/usr/bin/env python3
"""Download the photos in content/photo-picks.json from Unsplash into photos/ (1920 wide JPEG, ~300 KB each) and
write content/photos.json (the list the screen rotates through) and docs/PHOTO-CREDITS.md.

Run by .github/workflows/photos.yml (this needs the network). Every photo is under the Unsplash License
(https://unsplash.com/license): free to use, credit given anyway on screen and in the credits file.
Photos that fail to download or are not landscape are left out and reported, never guessed.
"""
import io
import json
import sys
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
PICKS = json.loads((ROOT / "content/photo-picks.json").read_text(encoding="utf-8"))
OUT = ROOT / "photos"
UA = {"User-Agent": "Luban photo fetcher (github.com/AQBassam/Luban)"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read()


def main():
    OUT.mkdir(exist_ok=True)
    kept, problems = [], []
    for p in PICKS:
        dst = OUT / f"{p['id']}.jpg"
        try:
            if not dst.exists():
                raw = get(f"https://unsplash.com/photos/{p['id']}/download?force=true&w=2400")
                im = ImageOps.exif_transpose(Image.open(io.BytesIO(raw))).convert("RGB")
                if im.width < im.height * 1.2:
                    problems.append(f"{p['id']}: not landscape ({im.width}x{im.height})")
                    continue
                # crop to 16:9 around the centre, then 1920x1080
                tw = min(im.width, int(im.height * 16 / 9)); th = int(tw * 9 / 16)
                left, top = (im.width - tw) // 2, (im.height - th) // 2
                im = im.crop((left, top, left + tw, top + th)).resize((1920, 1080), Image.LANCZOS)
                im.save(dst, "JPEG", quality=80, optimize=True, progressive=True)
            kept.append({"f": dst.name, "by": p["by"], "place_ar": p["place_ar"], "place_en": p["place_en"]})
        except Exception as e:  # noqa: BLE001
            problems.append(f"{p['id']}: {e}")
    for f in OUT.glob("*.jpg"):   # photos no longer picked
        if f.name not in {k["f"] for k in kept}:
            f.unlink()
    (ROOT / "content/photos.json").write_text(json.dumps(kept, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    lines = ["# Photo credits", "", "All photos: [Unsplash License](https://unsplash.com/license). Thank you to every photographer.", ""]
    lines += [f"- [{k['place_en']}](https://unsplash.com/photos/{k['f'][:-4]}) — {k['by']}" for k in kept]
    (ROOT / "docs/PHOTO-CREDITS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"kept {len(kept)}/{len(PICKS)}")
    for x in problems:
        print("!!", x)
    if len(kept) < 12:
        sys.exit("fewer than 12 photos: not enough for the rotation")


if __name__ == "__main__":
    main()
