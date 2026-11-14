"""Builds the launcher icons, one per world, from Gaurav's refined artwork (12 Nov 2026, the
"New Refined Logos" PSD), in tools/icon/:

  gta5.png        white O flowing into waypoint purple, grey arrow, on the dark street map
  rdr2.png        parchment O, red arrow, on the dark street map
  gta6.png        glowing white O on the Vice sunset with palms (the Play Store icon)
  gta6-macca.png  the same with Macca the Gator inside the O
  mask.png        the O and arrow in white on black (the same for every world)

Each artwork is a flat square raster, so it is cut into Android's adaptive layers with the mask:
  background  the artwork with the O painted out (inpainted), full bleed
  foreground  the artwork's own pixels where the O is (and Macca, found as what differs from
              gta6.png), so the icon reads exactly as drawn
  monochrome  the O and arrow as one white silhouette (Android tints themed icons)
Both are placed at the tested 85% of the 108 dp canvas: the mark spans 61% of the artwork, the
same as the first icon, so it is about 56 dp across, inside the 66 dp safe zone.

Writes, for each world <id>:
  android/app/src/main/res/mipmap-<dpi>/ic_launcher_<id>_{background,foreground,monochrome}.png
  android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_<id>.xml
  android/app/src/main/res/drawable-nodpi/app_icon_<id>.png  (Settings' picture of the icon)
plus ic_launcher.xml (the app's own icon: GTA VI) and the 512 px Play Store icons.
Run: python tools/make_icon.py   (needs Pillow, numpy, scipy, opencv-python), then make_debug_icon.py.
"""
import os
from pathlib import Path

import cv2
import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
ART = ROOT / "tools" / "icon"
PLACEMENT = 0.85
S = 1728  # 432 px (xxxhdpi, 108 dp) at 4x
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
ICONS = {"gta5": "gta5.png", "rdr2": "rdr2.png", "gta6": "gta6.png", "gta6_macca": "gta6-macca.png"}
APP_ICON = "gta6"  # the application's own icon and the Play Store's, his pick (13 Nov)

mask_luma = np.asarray(Image.open(ART / "mask.png").convert("L")).astype(np.float32)
# The mask's faint map lines sit under 45; the O is 255 with an antialiased rim.
mark_alpha = np.clip((mask_luma - 45) / (255 - 45), 0, 1)
core = mask_luma > 128
plain_vi = np.asarray(Image.open(ART / "gta6.png").convert("RGB")).astype(np.float32)


def grow(m: np.ndarray, px: int) -> np.ndarray:
    return ndimage.binary_dilation(m, iterations=px)


def feather(m: np.ndarray, sigma: float) -> np.ndarray:
    return np.clip(ndimage.gaussian_filter(m.astype(np.float32), sigma) * 1.6, 0, 1) if sigma else m.astype(np.float32)


def macca_region(art: np.ndarray) -> np.ndarray:
    """Where the Macca artwork differs from the plain GTA VI one: the gator, nothing else."""
    diff = np.abs(art - plain_vi).max(axis=2) > 14
    diff = ndimage.binary_closing(diff, iterations=4)
    diff = ndimage.binary_fill_holes(diff)
    labels, n = ndimage.label(diff)
    sizes = ndimage.sum(diff, labels, range(1, n + 1))
    keep = np.isin(labels, 1 + np.nonzero(sizes > 400)[0])
    return keep


def placed(layer: Image.Image, border) -> Image.Image:
    """The artwork at PLACEMENT of the canvas, centred; the rim outside it continues the edge."""
    size = round(S * PLACEMENT)
    pad = (S - size) // 2
    arr = np.asarray(layer.resize((size, size), Image.LANCZOS))
    out = cv2.copyMakeBorder(arr, pad, S - size - pad, pad, S - size - pad, border)
    return Image.fromarray(out, layer.mode)


def write_layers(icon_id: str, background: Image.Image, foreground: Image.Image, monochrome: Image.Image):
    for dens, px in DENSITIES.items():
        folder = RES / f"mipmap-{dens}"
        os.makedirs(folder, exist_ok=True)
        background.resize((px, px), Image.LANCZOS).save(folder / f"ic_launcher_{icon_id}_background.png", optimize=True)
        foreground.resize((px, px), Image.LANCZOS).save(folder / f"ic_launcher_{icon_id}_foreground.png", optimize=True)
        monochrome.resize((px, px), Image.LANCZOS).save(folder / f"ic_launcher_{icon_id}_monochrome.png", optimize=True)


def adaptive_xml(layers: str, note: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Built by tools/make_icon.py from tools/icon/{note}. -->\n"
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@mipmap/ic_launcher_{layers}_background" />\n'
        f'    <foreground android:drawable="@mipmap/ic_launcher_{layers}_foreground" />\n'
        f'    <monochrome android:drawable="@mipmap/ic_launcher_{layers}_monochrome" />\n'
        "</adaptive-icon>\n"
    )


# Settings' picture of each icon: what a launcher shows, the middle 72 of the 108 dp.
def preview(full: Image.Image) -> Image.Image:
    size = round(S * PLACEMENT)
    window = 72 / 108 * S / size  # share of the artwork inside the visible window
    w = full.width * window
    box = ((full.width - w) / 2, (full.height - w) / 2, (full.width + w) / 2, (full.height + w) / 2)
    return full.crop(tuple(round(v) for v in box)).resize((256, 256), Image.LANCZOS)


os.makedirs(RES / "mipmap-anydpi-v26", exist_ok=True)
os.makedirs(RES / "drawable-nodpi", exist_ok=True)
for icon_id, file in ICONS.items():
    art8 = np.asarray(Image.open(ART / file).convert("RGB"))
    art = art8.astype(np.float32)
    region = core.copy()
    if icon_id.endswith("macca"):
        region |= macca_region(art)
    # Background: the O (and Macca) painted out with what surrounds it, so a launcher's parallax
    # never shows a second O behind the first.
    hole = grow(region, 6).astype(np.uint8) * 255
    background = cv2.inpaint(art8, hole, 14, cv2.INPAINT_TELEA)
    # Foreground: the artwork's own pixels a hair beyond the O's edge, so its antialiasing and glow
    # come out exactly as drawn; the feather lands where background and artwork already agree.
    alpha = np.maximum(feather(grow(region, 3), 1.4), grow(region, 2))
    fg = np.dstack([art8, (alpha * 255).astype(np.uint8)])
    mono = (mark_alpha * 255).astype(np.uint8)

    bg_img = placed(Image.fromarray(background, "RGB"), cv2.BORDER_REFLECT)
    fg_img = placed(Image.fromarray(fg, "RGBA"), cv2.BORDER_CONSTANT)
    mono_img = placed(Image.fromarray(np.dstack([np.full_like(mono, 255)] * 3 + [mono]), "RGBA"), cv2.BORDER_CONSTANT)
    write_layers(icon_id, bg_img, fg_img, mono_img)
    (RES / "mipmap-anydpi-v26" / f"ic_launcher_{icon_id}.xml").write_text(adaptive_xml(icon_id, file), encoding="utf-8")
    preview(Image.fromarray(art8, "RGB")).save(RES / "drawable-nodpi" / f"app_icon_{icon_id}.png", optimize=True)

    # Check: background + foreground must give back the artwork.
    comp = background.astype(np.float32) * (1 - alpha[..., None]) + art * alpha[..., None]
    err = np.abs(comp - art).max(axis=2)
    print(f"{icon_id:11s} region {int(region.sum()):7d} px, rebuilt vs artwork: mean {err.mean():.2f}, 99.9% {np.percentile(err, 99.9):.1f}")

    # Play Store: the artwork itself, full square (Play rounds the corners itself).
    Image.fromarray(art8, "RGB").resize((512, 512), Image.LANCZOS).save(ROOT / "android" / f"playstore-icon-512-{icon_id}.png", optimize=True)

# The app's own icon (Settings > Apps, the share sheet, the car's launcher): GTA VI.
(RES / "mipmap-anydpi-v26" / "ic_launcher.xml").write_text(adaptive_xml(APP_ICON, ICONS[APP_ICON]), encoding="utf-8")
Image.open(ROOT / "android" / f"playstore-icon-512-{APP_ICON}.png").save(ROOT / "android" / "playstore-icon-512.png")
print("app icon:", APP_ICON)
