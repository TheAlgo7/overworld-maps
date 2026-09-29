"""Builds the speed-limit signs from Gaurav's sign art (tools/signs/).

Each theme has two sheets of 12 finished signs (10, 20 ... 120 km/h): normal, and red for when
the driver is over the limit. Every sign is cut out as is, number and all, into
android/app/src/main/res/drawable-nodpi/limit_{gta,rdr}_<limit>{,_over}.png.

For limits the sheets don't have (25, 65...), the app draws the number itself on a blank sign made
here from the art (limit_blank_{gta,rdr}{,_over}.png): the "10" sign with its number painted out,
labels and ring kept. The number's size and centre are measured from the art and printed, so the
app places odd limits exactly where the art puts its numbers.

Run: python tools/make_limit_signs.py   (needs Pillow, numpy, scipy)
"""
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
SIGNS = ROOT / "tools" / "signs"
OUT = ROOT / "android" / "app" / "src" / "main" / "res" / "drawable-nodpi"
SIZE = 200  # px; drawn at up to 66 dp, so crisp up to ~3x
LIMITS = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120]

SHEETS = {
    "gta": "GTA V-inspired design.png",
    "gta_over": "GTA V-inspired design - Red borders.png",
    "rdr": "RDR 2 -inspired design.png",
    "rdr_over": "RDR 2 -inspired design - red borders.png",
}


def signs(sheet: Path) -> list[np.ndarray]:
    """The 12 signs on a sheet, in reading order, each centred on a transparent SIZE square."""
    rgba = np.asarray(Image.open(sheet).convert("RGBA"))
    labels, _ = ndimage.label(rgba[..., 3] > 128)
    boxes = [b for b in ndimage.find_objects(labels) if b[1].stop - b[1].start > 200]
    assert len(boxes) == 12, (sheet.name, len(boxes))
    # Reading order: rows by top edge (rows are ~320 px apart), then left to right.
    boxes.sort(key=lambda b: (round(b[0].start / 160), b[1].start))
    out = []
    for ys, xs in boxes:
        cell = rgba[ys, xs]
        side = max(cell.shape[:2])
        square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        square.paste(Image.fromarray(cell, "RGBA"), ((side - cell.shape[1]) // 2, (side - cell.shape[0]) // 2))
        out.append(np.asarray(square.resize((SIZE, SIZE), Image.LANCZOS)))
    return out


def number_box(sign: np.ndarray, theme: str) -> tuple[float, float, float, float]:
    """Bounding box (x0, y0, x1, y1) of the big number's bright ink, as shares of the sign."""
    lum = sign[..., :3].astype(float).mean(-1)
    h, w = lum.shape
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.hypot(yy - h / 2, xx - w / 2) / (w / 2)
    bright = (lum > 150) & (sign[..., 3] > 200) & (r < (0.66 if theme == "gta" else 0.70))
    # GTA: skip the small grey LIMIT / KM/H labels (the number is pure white, the labels grey).
    if theme == "gta":
        bright &= lum > 215
    labels, n = ndimage.label(bright)
    sizes = ndimage.sum(np.ones_like(labels), labels, range(1, n + 1))
    keep = np.isin(labels, [i + 1 for i, s in enumerate(sizes) if s > 40])
    ys, xs = np.nonzero(keep)
    return xs.min() / w, ys.min() / h, (xs.max() + 1) / w, (ys.max() + 1) / h


def blank(sign: np.ndarray, box: tuple[float, float, float, float]) -> np.ndarray:
    """The sign with its number painted out in the disc's own colour."""
    a = sign.copy()
    h, w = a.shape[:2]
    x0, y0, x1, y1 = box
    m = 0.03
    region = (slice(int((y0 - m) * h), int((y1 + m) * h) + 1), slice(int((x0 - m) * w), int((x1 + m) * w) + 1))
    patch = a[region]
    dark = patch[..., :3].astype(float).mean(-1) < 60
    fill = np.median(patch[dark][:, :3], axis=0) if dark.any() else np.array([20, 20, 20])
    patch[..., :3] = fill
    patch[..., 3] = 255
    return a


OUT.mkdir(parents=True, exist_ok=True)
for old in OUT.glob("limit_ring_rdr*.png"):
    old.unlink()
for key, file in SHEETS.items():
    theme, _, state = key.partition("_")
    suffix = "_" + state if state else ""
    cut = signs(SIGNS / file)
    for limit, img in zip(LIMITS, cut):
        Image.fromarray(img, "RGBA").save(OUT / f"limit_{theme}_{limit}{suffix}.png", optimize=True)
    # Where the art puts two- and three-digit numbers (averaged over the sheet).
    two = np.mean([number_box(s, theme) for s, l in zip(cut, LIMITS) if l < 100], axis=0)
    three = np.mean([number_box(s, theme) for s, l in zip(cut, LIMITS) if l >= 100], axis=0)
    Image.fromarray(blank(cut[0], number_box(cut[0], theme)), "RGBA").save(OUT / f"limit_blank_{theme}{suffix}.png", optimize=True)
    for name, b in (("2-digit", two), ("3-digit", three)):
        print(f"{key:9} {name}: centre ({(b[0] + b[2]) / 2:.3f}, {(b[1] + b[3]) / 2:.3f})  height {b[3] - b[1]:.3f}  width {b[2] - b[0]:.3f}")
print("limit signs ->", OUT)
