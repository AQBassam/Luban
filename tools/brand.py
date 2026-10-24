#!/usr/bin/env python3
"""Draw the brand images: app icons, TV banners, the README hero. Run once after changing the design; the outputs are
committed.

The mark is the screensaver's dial in miniature: the rim of the navigators' star compass on the deep Indian Ocean,
the part of the day already lived in frankincense resin, and a lit grain of frankincense where "now" is, its smoke
rising. Oman shipped frankincense across this sea for three thousand years; the compass is how it travelled.
"""
import math
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DEEP, SEA = (6, 25, 42), (12, 46, 69)
RESIN, RESIN_SOFT, INK, GRAIN = (226, 169, 79), (243, 217, 164), (241, 235, 221), (247, 231, 196)
SEMI = str(ROOT / "assets/native/ElMessiri-SemiBold.ttf")
MED = str(ROOT / "assets/native/ElMessiri-Medium.ttf")
# the wordmark (docs/brand/v4): Noto Sans Arabic SemiBold outlines, refined with rhombus dots and slanted pen cuts
WORDMARK = ROOT / "docs/brand/v4/luban_wordmark_ink_v4.svg"


def wordmark(height, colour):
    """The wordmark as an RGBA image of the given height: its SVG path flattened and filled non-zero (letters that overlap
    stay filled; a contour wound the other way, the damma's hole, is cut out)."""
    svg = WORDMARK.read_text(encoding="utf-8")
    w, h = (float(v) for v in re.search(r'viewBox="0 0 ([\d.]+) ([\d.]+)"', svg).groups())
    d = re.search(r' d="([^"]+)"', svg).group(1)
    s = height * 4 / h
    W, H = int(w * s) + 2, int(height * 4) + 2
    contours = []
    for sub in re.findall(r"M[^M]+", d):
        pts, cur = [], None
        for cmd, args in re.findall(r"([MLQZ])([^MLQZ]*)", sub):
            v = [float(x) for x in re.findall(r"-?[\d.]+", args)]
            if cmd in "ML":
                cur = (v[0], v[1]); pts.append(cur)
            elif cmd == "Q":
                (cx, cy, x, y), (x0, y0) = v, cur
                for i in range(1, 13):
                    t = i / 12
                    pts.append(((1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t * t * x, (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t * t * y))
                cur = (x, y)
        contours.append((sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1])), pts))
    outer = max(contours, key=lambda c: abs(c[0]))[0] > 0   # the winding of the letters (the largest contour is one)
    fill, holes = Image.new("1", (W, H), 0), Image.new("1", (W, H), 0)
    for area, pts in contours:
        one = Image.new("1", (W, H), 0)
        ImageDraw.Draw(one).polygon([(x * s, y * s) for x, y in pts], fill=1)
        if (area > 0) == outer: fill = ImageChops.logical_or(fill, one)
        else: holes = ImageChops.logical_or(holes, one)
    mask = ImageChops.logical_and(fill, ImageChops.invert(holes))
    out = Image.new("RGBA", (W, H), colour + (255,))
    out.putalpha(mask.convert("L"))
    return out.resize((W // 4, H // 4), Image.LANCZOS)


def sea(w, h, cx, cy, rhumbs=True):
    """The deep sea, lighter around the rose, with a chart's rhumb lines running out of it."""
    im = Image.new("RGB", (w, h), DEEP)
    glow = Image.new("RGB", (w, h), DEEP)
    r = max(w, h) * .55
    ImageDraw.Draw(glow).ellipse((cx - r, cy - r, cx + r, cy + r), fill=SEA)
    im = glow.filter(ImageFilter.GaussianBlur(max(w, h) // 5))
    if rhumbs:
        lines = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(lines)
        L = w + h
        for i in range(32):
            a = i / 32 * 2 * math.pi
            d.line((cx, cy, cx + L * math.sin(a), cy - L * math.cos(a)), fill=RESIN + ((44 if i % 4 == 0 else 20),), width=max(1, w // 700))
        im = Image.alpha_composite(im.convert("RGBA"), lines).convert("RGB")
    return im


def mark(im, cx, cy, r, smoke=True):
    """Rim, the lived arc from Fajr round through noon, and the grain: clockwise, as the dial runs (now on the right)."""
    over = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    w = max(2, int(r * .06))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=INK + (90,), width=max(1, w // 2))
    band = max(3, int(r * .2))
    # PIL angles: 0 = 3 o'clock, clockwise. From lower left (Fajr) clockwise over the top to the right (now).
    d.arc((cx - r, cy - r, cx + r, cy + r), start=135, end=360, fill=RESIN + (255,), width=band)
    d.arc((cx - r, cy - r, cx + r, cy + r), start=0, end=135, fill=RESIN + (50,), width=band)
    a = math.radians(0)
    ex, ey = cx + (r - band / 2) * math.cos(a), cy + (r - band / 2) * math.sin(a)
    # glow
    g = Image.new("RGBA", im.size, (0, 0, 0, 0))
    gr = r * .55
    ImageDraw.Draw(g).ellipse((ex - gr, ey - gr, ex + gr, ey + gr), fill=(255, 160, 60, 150))
    g = g.filter(ImageFilter.GaussianBlur(gr * .45))
    base = Image.alpha_composite(im.convert("RGBA"), g)
    base = Image.alpha_composite(base, over)
    if smoke:
        # a soft plume and two fine threads that leave the grain together, then curl apart and fade as they rise
        for blur, width, alpha, threads in ((r * .06, r * .12, 40, ((.0, .5),)), (r * .01, r * .022, 150, ((.06, 0), (-.05, 2.4)))):
            sm = Image.new("RGBA", im.size, (0, 0, 0, 0))
            sd = ImageDraw.Draw(sm)
            for drift, ph in threads:
                prev = (ex, ey - r * .08)
                for j in range(1, 70):
                    q = j / 70
                    pt = (ex + math.sin(q * 5.2 + ph) * r * .09 * q + (drift + .12 * q) * r * q * 2, ey - r * .08 - q * r * 1.2)
                    sd.line((prev, pt), fill=(246, 236, 218, int(alpha * (1 - q) * min(1, q * 8))), width=max(1, int(width)))
                    prev = pt
            base = Image.alpha_composite(base, sm.filter(ImageFilter.GaussianBlur(max(1, blur))))
    gd = ImageDraw.Draw(base)
    gs = r * .13
    gd.ellipse((ex - gs, ey - gs, ex + gs, ey + gs), fill=GRAIN + (255,))
    gd.ellipse((ex - gs * .45, ey - gs * .55, ex + gs * .05, ey - gs * .1), fill=(255, 255, 255, 200))
    return base.convert("RGB")


def icon(size):
    s = 4
    S = size * s
    im = sea(S, S, S * .5, S * .56, rhumbs=False)
    im = mark(im, S * .5, S * .56, S * .3)
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, S - 1, S - 1), radius=S * .22, fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(im, (0, 0), m)
    return out.resize((size, size), Image.LANCZOS)


def banner(w, h, sub, sub_size=.1):
    s = 3
    W, H = w * s, h * s
    cx, cy = W * .80, H * .54
    im = sea(W, H, cx, cy)
    im = mark(im, cx, cy, H * .3)
    d = ImageDraw.Draw(im)
    wm = wordmark(int(H * .42), RESIN)
    im.paste(wm, (int(W * .38 - wm.width / 2), int(H * .42 - wm.height / 2)), wm)
    f2 = ImageFont.truetype(MED, int(H * sub_size))
    d.text((W * .38, H * .78), sub, font=f2, fill=RESIN_SOFT, anchor="mm")
    return im.resize((w, h), Image.LANCZOS)


def main():
    res = {"app": ROOT / "android/app/src/main/res", "launcher": ROOT / "android/launcher/src/main/res"}
    for name, base in res.items():
        for dpi, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
            p = base / f"mipmap-{dpi}" / "ic_launcher.png"
            p.parent.mkdir(parents=True, exist_ok=True)
            icon(px).save(p, optimize=True)
        p = base / "drawable-xhdpi" / "banner.png"
        p.parent.mkdir(parents=True, exist_ok=True)
        banner(320, 180, "SCREENSAVER" if name == "app" else "HOME").save(p, optimize=True)
    brand = ROOT / "docs/brand"
    brand.mkdir(parents=True, exist_ok=True)
    banner(1600, 600, "A TOUCH OF OMAN ON YOUR SCREEN", .055).save(brand / "hero.png", optimize=True)
    icon(512).save(brand / "icon-512.png", optimize=True)
    print("brand images written")


if __name__ == "__main__":
    main()
