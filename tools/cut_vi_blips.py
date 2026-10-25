"""Cuts GTA VI's map blips out of the icon sheet Gaurav saved: a sheet of the game's blips, 5 x 7
rounded squares on a white page. Writes one transparent PNG per icon, r<row>c<col>.png, next to it in
"Reference - GTA VI/blips" (gitignored like every game file: personal builds only). make_sprite.py
builds the "vi-*" map icons from them.

Run: python tools/cut_vi_blips.py   (needs Pillow, numpy, scipy)
"""
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
DIR = ROOT / "Reference - GTA VI" / "blips"
SRC = DIR / "sheet.jpg"


def runs(profile, min_len=60):
    """Stretches where the page has ink, along one axis: the icon rows (or columns)."""
    out, start = [], None
    for i, v in enumerate(list(profile) + [False]):
        if v and start is None:
            start = i
        elif not v and start is not None:
            if i - start >= min_len:
                out.append((start, i))
            start = None
    return out


page = np.asarray(Image.open(SRC).convert("RGB")).astype(int)
ink = page.min(axis=2) < 232
rows = runs(ink.sum(axis=1) > 20)
cols = runs(ink.sum(axis=0) > 20)
print(f"{len(rows)} rows x {len(cols)} columns")
for r, (y0, y1) in enumerate(rows):
    for c, (x0, x1) in enumerate(cols):
        cell = ink[y0:y1, x0:x1]
        ys, xs = np.nonzero(cell)
        if len(ys) == 0:
            continue
        top, bottom = y0 + ys.min() - 2, y0 + ys.max() + 3
        left, right = x0 + xs.min() - 2, x0 + xs.max() + 3
        crop = page[top:bottom, left:right]
        # The page around the rounded square (and in its corners) is near-white and touches the
        # crop's edge; the icon's own pale fill sits inside the dark border, so it doesn't.
        white = crop.min(axis=2) >= 232
        labels, _ = ndimage.label(white)
        edge = set(np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]]))) - {0}
        outside = np.isin(labels, list(edge))
        # Soften the cut by one pixel so the border's anti-aliasing isn't lost.
        alpha = np.where(outside, 0, 255).astype(np.uint8)
        soft = ndimage.uniform_filter(alpha.astype(float), size=2)
        alpha = np.maximum(alpha, (soft * (~outside)).astype(np.uint8))
        rgba = np.dstack([crop.astype(np.uint8), alpha])
        Image.fromarray(rgba, "RGBA").save(DIR / f"r{r}c{c}.png")
print("icons ->", DIR)
