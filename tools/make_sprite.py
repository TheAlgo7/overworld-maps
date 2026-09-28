"""Builds the map sprite (fill patterns for Frontier) for the Android app:
android/app/src/main/assets/sprites/overworld{,@2x}.{png,json}
Matches patternImage() in prototype/themes.js.
Run: python tools/make_sprite.py   (needs Pillow)
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "android" / "app" / "src" / "main" / "assets" / "sprites"
OUT.mkdir(parents=True, exist_ok=True)
INK = (92, 88, 48)


def hatch(scale):
    s = 16 * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for o in range(-s, s * 2, 6 * scale):
        d.line([(o, s), (o + s, 0)], fill=INK + (107,), width=scale)
    return img


def stipple(scale):
    s = 16 * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for x, y in [(3, 4), (11, 2), (7, 10), (14, 12), (1, 13)]:
        d.rectangle([x * scale, y * scale, x * scale + max(1, scale) , y * scale + max(1, scale)], fill=INK + (90,))
    return img


for scale, suffix in [(1, ""), (2, "@2x")]:
    parts = {"hatch": hatch(scale), "stipple": stipple(scale)}
    width = sum(p.width for p in parts.values())
    sheet = Image.new("RGBA", (width, 16 * scale), (0, 0, 0, 0))
    index, x = {}, 0
    for name, img in parts.items():
        sheet.paste(img, (x, 0))
        index[name] = {"x": x, "y": 0, "width": img.width, "height": img.height, "pixelRatio": scale}
        x += img.width
    sheet.save(OUT / f"overworld{suffix}.png")
    (OUT / f"overworld{suffix}.json").write_text(json.dumps(index), encoding="utf-8")
print("sprite ->", OUT)
