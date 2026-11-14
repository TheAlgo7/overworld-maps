"""Marks the debug build's launcher icon with a small D inside the O, so "Overworld debug" (the
copy installed over USB for testing) can't be mistaken for the Play copy, on the phone or in the
car's launcher.

The D is cut in the icon's own language: straight strokes with 45 degree corners like the
octagonal O, in the O's silver, dimmed so it reads as part of the art. Run after make_icon.py:
  python tools/make_debug_icon.py
Writes android/app/src/debug/res/mipmap-*/ic_launcher_<icon>_{foreground,monochrome}.png for each of the
four launcher icons, which replace the main ones in debug builds only.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "android" / "app" / "src" / "main" / "res"
DEBUG = ROOT / "android" / "app" / "src" / "debug" / "res"
OPACITY = 0.62  # subtle: the O stays the mark, the D is a note inside it


def d_mask(size: int) -> Image.Image:
    """A chamfered D, centred, as an alpha mask for an icon layer of [size] px (drawn at 4x)."""
    big = size * 4
    u = big / 432  # the D is designed on the 432 px xxxhdpi layer
    h, w, t, c = 66 * u, 50 * u, 12 * u, 17 * u  # height, width, stroke, corner cut
    x0, y0 = big / 2 - w / 2 + 3 * u, big / 2 - h / 2
    x1, y1 = x0 + w, y0 + h
    outer = [(x0, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0, y1)]
    ci = c - t * 0.41  # the inner corner cut, so the diagonal strokes keep the same width
    inner = [(x0 + t, y0 + t), (x1 - t - ci, y0 + t), (x1 - t, y0 + t + ci), (x1 - t, y1 - t - ci), (x1 - t - ci, y1 - t), (x0 + t, y1 - t)]
    m = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(m)
    draw.polygon(outer, fill=255)
    draw.polygon(inner, fill=0)
    return m.resize((size, size), Image.LANCZOS)


ICONS = ["gta5", "rdr2", "gta6", "gta6_macca"]  # the four launcher icons (make_icon.py)

for folder in sorted(MAIN.glob("mipmap-*dpi")):
    target = DEBUG / folder.name
    target.mkdir(parents=True, exist_ok=True)
    for icon in ICONS:
        fg = Image.open(folder / f"ic_launcher_{icon}_foreground.png").convert("RGBA")
        arr = np.asarray(fg).astype(np.float32)
        opaque = arr[..., 3] > 250
        # The O's own colour: its left half (GTA V's right half turns purple).
        left = opaque & (np.arange(fg.width)[None, :] < fg.width // 2)
        silver = np.median(arr[left][:, :3], axis=0)
        mask = np.asarray(d_mask(fg.width)).astype(np.float32) / 255 * OPACITY

        out = arr.copy()
        a0 = out[..., 3:4] / 255
        a1 = mask[..., None]
        alpha = a1 + a0 * (1 - a1)
        out[..., :3] = np.where(alpha > 0, (silver * a1 + out[..., :3] * a0 * (1 - a1)) / np.maximum(alpha, 1e-6), out[..., :3])
        out[..., 3:4] = alpha * 255
        Image.fromarray(out.astype(np.uint8), "RGBA").save(target / f"ic_launcher_{icon}_foreground.png")

        # Themed (monochrome) icons get the D too, at full strength: Android tints them one colour.
        mono = Image.open(folder / f"ic_launcher_{icon}_monochrome.png").convert("RGBA")
        m = np.asarray(mono).astype(np.float32)
        m[..., 3] = np.maximum(m[..., 3], np.asarray(d_mask(mono.width)).astype(np.float32))
        m[..., :3] = 255
        Image.fromarray(m.astype(np.uint8), "RGBA").save(target / f"ic_launcher_{icon}_monochrome.png")
    print("wrote", target.name)
