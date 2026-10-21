#!/usr/bin/env python3
"""Draw the brand images: app icons, TV banners, the README hero. Run once after changing the design; the outputs are
committed. The mark: a drop of frankincense resin above the stepped merlons of an Omani fort, in resin amber."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
AMBER, AMBER_SOFT, INK, BG, BG2 = (227, 176, 98), (242, 214, 162), (247, 244, 238), (11, 16, 19), (36, 28, 18)
RED, GREEN, WHITE = (213, 43, 30), (26, 143, 75), (247, 244, 238)
BOLD = str(ROOT / "assets/native/Tajawal-ExtraBold.ttf")
MED = str(ROOT / "assets/native/Tajawal-Medium.ttf")


def background(w, h):
    im = Image.new("RGB", (w, h), BG)
    glow = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(glow)
    d.ellipse((int(w * .35), int(-h * .4), int(w * 1.25), int(h * 1.1)), fill=BG2)
    return Image.blend(im, glow.filter(ImageFilter.GaussianBlur(max(w, h) // 6)), 1.0)


def merlons(d, x0, y_base, width, unit, color):
    """A wall line with stepped merlons standing on it, like the crenel on the screen."""
    d.rectangle((x0, y_base - unit * .5, x0 + width, y_base), fill=color)
    x = x0 + unit
    while x + unit * 6 <= x0 + width:
        for step in range(3):
            d.rectangle((x + step * unit, y_base - (step + 1) * unit * 1.2, x + (6 - step) * unit, y_base), fill=color)
        x += unit * 10


def drop(d, cx, cy, r, color):
    """A resin drop: a circle with a pointed top."""
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color)
    d.polygon([(cx - r * .86, cy - r * .5), (cx, cy - r * 2.1), (cx + r * .86, cy - r * .5)], fill=color)


def icon(size):
    s = 4
    im = background(size * s, size * s)
    d = ImageDraw.Draw(im)
    S = size * s
    drop(d, S / 2, S * .44, S * .17, AMBER)
    d.ellipse((S * .43, S * .40, S * .49, S * .46), fill=AMBER_SOFT)
    merlons(d, S * .16, S * .80, S * .68, S * .022, AMBER)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=S * .22, fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def flag_band(d, x, y, w, h):
    third = w / 3
    for i, c in enumerate((RED, WHITE, GREEN)):
        d.rectangle((x + i * third, y, x + (i + 1) * third, y + h), fill=c)


def banner(w, h, sub=None):
    s = 3
    W, H = w * s, h * s
    im = background(W, H)
    d = ImageDraw.Draw(im)
    drop(d, W * .80, H * .43, H * .13, AMBER)
    merlons(d, W * .66, H * .80, W * .28, H * .02, AMBER)
    f = ImageFont.truetype(BOLD, int(H * .34), layout_engine=ImageFont.Layout.RAQM)
    d.text((W * .38, H * .40), "لُبان", font=f, fill=INK, anchor="mm", direction="rtl", language="ar")
    f2 = ImageFont.truetype(MED, int(H * .12))
    d.text((W * .38, H * .70), sub or "LUBAN", font=f2, fill=AMBER_SOFT, anchor="mm")
    flag_band(d, W * .38 - W * .06, H * .84, W * .12, H * .025)
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
    banner(1600, 600, "LUBAN · OMANI TV SCREEN").save(brand / "hero.png", optimize=True)
    icon(512).save(brand / "icon-512.png", optimize=True)
    print("brand images written")


if __name__ == "__main__":
    main()
