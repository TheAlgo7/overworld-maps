"""Render docs/readme/hero.png (1600x820) for the README from the real screenshots in docs/readme.

The screenshots themselves are captured by hand: the car shots from the Desktop Head Unit
(tools/dhu/tata_curvv.ini, cropped to the 1920x720 screen), the phone shots from the emulator
(`emulator -avd overworld -gpu host`, status bar in demo mode, test drives via DebugDriveReceiver).

Usage: python tools/readme_hero.py   (needs Playwright with Chrome)
"""
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ROOT / "docs" / "readme"
FONTS = ROOT / "android" / "app" / "src" / "main" / "res" / "font"
ICON = ROOT / "tools" / "icon" / "overworld-icon-master-1254.png"

HTML = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Barlow; src: url('{(FONTS / "barlow_regular.ttf").as_uri()}'); }}
@font-face {{ font-family: Barlow; font-weight: 600; src: url('{(FONTS / "barlow_semibold.ttf").as_uri()}'); }}
@font-face {{ font-family: BarlowCondensed; src: url('{(FONTS / "barlow_condensed_semibold.ttf").as_uri()}'); }}
* {{ margin: 0; box-sizing: border-box; }}
body {{ width: 1600px; height: 820px; overflow: hidden; color: #f3efe6; font-family: Barlow, sans-serif;
  background: radial-gradient(120% 90% at 78% 50%, #23211d 0%, #121212 55%, #0b0b0b 100%); }}
.wrap {{ display: flex; align-items: center; height: 100%; padding: 0 44px 0 64px; gap: 48px; }}
.text {{ flex: 1; }}
.icon {{ width: 128px; height: 128px; border-radius: 30px; display: block; margin-bottom: 34px;
  box-shadow: 0 18px 40px rgba(0,0,0,.55); }}
h1 {{ font-family: BarlowCondensed; font-weight: 400; font-size: 96px; line-height: .95; letter-spacing: .5px; }}
.lead {{ font-size: 30px; line-height: 1.3; color: #d8d2c4; margin-top: 22px; max-width: 470px; }}
.meta {{ font-size: 20px; line-height: 1.6; color: #8f8a80; margin-top: 30px; }}
.meta b {{ font-weight: 600; color: #b9b2a4; }}
.cars {{ display: flex; flex-direction: column; gap: 26px; }}
.car {{ width: 960px; height: 360px; border-radius: 18px; overflow: hidden; display: block;
  border: 1px solid rgba(255,255,255,.09); box-shadow: 0 24px 50px rgba(0,0,0,.6); }}
</style></head><body><div class="wrap">
  <div class="text">
    <img class="icon" src="{ICON.as_uri()}">
    <h1>Overworld<br>Maps</h1>
    <p class="lead">Real roads, drawn like an <span style="white-space: nowrap">open-world game map.</span></p>
    <p class="meta"><b>GTA V</b> and <b>Red Dead 2</b> worlds<br>Android Auto first, phone too</p>
  </div>
  <div class="cars">
    <img class="car" src="{(SHOTS / "car-gta-city.png").as_uri()}">
    <img class="car" src="{(SHOTS / "car-rdr.png").as_uri()}">
  </div>
</div></body></html>"""


def main():
    page_file = SHOTS / "_hero.html"
    page_file.write_text(HTML, encoding="utf-8")
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(channel="chrome")
            page = browser.new_page(viewport={"width": 1600, "height": 820})
            page.goto(page_file.as_uri())
            page.wait_for_load_state("networkidle")
            page.evaluate("document.fonts.ready")
            page.screenshot(path=str(SHOTS / "hero.png"))
            browser.close()
    finally:
        page_file.unlink(missing_ok=True)
    print("wrote", SHOTS / "hero.png")


if __name__ == "__main__":
    main()
