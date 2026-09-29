"""Builds the launcher icon from the approved artwork (tools/icon/overworld-icon-master-1254.png):
one continuous O-shaped route, silver flowing into parchment (city and frontier maps), with an
ivory arrow, on charcoal with a faint street grid and terrain contours.

The artwork is a flat raster, so this cuts it into Android's adaptive layers:
  background  charcoal and map detail with the mark painted out, full bleed
  foreground  the O and arrow on transparency, with a soft matte for the antialiased edge
  monochrome  one white silhouette of the O and arrow (Android tints themed icons)
Both are placed at the tested 85% of the 108 dp canvas, centred, which keeps the mark inside the
66 dp safe zone. Also copies the 512 px Play Store icon.

Writes android/app/src/main/res/mipmap-*/ic_launcher_{background,foreground,monochrome}.png and
android/playstore-icon-512.png. Run: python tools/make_icon.py   (needs Pillow, numpy, scipy)
"""
import os
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
MASTER = ROOT / "tools" / "icon" / "overworld-icon-master-1254.png"
PLACEMENT = 0.85
S = 1728  # 432 px (xxxhdpi, 108 dp) at 4x

art = np.asarray(Image.open(MASTER).convert("RGB")).astype(np.float32)
luma = art @ np.array([0.299, 0.587, 0.114], dtype=np.float32)
charcoal = np.median(art[luma < 30], axis=0)
bg_luma = float(charcoal @ np.array([0.299, 0.587, 0.114]))

# The mark: everything clearly lighter than the charcoal (it sits at 160-255, the map at 19-30).
core = luma > 150
dist, (iy, ix) = ndimage.distance_transform_edt(~core, return_indices=True)
edge = dist <= 4  # the antialiased rim, not the faint map lines further out
mark_luma = luma[iy, ix]
alpha = np.clip((luma - bg_luma) / np.maximum(mark_luma - bg_luma, 1), 0, 1) * edge
alpha[core] = 1
colour = np.where(core[..., None], art, art[iy, ix])  # rim pixels take the mark colour next to them

fg = np.dstack([colour, alpha * 255]).astype(np.uint8)
bg = np.where(edge[..., None], charcoal, art).astype(np.uint8)


def placed(layer: Image.Image, fill) -> Image.Image:
    size = round(S * PLACEMENT)
    canvas = Image.new(layer.mode, (S, S), fill)
    canvas.paste(layer.resize((size, size), Image.LANCZOS), ((S - size) // 2, (S - size) // 2))
    return canvas


background = placed(Image.fromarray(bg, "RGB"), tuple(int(c) for c in charcoal))
foreground = placed(Image.fromarray(fg, "RGBA"), (0, 0, 0, 0))
silhouette = foreground.getchannel("A")
monochrome = Image.merge("RGBA", (silhouette.point(lambda _: 255),) * 3 + (silhouette,))

for dens, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    folder = RES / f"mipmap-{dens}"
    os.makedirs(folder, exist_ok=True)
    background.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_background.png")
    foreground.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_foreground.png")
    monochrome.resize((px, px), Image.LANCZOS).save(folder / "ic_launcher_monochrome.png")

# Play Store: the artwork itself, full square (Play applies its own corners).
Image.fromarray(art.astype(np.uint8), "RGB").resize((512, 512), Image.LANCZOS).save(ROOT / "android" / "playstore-icon-512.png")
print("icon written; charcoal", charcoal.round(), "mark pixels", int(core.sum()))
