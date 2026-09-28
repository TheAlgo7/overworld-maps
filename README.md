# Overworld Maps

Your real roads drawn like an open-world game map, with turn-by-turn navigation on the phone and on Android Auto. A personal app, built on free map data and free services.

Themes. The app draws everything itself: no game artwork, sprites or font files are bundled.

| Theme | Look | In the car | Status |
|---|---|---|---|
| **Metro Crime** (GTA V) | the GTA V pause map: near-black land, flat grey roads, grey blocks, ice-pale water, no labels, waypoint-purple route, two-tone radar arrow, four-petal waypoint | same, wider roads | active, matched 2026-09-28 |
| **Frontier** (RDR2) | parchment, ink roads, double-line highways, hatched forest, railway ties, paper grain | flat parchment, no grain or hatching | next to match |
| **Vice Coast** (GTA VI) | night-navy land, sand boulevards, cyan shorelines, coral route with glow | no glow | paused (`paused: true` in `themes.js`) until GTA VI's real map UI is public |

**GTA V phone UI (Metro Crime only):** planning is a GTA interaction menu (Pricedown header, white selected row, tick boxes, description box, key-prompt buttons). Driving shows the in-game HUD: help-text turn instruction, money-counter ETA, "Go to **place**." objective, waypoint distance with health/armour-style bars (trip progress, next turn), "Street | Area", an N badge that orbits as the map turns, a trip menu behind the M prompt, and an ARRIVED banner. Frontier keeps the plain UI until its RDR2 pass.

**Game fonts:** Pricedown, Chalet London 1960 and Chalet Comprime Cologne load from `android/app/src/local/assets/fonts/` (gitignored, personal use only). Without them the app falls back to Passion One, Barlow and Barlow Condensed.

**Where the GTA V values come from:** map colours measured from pause-map screenshots (land `#1e1e1e`, blocks `#424242`, roads `#b5b5b5`, water `#bcc7cd` with `#d6dee1` shore); the route and waypoint use the game's documented `HUD_COLOUR_WAYPOINT` `#A44CF2` (dark `#522679`); arrival time uses the health green `#359A47`. The arrow and waypoint shapes are redrawn as vectors from the radar sprites. Reference screenshots live in `Reference - GTA V/` (gitignored). The HUD font is Barlow Condensed as a free stand-in for GTA's commercial Chalet.

## What's in here

| Path | What it is |
|---|---|
| `android/` | The app (Kotlin, Jetpack Compose, Android Auto) |
| `prototype/` | Web preview of the themes on a recorded Delhi drive; open `prototype/index.html` |
| `prototype/themes.js` | **The single theme source.** Palettes in, MapLibre styles out, for the web and the app |
| `tools/export-android.mjs` | Writes the app's style files and `ThemeTokens.kt` from `themes.js` |
| `tools/make_sprite.py` | Builds Frontier's hatch and stipple patterns |
| `tools/make_route.py` | Re-records the prototype's demo route |

After changing a theme in `themes.js`: `node tools/export-android.mjs`, then rebuild the app.

## How it works

- **Map:** MapLibre (through MapLibre Compose) drawing OpenFreeMap vector tiles with our own style JSON, bundled in the app.
- **Navigation:** [Ferrostar](https://github.com/stadiamaps/ferrostar) 0.57 (route following, rerouting, voice prompts, Android Auto templates).
- **Routing:** Valhalla on the public FOSSGIS server (`valhalla1.openstreetmap.de`). India drives on the left and it returns metric turn prompts.
- **Search:** Photon by komoot. Long-press the map to drop a pin instead.
- **Android Auto:** a navigation `CarAppService`. Android Auto draws the turn card, ETA and buttons; the app draws the themed map under them and tints the turn card with the theme colour. Distances on the dashboard are forced to metric.

Running cost: nothing. The public routing and search servers are fair-use; fine for one driver, not for a public release.

## Build

Needs JDK 21 and the Android SDK (both are on this laptop: `%LOCALAPPDATA%\Android\Sdk`).

```bash
cd android
JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.12.8-hotspot" ./gradlew :app:assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

Tests (the Android Auto screen inside Google's car-app test host, with a real simulated trip) need an emulator or phone connected:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Put it on the phone

1. On the Samsung: Settings > About phone > Software information > tap **Build number** 7 times. Then Developer options > **USB debugging** on.
2. Plug into the laptop, accept the prompt on the phone.
3. `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`

The phone app works fully like this. Use **Test drive** to simulate a trip without driving.

## Try Android Auto on the laptop (no car needed)

Verified 2026-09-28 on a Galaxy S24 Ultra (Android 16, Android Auto 17.6).

1. Phone: Settings > Connected devices > **Android Auto** > scroll down, tap **Version** 10 times to unlock developer mode.
2. Android Auto's ⋮ menu > **Developer settings** > turn on **Unknown sources**.
3. Go back to the **main** Android Auto page, ⋮ menu > **Start head unit server**. (It is in that menu, not in the Developer settings list.)
4. Laptop, phone on USB. Run the DHU from a console window; it quits if its console has no input:
   ```bat
   adb forward tcp:5277 tcp:5277
   cd /d "%LOCALAPPDATA%\Android\Sdk\extras\google\auto" && desktop-head-unit.exe
   ```
5. Overworld is in the Android Auto app drawer (nine-dot button). Pick a destination on the phone; turn on **Test drive** to simulate the trip.

If the DHU window sits on "Waiting for phone", stop and start the head unit server again, then relaunch the DHU.

**Test drives from the laptop (debug builds only, works with the phone locked):**

```bat
adb shell am broadcast -n com.thealgothrim.overworld/.DebugDriveReceiver -a com.thealgothrim.overworld.DEBUG_DRIVE --es theme metro --ef lat 28.6129 --ef lng 77.2295 --es name "India%sGate"
adb shell am broadcast -n com.thealgothrim.overworld/.DebugDriveReceiver -a com.thealgothrim.overworld.DEBUG_STOP
```

Check map icons (arrow, waypoint) on the real phone or DHU: the emulator's software GPU does not draw runtime-added icons with the OpenGL renderer.

## Put it in the car (one-time USD 25)

Android Auto will not show a sideloaded navigation app in a real car ([Google's testing docs](https://developer.android.com/training/cars/testing)). It has to come from Google Play, but **Internal App Sharing skips the review**:

1. Create a Google Play Console personal developer account (USD 25 once, identity check with a government ID).
2. Upload the APK at play.google.com/console > **Internal app sharing**. Internal sharing accepts debug builds. Create the app entry first if the Console asks.
3. On the phone: Play Store > Settings > About > tap **Play Store version** 7 times, then Settings > General > **Internal app sharing** on.
4. Open the upload link on the phone and install. Connect to the car; Overworld is in the Android Auto launcher.

## Known limits

- Map labels use OpenFreeMap's Noto Sans glyphs. Themed map-label fonts need self-generated glyph files.
- No offline maps yet (OpenFreeMap has no India extract download; Protomaps PMTiles would be the route).
- Android refuses the background location service if a trip starts while the app isn't on screen. The app then keeps navigating while the phone or car screen shows it, instead of crashing.
- MapLibre renders with **OpenGL**, not its default Vulkan: the Snapdragon Vulkan driver on the S24 Ultra failed to compile MapLibre's shaders and only the background drew (see `app/build.gradle`).
- Verified on the real phone and on Android Auto through the Desktop Head Unit (themed map, tinted turn card, metric ETA, live theme switching). Not yet in the real car, which needs the Play step above.

## Credits

Map data © OpenStreetMap contributors (ODbL). Tiles: OpenFreeMap / OpenMapTiles. Routing: Valhalla, FOSSGIS server. Search: Photon by komoot. Navigation: Ferrostar by Stadia Maps (BSD 3-Clause, see `THIRD_PARTY_NOTICES.txt`). Fonts: Barlow, Barlow Condensed, IM Fell English, Chakra Petch (SIL Open Font License).
