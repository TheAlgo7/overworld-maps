"""Cuts each game's generated icon set into the app.

Gaurav made 80 icons per game with ChatGPT's image generator (the prompts are in his assets folder,
"Ongoing Apps Assets/Overworld Maps/Icon Generation"): white pictograms with a black edge for GTA V,
and the same list for Red Dead and GTA VI. The originals go in "Reference - <game>/icons", named
"NN-name.png" by their number in the list (a "-v2" edit of one wins). Like the game blips they
stay out of the public repo: this writes android/app/src/local/assets/icons/<game>/<name>.png, and
the app falls back to the icons it draws in code when they're missing (ui/skin/IconArt.kt).

tools/make_sprite.py takes the map icons (places, your own places) from here too.

Run: python tools/make_icons.py   (needs Pillow)
"""
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "android" / "app" / "src" / "local" / "assets" / "icons"
SOURCES = {
    "gta5": ROOT / "Reference - GTA V" / "icons",
    "rdr2": ROOT / "Reference - RDR2" / "icons",
    "gta6": ROOT / "Reference - GTA VI" / "icons",
}

# The list's number -> the name the app asks for. Buttons are GameIcon names (lowercase); places
# are the map's POI kinds (prototype/themes.js); road alerts are RoadFeatureKind names.
NAMES = {
    1: "search", 2: "settings", 3: "layers", 4: "locate", 5: "north", 6: "close", 7: "back", 8: "star",
    9: "star_filled", 10: "home", 11: "work", 12: "recent", 13: "pin", 14: "route", 15: "navigate", 16: "overview",
    17: "sound_on", 18: "sound_off", 19: "zoom_in", 20: "zoom_out", 21: "car", 22: "traffic", 23: "buildings",
    24: "time_of_day", 25: "add_stop", 26: "end_trip", 27: "camera", 28: "nearby", 29: "share", 30: "edit",
    31: "delete", 32: "speedometer",
    33: "food", 34: "cafe", 35: "fastfood", 36: "bar", 37: "hospital", 38: "doctor", 39: "pharmacy", 40: "bank",
    41: "post", 42: "hotel", 43: "train", 44: "bus", 45: "barber", 46: "clothes", 47: "repair", 48: "store",
    49: "market", 50: "fuel", 51: "cng", 52: "charging", 53: "police", 54: "theatre", 55: "landmark", 56: "worship",
    57: "park", 58: "school", 59: "gym", 60: "parking", 61: "airport", 62: "toilet", 63: "mall", 64: "tyre",
    65: "my_home", 66: "my_work", 67: "my_star", 68: "my_recent", 69: "stop", 70: "destination",
    71: "traffic_light", 72: "speed_camera", 73: "accident", 74: "roadworks", 75: "closure", 76: "flooding",
    77: "lane_closed", 78: "speed_breaker", 79: "toll", 80: "school_zone",
}
# Buttons in the app are drawn at up to 32 dp: 144 px covers that on a 4.5x screen.
SIZE = 144
# Places the app also shows on its own panels (the Nearby chips, GameIcon's FUEL, FOOD...).
PANEL_PLACES = {"fuel", "food", "parking", "toilet", "hospital", "hotel"}
# Red Dead's buttons are ivory and its places ivory on a black disc; GTA VI's places are dark on a
# pale tile. The app tints what it draws on a panel, so those are written as plain white pictograms:
# a disc would vanish on the black panel, a tile would leave a pictogram half the size. Their map
# icons keep them.
PLAIN = {"rdr2": {name for n, name in NAMES.items() if n <= 32} | PANEL_PLACES, "gta6": PANEL_PLACES}
# The games whose pictogram is darker than what's behind it.
DARK_INK = {"gta6"}

# GTA VI's map icons are rebuilt around their pictograms to match the blip sheet Gaurav saved
# ("Reference - GTA VI/blips", tools/cut_vi_blips.py): its tints, one per kind, and its heavy rim.
VI_SLATE, VI_RED, VI_INK = (0x4F, 0x4D, 0x53), (0xC6, 0x5B, 0x61), (0x35, 0x33, 0x36)
VI_RIM, VI_CORNER = 0.066, 0.135
VI_ALERT = 0xE0DDE8
VI_PINK = 0xD28EB2
VI_TINTS = {
    "food": 0xB8BBC7, "cafe": 0xC0BAC8, "fastfood": 0xCFCCD3, "bar": 0xE3E4F7, "hospital": 0xEBD9DB, "doctor": 0xEBD9DB,
    "pharmacy": 0xEBD9DB, "bank": 0xE3E4DF, "post": 0xCBBFC9, "hotel": 0xF0E6E5, "train": 0xC9C4CA, "bus": 0xC9C4CA,
    "barber": 0xEDDDE0, "clothes": 0xA5989F, "repair": 0xA39CBD, "store": 0xA6A1AF, "market": 0xE9CFCB, "fuel": 0xB2B0BC,
    "cng": 0xB2B0BC, "charging": 0xA3ABD8, "police": 0xD2C8CD, "theatre": 0xB0ADDA, "landmark": 0xCEB7BD, "worship": 0xE0DEE9,
    "park": 0xE3E4DF, "school": 0xE9CFCB, "gym": 0x9A95A5, "parking": 0xB0A5B5, "airport": 0xE0DBE7, "toilet": 0xE0DEE9,
    "mall": 0xEADAE5, "tyre": 0xD2C8CD,
    "my_home": VI_PINK, "my_work": VI_PINK, "my_star": VI_PINK, "my_recent": VI_PINK, "stop": VI_PINK, "destination": VI_PINK,
}
VI_RED_RIM = {"hospital", "doctor", "pharmacy", "airport", "speed_camera", "accident", "closure"}
# Pictograms whose fine lines are what they say (the rupee, the cones, the tread): rounded, not thickened.
VI_FINE = {"cafe", "bank", "post", "hotel", "closure", "train", "bus", "repair", "market", "cng", "charging", "police", "theatre", "landmark", "worship", "school",
           "toilet", "tyre", "my_work", "my_recent", "destination", "traffic_light", "speed_camera", "accident", "roadworks", "flooding",
           "lane_closed", "toll", "school_zone"}
# Kinds the sheet draws itself: its own pictogram, cut from its (row, column), beats the generated one.
VI_SHEET = {"food": (0, 0), "clothes": (0, 1), "barber": (0, 4), "store": (1, 0), "gym": (1, 2), "bar": (1, 3), "hospital": (1, 4),
            "fastfood": (2, 0), "fuel": (3, 3), "my_home": (3, 4), "airport": (4, 2)}


def original(game: str, name: str) -> Path | None:
    """The latest edit of an icon: "63-shopping-mall-v2.png" over "63-shopping-mall.png"."""
    number = next((n for n, v in NAMES.items() if v == name), None)
    folder = SOURCES[game]
    if game == "gta6" and name in VI_SHEET:
        r, c = VI_SHEET[name]
        cut = folder.parent / "blips" / f"r{r}c{c}.png"
        if cut.exists():
            return cut
    if number is None or not folder.exists():
        return None
    # By the name's version, not the file's date: copying a set gives every file the same date.
    def edit(path: Path) -> int:
        version = re.search(r"-v(\d+)\.png$", path.name)
        return int(version.group(1)) if version else 1

    found = sorted(folder.glob(f"{number:02d}-*.png"), key=edit)
    return found[-1] if found else None


def plain(icon: Image.Image, dark: bool = False) -> Image.Image:
    """
    White on the pictogram, clear elsewhere: the icon without its disc. A [dark] pictogram is on a
    pale tile whose rim is as dark as it is, so only what's inside the rim counts.
    """
    grey = icon.convert("L")
    alpha = icon.getchannel("A")
    if dark:
        x0, y0, x1, y1 = alpha.point(lambda a: 255 if a > 8 else 0).getbbox()
        rim = round((x1 - x0) * 0.085)
        inside = Image.new("L", icon.size, 0)
        ImageDraw.Draw(inside).rounded_rectangle((x0 + rim, y0 + rim, x1 - rim, y1 - rim), radius=round((x1 - x0) * 0.1), fill=255)
        alpha = ImageChops.multiply(alpha, inside)
        # The tile is the commonest pale tone: from pale lilac down to the sheet's dark greys.
        counts = grey.histogram(mask=inside)
        tile = max(range(112, 256), key=counts.__getitem__)
        cut = tile - 24
        ink = grey.point(lambda v: max(0, min(255, (cut - v) * 255 // max(1, cut - 72))))
    else:
        ink = grey.point(lambda v: max(0, min(255, (v - 64) * 255 // 128)))
    white = Image.new("RGBA", icon.size, (255, 255, 255, 0))
    white.putalpha(ImageChops.multiply(alpha, ink))
    return white


def vi_tile(icon: Image.Image, name: str, sheet: bool) -> Image.Image:
    """
    GTA VI's blip around [icon]'s pictogram. The generated pictograms are finer than the sheet's, so
    they're thickened and rounded the way the sheet's are; the sheet's own only get smoothed, being
    small cuts. A square tile (the generated ones ran wide), so nothing is stretched.
    """
    size = 768
    glyph = plain(icon, dark=True).getchannel("A")
    x0, _, x1, _ = icon.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    glyph = glyph.crop(glyph.getbbox())
    scale = size / (x1 - x0)
    glyph = glyph.resize((max(1, round(glyph.width * scale)), max(1, round(glyph.height * scale))), Image.LANCZOS)
    pad = 64
    wide = Image.new("L", (glyph.width + 2 * pad, glyph.height + 2 * pad), 0)
    wide.paste(glyph, (pad, pad))
    if not sheet and name not in VI_FINE:
        wide = wide.filter(ImageFilter.MaxFilter(9))
    soft = wide.filter(ImageFilter.GaussianBlur(7 if sheet else 4 if name in VI_FINE else 11))
    wide = soft.point(lambda v: max(0, min(255, (v - 104) * 255 // 48)))
    box = wide.getbbox()
    wide = wide.crop(box)

    tint = VI_TINTS.get(name, VI_ALERT)
    tint = (tint >> 16, tint >> 8 & 255, tint & 255)
    edge = VI_RED if name in VI_RED_RIM else VI_SLATE
    big = size * 2
    shape = Image.new("L", (big, big), 0)
    ImageDraw.Draw(shape).rounded_rectangle((0, 0, big - 1, big - 1), radius=round(big * VI_CORNER), fill=255)
    rim = round(big * VI_RIM)
    core = Image.new("L", (big, big), 0)
    ImageDraw.Draw(core).rounded_rectangle((rim, rim, big - rim - 1, big - rim - 1), radius=round(big * (VI_CORNER - VI_RIM * 0.6)), fill=255)
    out = Image.new("RGBA", (big, big), edge + (0,))
    out.paste(edge + (255,), mask=shape)
    out.paste(tint + (255,), mask=core)
    out = out.resize((size, size), Image.LANCZOS)
    out.paste(VI_INK + (255,), ((size - wide.width) // 2, (size - wide.height) // 2), wide)
    return out


def art(game: str, name: str, size: int, margin: float = 0.06, panel: bool = False) -> Image.Image | None:
    """
    The icon trimmed to its edge and centred on a [size] square, [margin] clear all round. With
    [panel], the form the app draws on its own panels (see PLAIN).
    """
    path = original(game, name)
    if path is None:
        return None
    icon = Image.open(path).convert("RGBA")
    if panel and name in PLAIN.get(game, ()):
        icon = plain(icon, game in DARK_INK)
    elif game == "gta6" and next(n for n, v in NAMES.items() if v == name) > 32:
        icon = vi_tile(icon, name, path.parent.name == "blips")
    box = icon.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    if box:
        icon = icon.crop(box)
    room = size * (1 - 2 * margin)
    scale = room / max(icon.width, icon.height)
    icon = icon.resize((max(1, round(icon.width * scale)), max(1, round(icon.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(icon, ((size - icon.width) // 2, (size - icon.height) // 2))
    return out


if __name__ == "__main__":
    for game in SOURCES:
        made = 0
        for name in NAMES.values():
            img = art(game, name, SIZE, panel=True)
            if img is None:
                continue
            (OUT / game).mkdir(parents=True, exist_ok=True)
            img.save(OUT / game / f"{name}.png", optimize=True)
            made += 1
        print(f"{game}: {made} icons" + (f" -> {OUT / game}" if made else ""))
