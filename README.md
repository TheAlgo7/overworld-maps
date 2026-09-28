# Overworld Maps

Your real roads drawn like an open-world game map, with turn-by-turn navigation on the phone and on Android Auto. A personal app, built on free map data and free services.

Three original themes (no game artwork, fonts, icons or names):

| Theme | Look | In the car |
|---|---|---|
| **Metro Crime** | asphalt blocks, pale grey roads, one magenta route | same, wider roads |
| **Frontier** | parchment, ink roads, double-line highways, hatched forest, railway ties, paper grain | flat parchment, no grain or hatching |
| **Vice Coast** | night-navy land, sand boulevards, cyan shorelines, coral route with glow | no glow |

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

1. Phone: Settings > Connected devices > **Android Auto** > scroll down, tap **Version** 10 times to unlock developer settings.
2. In Android Auto's three-dot menu: **Developer settings** > turn on **Unknown sources**, then **Start head unit server**.
3. Laptop, phone on USB:
   ```bash
   adb forward tcp:5277 tcp:5277
   "%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
   ```
4. Overworld appears in the launcher on that window. Pick a destination on the phone. Typing `autodrive` in the DHU console simulates driving.

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
- The car screen has been verified in Google's car-app test host and the phone on an emulator; it still needs a first run on the real head unit.

## Credits

Map data © OpenStreetMap contributors (ODbL). Tiles: OpenFreeMap / OpenMapTiles. Routing: Valhalla, FOSSGIS server. Search: Photon by komoot. Navigation: Ferrostar by Stadia Maps (BSD 3-Clause, see `THIRD_PARTY_NOTICES.txt`). Fonts: Barlow, Barlow Condensed, IM Fell English, Chakra Petch (SIL Open Font License).
