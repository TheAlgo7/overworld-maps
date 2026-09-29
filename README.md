# Overworld Maps

Your real roads drawn like an open-world game map, with turn-by-turn navigation on the phone and on Android Auto. A personal app, built on free map data and free services.

Themes (a personal build, so they carry the games' names; rename before any public release):

| Theme | Look | Status |
|---|---|---|
| **GTA V** (`gta5`) | the pause map: near-black land, flat grey roads, grey blocks, ice-pale water, no labels, the game's shop blips (T-shirt, scissors, spray gun, 24/7 basket...), a flat waypoint-purple route, scale bar and area name bottom-left; GTA menus and HUD | active |
| **Red Dead 2** (`rdr2`) | parchment and ink: ink roads, dotted railways, pencil landmarks, hatched forest, paper grain, red route; Red Dead menus and HUD | active |
| **GTA VI** (`gta6`) | night-navy land, sand boulevards, coral route | paused until GTA VI's real map UI is public |

**Layout = Google Maps, look = the game.** Phone: search bar with a settings button on top, Home / Work / Saved chips, a Layers button and compass on the right, locate bottom-right, a place sheet (Directions, Save as Home / Work / star), a route preview (time, distance, via, traffic lights and incidents on the route, arrival, Start), then driving with a turn banner and road alerts on top, sound, overview and Layers buttons on the right, speedometer with the speed limit and the street name at the bottom, and a bottom sheet (end, time to go, distance, arrival, more). **Layers** is Google's layers sheet: pick the theme from two preview cards (one tap) and switch map details (live traffic, traffic lights, incidents). Settings is the game's pause menu: theme, car screen style, voice, test drive, Home, Work, clear recents, about. The map opens where the phone was last seen. Arrival times use a 12-hour clock.

**Android Auto (built for the Tata Curvv's 10.25-inch 1920x720 HARMAN screen):** our HUD owns the left, Android Auto the right. Idle: a themed "Where to?" card top-left; Saved and Theme in Android Auto's strip. Route preview from the phone: a themed card top-left with the time, via, traffic lights and **Start** / **Cancel** buttons drawn in the theme (map taps are passed through to them, since Android Auto's own strip hides after a few seconds), and the route fitted beside it. Driving: turn card with "Then" and road alerts top-left, time card and speedometer bottom-left, street name along the bottom, Android Auto's End and map buttons on the right. The route starts at the marker: the road already driven disappears, as in GTA V and on the RDR2 minimap. Voice guidance starts off (Settings turns it on and remembers). The locate button asks Android to turn location on when it is off. Red Dead's paper texture shows in the car too. To preview on the laptop: `desktop-head-unit.exe -c config\tata_curvv.ini` (preset in `tools/dhu/`, right-hand drive, copy it to the SDK's `extras\google\auto\config`).

**Where the GTA V values come from:** map colours measured from pause-map screenshots (land `#1e1e1e`, blocks `#424242`, roads `#b5b5b5`, water `#bcc7cd` with `#d6dee1` shore); the route and waypoint use the game's documented `HUD_COLOUR_WAYPOINT` `#A44CF2` (dark `#522679`); arrival time uses the health green `#359A47`. The arrow and waypoint shapes are redrawn as vectors from the radar sprites. Reference screenshots live in `Reference - GTA V/` (gitignored). The HUD font is Barlow Condensed as a free stand-in for GTA's commercial Chalet.

## What's in here

| Path | What it is |
|---|---|
| `android/` | The app (Kotlin, Jetpack Compose, Android Auto) |
| `prototype/` | Web preview of the themes on a recorded Delhi drive; open `prototype/index.html` |
| `prototype/themes.js` | **The single theme source.** Palettes in, MapLibre styles out, for the web and the app |
| `tools/export-android.mjs` | Writes the app's style files and `ThemeTokens.kt` from `themes.js` |
| `tools/make_sprite.py` | Builds Red Dead's hatch and stipple patterns and map blips |
| `tools/make_glyphs.py` | Builds Red Dead's map-label fonts as MapLibre SDF glyphs |
| `tools/make_icon.py` | Builds the adaptive app icon from the approved artwork in `tools/icon/` (silver-to-parchment O route, ivory arrow) |
| `tools/build-install.ps1` | Builds the debug APK and installs it |
| `tools/make_route.py` | Re-records the prototype's demo route |

After changing a theme in `themes.js`: `node tools/export-android.mjs`, then rebuild the app.

## How it works

- **Map:** MapLibre (through MapLibre Compose) drawing OpenFreeMap vector tiles with our own style JSON, bundled in the app.
- **Navigation:** [Ferrostar](https://github.com/stadiamaps/ferrostar) 0.57 (route following, rerouting, voice prompts, Android Auto templates).
- **Routing:** Valhalla on the public FOSSGIS server (`valhalla1.openstreetmap.de`). India drives on the left and it returns metric turn prompts.
- **Search:** Photon by komoot. Long-press the map to drop a pin instead.
- **Android Auto:** a navigation `CarAppService`. Android Auto draws the turn card, ETA and buttons; the app draws the themed map under them and tints the turn card with the theme colour. Distances on the dashboard are forced to metric.

### Road info

| What | Where it comes from | Needs |
|---|---|---|
| Traffic lights on the route | Valhalla `trace_attributes` on the same FOSSGIS server that routes (OpenStreetMap `highway=traffic_signals`), about a second per route | nothing |
| Speed cameras ahead | OpenStreetMap through Overpass, in small boxes along the route (Overpass is often busy; cameras simply arrive later) | nothing |
| Traffic on your route (amber slow, red jams, darkest where closed), like Google's route line | TomTom routing rebuilt along our own route (`supportingPoints`, `sectionType=traffic`) | TomTom key |
| Traffic on every road (off unless turned on in Layers): highways from city zoom, arterials from 13, streets from 14.5, only roads slower than usual | TomTom traffic-flow vector tiles, coloured per theme | TomTom key |
| Accidents, road works, closures, lane closures, flooding on the route (icons on the map and an alert under the turn banner; stationary traffic alerts too) | TomTom incident details, refreshed every 2.5 minutes while driving; only incidents that run along the route, not closed side streets it crosses | TomTom key |
| Time to go with traffic for our route, "+21 min of traffic" (travel time minus empty-road time), time coloured like Google's (neutral when unknown) | the same TomTom request as the route traffic | TomTom key |

**TomTom key (free, no card):** sign up at developer.tomtom.com, copy the default API key from the dashboard, add `tomtomKey=<key>` to `android/local.properties`, rebuild. The free tier gives 2,500 non-tile and 50,000 tile requests a day, far more than one driver uses. Without a key the app works as before and Layers shows those rows as off.

Running cost: nothing. The public routing and search servers are fair-use; fine for one driver, not for a public release.

## Build

Needs JDK 21 and the Android SDK (both are on this laptop: `%LOCALAPPDATA%\Android\Sdk`).

```powershell
powershell -File tools/build-install.ps1        # build and install on the connected phone or emulator
```

Or by hand from PowerShell: `cd android; .\gradlew.bat :app:assembleDebug` (APK: `android/app/build/outputs/apk/debug/app-debug.apk`). Don't call `gradlew.bat` from Git Bash: the space in "Overworld Maps" breaks it and the old APK stays in place without an obvious error.

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
adb shell am broadcast -n com.thealgothrim.overworld/.DebugDriveReceiver -a com.thealgothrim.overworld.DEBUG_DRIVE --es theme gta5 --ef lat 28.6129 --ef lng 77.2295 --es name "India%sGate"
adb shell am broadcast -n com.thealgothrim.overworld/.DebugDriveReceiver -a com.thealgothrim.overworld.DEBUG_STOP
```

Map icons and labels (arrow, waypoint, traffic lights, Red Dead's place names) don't draw on the emulator's default software GPU. Start the emulator on the laptop's GPU instead: `emulator -avd overworld -gpu host`.

## Put it in the car (one-time USD 25)

Android Auto will not show a sideloaded navigation app in a real car ([Google's testing docs](https://developer.android.com/training/cars/testing)). It has to come from Google Play, but **Internal App Sharing skips the review**:

1. Create a Google Play Console personal developer account (USD 25 once, identity check with a government ID).
2. Upload the APK at play.google.com/console > **Internal app sharing**. Internal sharing accepts debug builds. Create the app entry first if the Console asks.
3. On the phone: Play Store > Settings > About > tap **Play Store version** 7 times, then Settings > General > **Internal app sharing** on.
4. Open the upload link on the phone and install. Connect to the car; Overworld is in the Android Auto launcher.

## Known limits

- GTA V hides map labels, like the game's pause map. Red Dead's label fonts are SDF glyphs made by `tools/make_glyphs.py` (Latin ranges only).
- No offline maps yet (OpenFreeMap has no India extract download; Protomaps PMTiles would be the route).
- Android refuses the background location service if a trip starts while the app isn't on screen. The app then keeps navigating while the phone or car screen shows it, instead of crashing.
- MapLibre renders with **OpenGL**, not its default Vulkan: the Snapdragon Vulkan driver on the S24 Ultra failed to compile MapLibre's shaders and only the background drew (see `app/build.gradle`).
- Verified on the real phone and on Android Auto through the Desktop Head Unit (themed map, tinted turn card, metric ETA, live theme switching). Not yet in the real car, which needs the Play step above.

## Credits

Map data © OpenStreetMap contributors (ODbL). Tiles: OpenFreeMap / OpenMapTiles. Routing and traffic lights: Valhalla, FOSSGIS server. Speed cameras: Overpass API. Live traffic (optional): TomTom. Search: Photon by komoot. Navigation: Ferrostar by Stadia Maps (BSD 3-Clause, see `THIRD_PARTY_NOTICES.txt`). Fonts: Barlow, Barlow Condensed, IM Fell English, Chakra Petch (SIL Open Font License).
