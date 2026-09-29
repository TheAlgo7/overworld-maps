"""Builds the map sprites for the Android app.

1. android/app/src/main/assets/sprites/overworld{,@2x}.{png,json}
   Fill patterns only (Frontier's forest hatch and park stipple). Committed.
2. android/app/src/local/assets/sprites-local/overworld{,@2x}.{png,json}
   The same patterns plus RDR2 POI icons: the game's blip pictograms on black discs, the way the
   RDR2 map draws shops. Built from "Reference - RDR2/blips" (Rockstar's textures), so it lives in
   the gitignored local folder and is only used on personal builds. The app picks it up when present.
   GTA V's shop blips go in the same local sheet ("gta-*"), cut from the game's blip texture sheet
   in "Reference - GTA V/blips" (a 16x16 grid of 64 px icons: white pictograms, black outline).

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
}


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
    if len(local) > len(patterns):
        write(LOCAL, local, scale, suffix)
print("sprites ->", MAIN, "and", LOCAL if BLIPS.exists() or GTA_SHEET.exists() else "(no local blips)")
