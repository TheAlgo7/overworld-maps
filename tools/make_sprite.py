"""Builds the map sprites for the Android app.

1. android/app/src/main/assets/sprites/overworld{,@2x}.{png,json}
   Fill patterns only (Frontier's forest hatch and park stipple). Committed.
2. android/app/src/local/assets/sprites-local/overworld{,@2x}.{png,json}
   The same patterns plus RDR2 POI icons: the game's blip pictograms on black discs, the way the
   RDR2 map draws shops. Built from "Reference - RDR2/blips" (Rockstar's textures), so it lives in
   the gitignored local folder and is only used on personal builds. The app picks it up when present.
   GTA V's shop blips go in the same local sheet ("gta-*"), cut from the game's blip texture sheet
   in "Reference - GTA V/blips" (a 16x16 grid of 64 px icons: white pictograms, black outline).
   GTA VI's ("vi-*") come from "Reference - GTA VI/blips" (tools/cut_vi_blips.py).

Run: python tools/make_sprite.py   (needs Pillow)
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "android" / "app" / "src" / "main" / "assets" / "sprites"
LOCAL = ROOT / "android" / "app" / "src" / "local" / "assets" / "sprites-local"
BLIPS = ROOT / "Reference - RDR2" / "blips"
GTA_SHEET = ROOT / "Reference - GTA V" / "blips" / "blips_texturesheet_ng.png"
INK = (64, 66, 61)

# Map icon name -> RDR2 blip. The style's POI layer picks these by OSM category.
POIS = {
    "poi-food": "blip_grub",
    "poi-bar": "blip_saloon",
    "poi-doctor": "blip_shop_doctor",
    "poi-pharmacy": "blip_supplies_health",
    "poi-bank": "blip_proc_bank",
    "poi-post": "blip_post_office",
    "poi-hotel": "blip_hotel_bed",
    "poi-train": "blip_shop_train",
    "poi-barber": "blip_shop_barber",
    "poi-clothes": "blip_shop_tailor",
    "poi-repair": "blip_shop_blacksmith",
    "poi-store": "blip_shop_store",
    "poi-market": "blip_shop_market_stall",
    "poi-fuel": "blip_stable",
    "poi-police": "blip_ambient_sheriff",
    "poi-theatre": "blip_ambient_theatre",
    "poi-landmark": "blip_poi",
}


# Map icon name -> (row, column) in GTA V's blip sheet.
GTA_POIS = {
    "gta-bar": (2, 1),  # cocktail glass
    "gta-doctor": (0, 13),  # star of life
    "gta-pharmacy": (0, 8),  # pill
    "gta-bank": (2, 11),  # dollar
    "gta-barber": (1, 2),  # scissors (Herr Kutz)
    "gta-clothes": (1, 4),  # T-shirt (Binco, Suburban)
    "gta-repair": (1, 3),  # spray gun (Los Santos Customs)
    "gta-store": (0, 9),  # basket (24/7)
    "gta-fuel": (12, 3),  # petrol pump
    "gta-police": (0, 12),  # police shield
    "gta-theatre": (3, 11),  # film camera
    "gta-hotel": (14, 7),  # tower block (the safehouse house is kept for Home, below)
    "gta-worship": (5, 12),  # figure sitting in meditation
    "gta-landmark": (5, 7),  # photo camera
    "gta-parking": (7, 0),  # P
    "gta-airport": (8, 1),  # plane
}


# Map icon name -> (row, column) of GTA VI's blips, cut from Gaurav's sheet by tools/cut_vi_blips.py
# into "Reference - GTA VI/blips": rounded squares in pale tints with a dark slate border.
VI_BLIPS = ROOT / "Reference - GTA VI" / "blips"
VI_POIS = {
    "vi-food": (0, 0),  # knife and fork
    "vi-clothes": (0, 1),  # T-shirt
    "vi-barber": (0, 4),  # scissors
    "vi-store": (1, 0),  # basket
    "vi-bar": (1, 3),  # cocktail glass
    "vi-doctor": (1, 4),  # medical star, red rim
    "vi-pharmacy": (1, 4),
    "vi-repair": (2, 4),  # spray paint
    "vi-fuel": (3, 3),  # petrol pump
}


def vi(cell: tuple, scale: int) -> Image.Image:
    size = 22 * scale
    r, c = cell
    return Image.open(VI_BLIPS / f"r{r}c{c}.png").convert("RGBA").resize((size, size), Image.LANCZOS)


def gta(cell: tuple, scale: int) -> Image.Image:
    size = 22 * scale
    r, c = cell
    icon = Image.open(GTA_SHEET).convert("RGBA").crop((c * 64, r * 64, c * 64 + 64, r * 64 + 64))
    return icon.resize((size, size), Image.LANCZOS)


def hatch(scale):
    s = 16 * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for o in range(-s, s * 2, 6 * scale):
        d.line([(o, s), (o + s, 0)], fill=INK + (70,), width=scale)
    return img


def stipple(scale):
    s = 16 * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for x, y in [(3, 4), (11, 2), (7, 10), (14, 12), (1, 13)]:
        d.ellipse([x * scale, y * scale, x * scale + scale, y * scale + scale], fill=INK + (80,))
    return img


def poi(blip: str, scale: int) -> Image.Image:
    size = 24 * scale
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([scale, scale, size - scale - 1, size - scale - 1], fill=(27, 26, 26, 255))
    icon = Image.open(BLIPS / f"{blip}.png").convert("RGBA").resize((size, size), Image.LANCZOS)
    # The blips are white pictograms; tint them to RDR2's off-white (#E6E6E6).
    r, g, b, a = icon.split()
    tinted = Image.merge("RGBA", (r.point(lambda v: v * 230 // 255), g.point(lambda v: v * 230 // 255), b.point(lambda v: v * 230 // 255), a))
    img.alpha_composite(tinted)
    return img


def write(out: Path, parts: dict, scale: int, suffix: str) -> None:
    out.mkdir(parents=True, exist_ok=True)
    width = sum(p.width for p in parts.values())
    height = max(p.height for p in parts.values())
    sheet = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    index, x = {}, 0
    for name, img in parts.items():
        sheet.paste(img, (x, 0))
        index[name] = {"x": x, "y": 0, "width": img.width, "height": img.height, "pixelRatio": scale}
        x += img.width
    sheet.save(out / f"overworld{suffix}.png")
    (out / f"overworld{suffix}.json").write_text(json.dumps(index), encoding="utf-8")


for scale, suffix in [(1, ""), (2, "@2x")]:
    patterns = {"hatch": hatch(scale), "stipple": stipple(scale)}
    write(MAIN, patterns, scale, suffix)
    local = dict(patterns)
    if BLIPS.exists():
        local.update({name: poi(blip, scale) for name, blip in POIS.items()})
    if GTA_SHEET.exists():
        local.update({name: gta(cell, scale) for name, cell in GTA_POIS.items()})
    if (VI_BLIPS / "r0c0.png").exists():
        local.update({name: vi(cell, scale) for name, cell in VI_POIS.items()})
    if len(local) > len(patterns):
        write(LOCAL, local, scale, suffix)

# Your own places on the map, in each game's blip: GTA V's safehouse, briefcase and star (green, as
# GTA Online marks what you own), Red
# Dead's home, GTA VI's pink house. 96 px PNGs the app loads by name (map/SavedPlacesLayer.kt);
# where a game has no blip for one (Red Dead's work and star, GTA VI's work and star) the app
# draws its own icon in that game's blip style instead.
SAVED = ROOT / "android" / "app" / "src" / "local" / "assets" / "saved-blips"
saved = {}
if GTA_SHEET.exists():
    sheet = Image.open(GTA_SHEET).convert("RGBA")
    for kind, (r, c) in {"home": (0, 4), "work": (11, 9), "star": (7, 14)}.items():
        icon = sheet.crop((c * 64, r * 64, c * 64 + 64, r * 64 + 64)).resize((96, 96), Image.LANCZOS)
        # Green, as GTA Online marks a property you own; the black edge stays.
        px = icon.load()
        for y in range(icon.height):
            for x in range(icon.width):
                red, g, b, a = px[x, y]
                v = (red + g + b) / 765
                px[x, y] = (int(0x35 * v), int(0x9A * v), int(0x47 * v), a)
        saved[f"gta5-{kind}"] = icon
if BLIPS.exists():
    home = poi("blip_proc_home", 4)
    # A waypoint-red rim sets it apart from the shop discs around it.
    ImageDraw.Draw(home).ellipse([4, 4, 91, 91], outline=(0xCC, 0, 0, 255), width=6)
    saved["rdr2-home"] = home
if (VI_BLIPS / "r3c4.png").exists():
    saved["gta6-home"] = vi((3, 4), 4)
if saved:
    SAVED.mkdir(parents=True, exist_ok=True)
    for name, img in saved.items():
        img.save(SAVED / f"{name}.png")

print("sprites ->", MAIN, "and", LOCAL if len(local) > len(patterns) else "(no local blips)")
