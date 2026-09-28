"""Builds MapLibre SDF glyph files (.pbf) from TTF fonts, so map labels can use any font.

Output: android/app/src/main/assets/glyphs/<Stack>/<start>-<end>.pbf for the Latin ranges.
Follows the fontnik / font-maker conventions: 24 px glyphs, 3 px buffer, SDF radius 8,
cutoff 0.25, metrics top = glyph top minus the font's ascender.

Run: python tools/make_glyphs.py   (needs Pillow, numpy, scipy, fonttools)
Fonts (SIL Open Font License) go in tools/fonts/, from github.com/google/fonts/tree/main/ofl:
raleway/Raleway[wght].ttf, merriweather/Merriweather[opsz,wdth,wght].ttf,
crimsontext/CrimsonText-BoldItalic.ttf, homemadeapple/HomemadeApple-Regular.ttf
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.ndimage import distance_transform_edt

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "glyphs"
FONTS = ROOT / "tools" / "fonts"

# Stack name -> (font file in tools/fonts, variable-font instance or None)
STACKS = {
    "RalewayBlack": ("raleway.ttf", "Black"),
    "MerriweatherBlack": ("merriweather.ttf", "Black"),
    "CrimsonBoldItalic": ("crimson-bolditalic.ttf", None),
    "HomemadeApple": ("homemade-apple.ttf", None),
}
RANGES = [(0, 255), (256, 511), (8192, 8447)]

SIZE, BUFFER, RADIUS, CUTOFF, SCALE = 24, 3, 8.0, 0.25, 4


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def field(num: int, wire: int) -> bytes:
    return varint((num << 3) | wire)


def u32(num: int, v: int) -> bytes:
    return field(num, 0) + varint(v)


def s32(num: int, v: int) -> bytes:
    return field(num, 0) + varint(((v << 1) ^ (v >> 31)) & 0xFFFFFFFF)  # zigzag


def blob(num: int, data: bytes) -> bytes:
    return field(num, 2) + varint(len(data)) + data


def load(file: str, instance, size: int) -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(str(FONTS / file), size)
    if instance:
        f.set_variation_by_name(instance)
    return f


def glyph(ch: str, small: ImageFont.FreeTypeFont, big: ImageFont.FreeTypeFont, ascender: int) -> bytes | None:
    advance = round(small.getlength(ch))
    bbox = big.getbbox(ch, anchor="ls")  # left, top, right, bottom around the baseline origin, at SCALE x
    if bbox[2] <= bbox[0] or bbox[3] <= bbox[1]:
        # Blank glyph (space): metrics only.
        return u32(1, ord(ch)) + u32(3, 0) + u32(4, 0) + s32(5, 0) + s32(6, -ascender) + u32(7, advance)

    left = int(np.floor(bbox[0] / SCALE))
    top_px = int(np.floor(bbox[1] / SCALE))  # negative: above the baseline
    right = int(np.ceil(bbox[2] / SCALE))
    bottom = int(np.ceil(bbox[3] / SCALE))
    w, h = right - left, bottom - top_px

    # Render at SCALE x with the buffer, then build a signed distance field and sample it at 1x.
    pw, ph = (w + 2 * BUFFER) * SCALE, (h + 2 * BUFFER) * SCALE
    img = Image.new("L", (pw, ph), 0)
    ImageDraw.Draw(img).text(((BUFFER - left) * SCALE, (BUFFER - top_px) * SCALE), ch, font=big, fill=255, anchor="ls")
    inside = np.asarray(img) >= 128
    d_out = distance_transform_edt(~inside)
    d_in = distance_transform_edt(inside)
    signed = (d_out - d_in) / SCALE  # positive outside the glyph, in 1x pixels
    samples = signed[SCALE // 2 :: SCALE, SCALE // 2 :: SCALE][: h + 2 * BUFFER, : w + 2 * BUFFER]
    values = np.clip(255 - 255 * (samples / RADIUS + CUTOFF), 0, 255).astype(np.uint8)

    top = -top_px - ascender  # glyph top above the baseline, minus the ascender
    return (
        u32(1, ord(ch))
        + blob(2, values.tobytes())
        + u32(3, w)
        + u32(4, h)
        + s32(5, left)
        + s32(6, top)
        + u32(7, advance)
    )


def main() -> None:
    from fontTools.ttLib import TTFont

    for stack, (file, instance) in STACKS.items():
        cmap = TTFont(str(FONTS / file)).getBestCmap()
        small = load(file, instance, SIZE)
        big = load(file, instance, SIZE * SCALE)
        ascender = small.getmetrics()[0]
        folder = OUT / stack
        folder.mkdir(parents=True, exist_ok=True)
        total = 0
        for start, end in RANGES:
            glyphs = b""
            for code in range(start, end + 1):
                ch = chr(code)
                if code < 32 or code not in cmap:
                    continue
                try:
                    g = glyph(ch, small, big, ascender)
                except Exception:
                    g = None
                if g:
                    glyphs += blob(3, g)
            stack_msg = blob(1, stack.encode()) + blob(2, f"{start}-{end}".encode()) + glyphs
            data = blob(1, stack_msg)
            (folder / f"{start}-{end}.pbf").write_bytes(data)
            total += len(data)
        print(f"{stack}: {total // 1024} KB")


if __name__ == "__main__":
    main()
