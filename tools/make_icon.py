"""Builds the launcher icon: the India Gate hexagon in the GTA V theme with a purple route
(tools/icon/icon_map.png, rendered from the app's own style) under a two-tone purple arrow.

Writes adaptive icon layers to android/app/src/main/res/mipmap-*/ic_launcher_{background,
foreground,monochrome}.png and a flat 512 px Play Store icon to android/playstore-icon-512.png.
Run: python tools/make_icon.py   (needs Pillow)
"""
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
S = 1728  # 432 px (xxxhdpi, 108 dp) at 4x for smooth edges

# Background: the map, fading darker towards the edges.
bg = Image.open(ROOT / "tools" / "icon" / "icon_map.png").convert("RGB").resize((S, S), Image.LANCZOS)
vig = Image.new("L", (S, S), 0)
d = ImageDraw.Draw(vig)
for k in range(60):
    r = S * (0.78 - k * 0.006)
    d.ellipse([S / 2 - r, S / 2 - r, S / 2 + r, S / 2 + r], fill=int(255 * (k / 60) ** 1.6))
vig = vig.filter(ImageFilter.GaussianBlur(S * 0.03))
bg = Image.composite(bg, Image.new("RGB", (S, S), (8, 8, 10)), vig.point(lambda v: 90 + v * 165 // 255))

# Foreground: the arrow, GTA radar style split (lit left half, shaded right), white rim, glow.
fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
cx, cy = S / 2, S / 2 + S * 0.01
h = S * 0.235
w = h * 0.86
tip, lt, rt, notch = (cx, cy - h * 0.55), (cx - w / 2, cy + h * 0.45), (cx + w / 2, cy + h * 0.45), (cx, cy + h * 0.2)
shape = [tip, rt, notch, lt]
shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(shadow).polygon([(x, y + S * 0.016) for x, y in shape], fill=(0, 0, 0, 170))
fg.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(S * 0.02)))
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(glow).polygon(shape, fill=(164, 76, 242, 150))
fg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(S * 0.03)))
draw = ImageDraw.Draw(fg)
draw.line(shape + [shape[0]], fill=(255, 255, 255, 255), width=int(S * 0.036), joint="curve")
draw.polygon(shape, fill=(255, 255, 255, 255))
inner = Image.new("RGBA", (S, S), (0, 0, 0, 0))
di = ImageDraw.Draw(inner)
di.polygon([tip, notch, lt], fill=(190, 128, 250, 255))
di.polygon([tip, rt, notch], fill=(122, 47, 209, 255))
fg.alpha_composite(inner)

mono = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(mono).polygon(shape, fill=(255, 255, 255, 255))

for dens, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    folder = RES / f"mipmap-{dens}"
    os.makedirs(folder, exist_ok=True)
    bg.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_background.png")
    fg.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_foreground.png")
    mono.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_monochrome.png")

full = bg.convert("RGBA")
full.alpha_composite(fg)
full.resize((512, 512), Image.LANCZOS).save(ROOT / "android" / "playstore-icon-512.png")
print("icon written")
