"""Render the README pictures in docs/readme from tools/readme (layouts.js + draw.js + assets/).

Stills: every layout in layouts.js becomes docs/readme/<name>.webp. A wide one for desktop and a
"-mobile" one that the README swaps in on screens up to 600 px wide. The script refuses to save a
picture if a font fell back or if any device (phone or car screen) comes within 12 px of an edge,
so every bezel stays whole.

Clips (animated WebP): drive.webp plays the same Connaught Place drive in GTA V, Red Dead 2 and GTA VI inside the car
screen, with a scan line wiping from one world to the next; real.webp is the dashboard of the real car.
Their source clips are the website's (Overworld Site/public/video); pass --videos if they live elsewhere.

The assets are real captures: car screens from the debug CarPreviewActivity at 1920x720 (scaled),
phones from the emulator with a demo status bar, both at public places, never near home.

Usage:  python tools/readme_images.py            (everything)
        python tools/readme_images.py hero       (one layout, or --clips for only the two clips)
Needs Playwright with Microsoft Edge, Pillow, numpy and ffmpeg on PATH.
"""
import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "tools" / "readme" / "page.html"
OUT = ROOT / "docs" / "readme"
VIDEOS = ROOT.parent / "Overworld Site" / "public" / "video"
MARGIN = 12

# The drive clip's screen inside the 'clip-frame' layout: x + bezel, y + bezel, 920 x 345, corner 2.2% of the width.
SCREEN = (20, 28, 920, 345)
SCREEN_RADIUS = 20
FPS = 12
WORLDS = [("car-gta5.mp4", None), ("car-rdr2.mp4", (200, 16, 27)), ("car-gta6.mp4", (251, 116, 165))]
WIPE = 0.45  # seconds


def layout_names(page):
    page.goto(PAGE.as_uri() + "?id=hero")
    return page.evaluate("Object.keys(window.LAYOUTS)")


def render(page, name):
    page.goto(PAGE.as_uri() + f"?id={name}")
    size = page.evaluate(f"(({{w, h}}) => [w, h])(window.LAYOUTS['{name}'])")
    page.set_viewport_size({"width": size[0], "height": size[1]})
    page.wait_for_load_state("networkidle")
    page.evaluate("document.fonts.ready")
    page.wait_for_function("[...document.images].every(i => i.complete && i.naturalWidth > 0)")
    has_text = page.evaluate("!!document.querySelector('.head, .sub, .brand')")
    for font in ['700 40px "Barlow Condensed"', '500 20px "Inter"', '600 20px "Inter"'] if has_text else []:
        if not page.evaluate(f"document.fonts.check('{font}')"):
            sys.exit(f"{name}: font fell back ({font}), nothing saved")
    margin = page.evaluate(f"window.LAYOUTS['{name}'].margin ?? {MARGIN}")
    boxes = page.evaluate("[...document.querySelectorAll('.device')].map(d => { const r = d.getBoundingClientRect(); return [r.left, r.top, r.right, r.bottom]; })")
    for l, t, r, b in boxes:
        if l < margin or t < margin or r > size[0] - margin or b > size[1] - margin:
            sys.exit(f"{name}: a device runs off the picture ({l:.0f}, {t:.0f}, {r:.0f}, {b:.0f} in {size[0]}x{size[1]}), nothing saved")
    png = OUT / f"{name}.png"
    page.locator("#c").screenshot(path=str(png))
    if name == "clip-frame":
        return png
    # WebP at 90 looks the same as the PNG at a sixth of the size (about 1.2 MB down to 200 KB),
    # which matters on a phone loading the README over mobile data.
    shot = OUT / f"{name}.webp"
    Image.open(png).convert("RGB").save(shot, quality=90, method=6)
    png.unlink()
    print(f"{shot.relative_to(ROOT)}  {size[0]}x{size[1]}  {shot.stat().st_size // 1024} KB")
    return shot


def frames(clip, folder, width, height, fps):
    folder.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(clip), "-vf", f"fps={fps},scale={width}:{height}:flags=lanczos",
                    str(folder / "%03d.png")], check=True)
    return sorted(folder.glob("*.png"))


def encode_clip(folder, fps, target, quality=72):
    # Animated WebP: a GIF of a map that moves every frame came out at 24 MB; this is a tenth of that.
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", str(fps), "-i", str(folder / "%03d.png"),
                    "-c:v", "libwebp_anim", "-lossless", "0", "-quality", str(quality), "-compression_level", "6",
                    "-loop", "0", str(target)], check=True)
    print(f"{target.relative_to(ROOT)}  {target.stat().st_size // 1024} KB")


def drive_clip(frame_png, videos, tmp):
    base = Image.open(frame_png).convert("RGB")
    x, y, w, h = SCREEN
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), SCREEN_RADIUS, fill=255)
    clips = [frames(videos / name, tmp / name, w, h, FPS) for name, _ in WORLDS]
    total = min(len(c) for c in clips)
    seg = total / len(WORLDS)
    out = tmp / "drive"
    out.mkdir()
    wipe = int(WIPE * FPS)
    for i in range(total):
        k = min(int(i / seg), len(WORLDS) - 1)
        screen = Image.open(clips[k][i]).convert("RGB")
        into = i - int(k * seg)
        if k > 0 and into < wipe:
            # the new world sweeps in from the left behind a scan line in its own route colour
            old = Image.open(clips[k - 1][i]).convert("RGB")
            edge = int(w * (into + 1) / wipe)
            old.paste(screen.crop((0, 0, edge, h)), (0, 0))
            arr = np.asarray(old).astype(np.float32)
            colour = np.array(WORLDS[k][1], dtype=np.float32)
            for dx in range(-14, 3):
                cx = edge + dx
                if 0 <= cx < w:
                    a = 0.9 if dx >= -2 else 0.9 * (1 + dx / 14) ** 2
                    arr[:, cx] = arr[:, cx] * (1 - a) + colour * a
            screen = Image.fromarray(arr.clip(0, 255).astype(np.uint8))
        frame = base.copy()
        frame.paste(screen, (x, y), mask)
        frame.save(out / f"{i:03d}.png")
    encode_clip(out, FPS, OUT / "drive.webp", quality=58)


def real_clip(videos, tmp):
    folder = tmp / "real"
    frames(videos / "real-gta5.mp4", folder, 360, 480, 12)
    encode_clip(folder, 12, OUT / "real.webp", quality=70)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--clips", action="store_true", help="only the two animated clips")
    ap.add_argument("--videos", type=Path, default=VIDEOS)
    args = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    tmp = Path(tempfile.mkdtemp(prefix="overworld-readme-"))
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(channel="msedge")
            page = browser.new_page(device_scale_factor=1)
            names = args.names or [n for n in layout_names(page) if n != "clip-frame"]
            if not args.clips:
                for name in names:
                    render(page, name)
            if args.clips or not args.names:
                frame_png = render(page, "clip-frame")
                shutil.move(str(frame_png), tmp / "clip-frame.png")
                browser.close()
                drive_clip(tmp / "clip-frame.png", args.videos, tmp)
                real_clip(args.videos, tmp)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
