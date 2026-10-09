"""Builds the player markers from Gaurav's blip art (tools/blips/).

GTA V and GTA VI are his drawings as they are. Red Dead 2 is his teardrop dressed like the game's
blip_code_center_on_horse, the player marker while riding (which is what driving is): an off-white
fill (COLOR_GREYLIGHT from the game's colors.xml), a heavier dark outline and a soft shadow so it
stands off the parchment. The on-foot marker, blip_code_center, adds a dark ring in the round end;
the riding one has none, so neither does this.

Every marker is centred on a transparent square, never stretched: the map turns it around the
square's centre, which is the middle of the drawing, as the games turn their sprites. Output:
android/app/src/main/res/drawable-nodpi/puck_{gta5,rdr2,gta6}.png

Run: python tools/make_pucks.py   (needs Pillow, numpy, scipy)
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
BLIPS = ROOT / "tools" / "blips"
OUT = ROOT / "android" / "app" / "src" / "main" / "res" / "drawable-nodpi"
SIZE = 192  # px; drawn at up to about 30 dp, so crisp up to ~6x

INK = np.array([0x1B, 0x1A, 0x1A]) / 255  # RDR2 COLOR_OFFBLACK-ish outline
FILL = np.array([0xD5, 0xD3, 0xD2]) / 255  # RDR2 COLOR_GREYLIGHT, the player blip


def square(img: Image.Image, margin: float) -> Image.Image:
    """The drawing centred (by its ink box) on a transparent square with [margin] free all round."""
    box = img.getbbox()
    ink = img.crop(box)
    side = round(max(ink.size) * (1 + 2 * margin))
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    out.paste(ink, ((side - ink.width) // 2, (side - ink.height) // 2), ink)
    return out


def rdr2(path: Path) -> Image.Image:
    """His teardrop silhouette, dressed as the game's riding blip: off-white, outlined, shadowed."""
    src = np.asarray(Image.open(path).convert("RGBA")).astype(float) / 255
    # The drop touches the edges of his PNG; the outline is measured from the shape's edge, so it
    # needs clear space all round or it breaks where the drop meets the border.
    margin = 24
    shape = np.pad(src[..., 3], margin) > 0.5
    h, w = shape.shape
    ys, xs = np.nonzero(shape)
    top, bottom, left, right = ys.min(), ys.max(), xs.min(), xs.max()
    width = float(right - left + 1)
    height = float(bottom - top + 1)

    # Signed distance to his outline: an anti-aliased edge, and the outline drawn inside it so the
    # outer shape stays his.
    inside = ndimage.distance_transform_edt(shape)
    outside = ndimage.distance_transform_edt(~shape)
    alpha = np.clip(inside - outside + 0.5, 0, 1)
    outline = 0.075 * width
    fill_alpha = np.clip(inside - outline + 0.5, 0, 1)

    rgb = INK[None, None, :] * (1 - fill_alpha[..., None]) + FILL[None, None, :] * fill_alpha[..., None]
    out_alpha = alpha.copy()

    drop = Image.fromarray((np.dstack([rgb, out_alpha]) * 255).round().astype(np.uint8), "RGBA")

    # Soft shadow under it, a little lower, like the old marker's.
    pad = int(round(0.14 * height))
    canvas = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    shadow_mask = Image.fromarray((alpha * 255).astype(np.uint8), "L")
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow.paste(Image.new("RGBA", (w, h), (0, 0, 0, 56)), (pad, pad + int(round(0.02 * height))), shadow_mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(0.035 * width))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(drop, (pad, pad))
    return canvas


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    made = {
        "gta5": square(Image.open(BLIPS / "GTA 5 - Algo.png").convert("RGBA"), margin=0.04),
        "rdr2": square(rdr2(BLIPS / "RDR 2 - Algo.png"), margin=0.0),
        "gta6": square(Image.open(BLIPS / "GTA 6 - Algo.png").convert("RGBA"), margin=0.02),
    }
    for name, img in made.items():
        img.resize((SIZE, SIZE), Image.LANCZOS).save(OUT / f"puck_{name}.png", optimize=True)
        print(name, img.size, "->", SIZE)
    print("pucks ->", OUT)


main()
