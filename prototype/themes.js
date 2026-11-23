// Overworld theme engine.
// Each theme is a palette. buildStyle() turns it into a full MapLibre style
// (the same JSON spec MapLibre Native reads on Android and on the Android Auto
// surface), in two variants: the phone look and a calmer car-safe look.
// Data: OpenFreeMap vector tiles, OpenMapTiles schema, OpenStreetMap data.

(function () {
  const OFM = "https://tiles.openfreemap.org";
  const NAME = ["coalesce", ["get", "name_en"], ["get", "name:latin"], ["get", "name"]];

  // Palettes follow the September 2026 design research (GTA V, Red Dead 2, GTA VI).
  // A personal build: named after the games. Rename before any public release.
  // GTA VI's minimap as the game draws it since the launch, measured 23 Nov from Gaurav's 31 launch
  // captures (Desktop\GTA6_Minimap_Screenshots, 4K walkthrough frames; the method and numbers are in
  // "Ongoing Apps Assets\Overworld Maps\GTA VI\Minimap - launch measurements.md"). It is frosted
  // glass: the world shows through at about 47%, so every colour here is the minimap as it reads over
  // a typical world behind it (a fit over all 45 samples), not its raw layer. Green open ground,
  // grey built-up ground, pale roads, khaki-yellow interstates with faint lane lines, plain slate
  // blocks (no windows), teal water, khaki sand, no names, one pink route (#FB74A5). The earlier
  // palette came from the pre-launch "Extended Look" trailer (pale warm grey, no green): wrong.
  // GTA VI's radar, the look on a phone trip; the pause map is the theme's own palette.
  // The minimap's layer colours (fitted from the captures) as they read over a sunlit world: most
  // captures had shade or foliage behind the minimap, and composited over that the day map looked
  // like dusk (Gaurav, 23 Nov). Over mid-grey sunlit ground (#6A6A6A) instead: sage-green open land,
  // silver-grey built-up ground, roads a step lighter, light slate blocks, teal water.
  // 23 Nov night, from his two in-game daylight shots: by day the minimap is near-white (pale grey
  // ground with only a hint of green, whitish roads, light cyan water). Composited over mid-grey it
  // read as dusk ("much darker, feels like dusk"). Blocks without the dark roof rim and only a
  // shade off the ground ("tearing my eyes how sharp"): no MSAA here, so contrast is what shows the
  // jagged edge.
  const VI_MINIMAP = {
    land: "#b6c2ba", residential: "#c3c7c9", industrial: "#c3c7c9",
    park: "#adc0b3", wood: "#a6b9ac", grass: "#afc1b5", sand: "#d8d4c2",
    water: "#97c6d0", waterway: "#97c6d0",
    building: "#b9bfc6", buildingLine: null,
    // Raised blocks: real heights where OpenStreetMap has them, a low default where it doesn't.
    // Plain slate, soft and see-through like the game's frosted blocks: the ground shows through,
    // the roof only a little lighter than the walls and no darkening down them. Solid with full
    // light and a gradient, their top edges cut hard against the walls (Gaurav, 23 Nov: "so sharp").
    // The rim: capture 34's roof #43505C inside a #4C5458 to #555A5D edge, darker and greyer than
    // the roof, scaled to our block colour.
    buildingShadow: { color: "#8b9196", opacity: 0.32, offset: [2, 3], blur: 5 },
    buildings3d: { color: "#b9bfc6", opacity: 0.7, heightScale: 1, minHeight: 4, gradient: false, rim: null },
    // Flyovers seen from the minimap's 60 degree tilt: a concrete side under the deck and its
    // shadow on the ground (see bridgeLayers).
    bridge: { lift: 5.0, face: 1.7, faceColor: "#9ea3a1", edge: "#c5cac8", shadow: { color: "#2c302d", opacity: 0.5 } },
    light: { color: "#ffffff", intensity: 0.22 },
    // Railways: two light blue-grey rails (#546368 over #415C4B ground in capture 24, scaled to here).
    rail: "#a9b4bb", railDouble: true,
    // A soft shore: the water's own colour blurred a little out over the land (captures 32, 46 have
    // no hard edge), and pale solid footpaths between the blocks (34).
    waterLine: "#97c6d0", waterLineBlur: 3, waterLineOpacity: 1,
    trails: { color: "#d4d8d7", solid: true },
    // Roads are broad pale bands with no outlines (the junction in capture 27), the palest thing on
    // the map: the same contrast to the ground as the game (#787D77 on #4A6C53 there), and wide, since
    // the game's bands are several times wider for the screen than a lane drawn to scale.
    // Interstates are a warm khaki-grey (captures 24, 25, 26, 29: "I-97").
    roadScale: 2.4,
    roads: {
      // Interstates as in capture 24, across: green, a grey shoulder, yellow lanes, a thin dark
      // median, yellow lanes, a grey shoulder. The dark edge (casing) makes the median where the two
      // carriageways meet; the shoulders are stripes along each carriageway's edges.
      motorway: { fill: "#dddbd0", casing: "#b9bcbc" },
      major: { fill: "#e3e6e5", casing: null },
      mid: { fill: "#e1e4e3", casing: null },
      minor: { fill: "#dfe2e1", casing: null },
      service: { fill: "#d9dcdb", casing: null },
    },
    laneDashes: null,
    laneStripes: { color: "#e6e8e8", groups: ["motorway"], share: 0.13, middle: false, minzoom: 13 },
    glow: null,
    labels: false,
    label: {
      road: "#3a3640", roadHalo: "#d2c9c4", roadFont: "Noto Sans Regular",
      place: "#2e2a33", placeHalo: "#beb6b1", placeFont: "Noto Sans Bold", placeSpacing: 0.08, upper: true,
      water: "#2f5560", waterHalo: "#739ba2",
    },
    // A flat pink ribbon (#FB74A5) with a thin brighter pink edge each side (#FFA5D4, about a
    // tenth of its width), the same by day, at golden hour and at night; it fades in just
    // ahead of the arrow.
    route: { line: "#fb74a5", casing: "#ffa5d4", edge: 0.1, glow: null, fade: 130 },
    // Shops and services as GTA VI's blips: pale rounded squares with a slate rim (red for
    // medical), Gaurav's generated set (tools/make_icons.py), or the sheet he saved
    // (tools/cut_vi_blips.py) where a build has only that; personal builds only. Every kind the
    // set has an icon for, except bus stops and shops OpenStreetMap doesn't classify.
    pois: { prefix: "vi-", minzoom: 15.2, size: 0.85,
      only: ["cafe", "fastfood", "food", "bar", "hospital", "doctor", "pharmacy", "bank", "post", "hotel", "train", "barber",
        "clothes", "repair", "store", "market", "fuel", "charging", "police", "theatre", "landmark", "worship", "parking",
        "airport", "school", "gym", "toilet", "mall", "tyre"] },
    // The pause map's sea depth and shore rim belong to the pause map, not the minimap.
    depth: null,
    waterLine: null,
    hillshade: null,
    viNames: null,
    fonts: null,
  };
  // Golden hour: the launch captures at dusk (05, 12, 14) read almost as by day, so it is the day's
  // palette 12% dimmer with a touch of sunset warmth (#FFAA78 at 4%), not the much darker map the
  // trailer suggested.
  const VI_MINIMAP_DUSK = {
    land: "#687e6f", residential: "#7f7f80", industrial: "#7f7f80",
    park: "#6a8071", wood: "#627766", grass: "#6b8172", sand: "#9f9881",
    water: "#597f87", waterway: "#597f87",
    building: "#5e646c",
    buildingShadow: { color: "#3f423f", opacity: 0.32, offset: [2, 3], blur: 5 },
    buildings3d: { color: "#767a81", opacity: 0.8, heightScale: 1, minHeight: 4, gradient: false, rim: null },
    bridge: { lift: 5.0, face: 1.7, faceColor: "#6b6e6a", edge: "#9ea29d", shadow: { color: "#1e1c1a", opacity: 0.55 } },
    light: { color: "#fff0e2", intensity: 0.22 },
    rail: "#858d92",
    waterLine: "#597f87",
    trails: { color: "#888c88", solid: true },
    roads: {
      motorway: { fill: "#959081", casing: "#7a7976" },
      major: { fill: "#8e928e", casing: null },
      mid: { fill: "#8c8f8b", casing: null },
      minor: { fill: "#898d89", casing: null },
      service: { fill: "#828582", casing: null },
    },
    laneDashes: null,
    laneStripes: { color: "#939595", groups: ["motorway"], share: 0.13, middle: false, minzoom: 13 },
  };
  // Night: navy-violet ground, grey-violet roads, slate-blue buildings, teal grass and open land,
  // near-black navy water. Ground, buildings and greens corrected 23 Nov from a 4K night drive at
  // launch (ground #28253D, buildings #354256 to #484D66, open land #244B4B to #295055). Roads a little
  // lighter and greyer since the launch footage (22 Nov: #53545e to #605961 at night, in Gaurav's
  // research folder), so they read against the ground on a dark car screen.
  const VI_MINIMAP_NIGHT = {
    // Measured 23 Nov from a 4K night drive through Vice City (Gaurav's GTA6_ViceCity_Night_Screenshots,
    // 18 minimap crops), the frost taken out and put back over dark asphalt (#26262C): navy built-up
    // ground, pale lavender-grey roads, dark teal open land, periwinkle water inside a dark plum shore,
    // slate-blue blocks with a lighter roof and a thin edge between roof and walls.
    land: "#2b2e47", residential: "#2b2e47", industrial: "#2a2d45",
    park: "#26504f", wood: "#22474a", grass: "#264e51", sand: "#3a3540",
    water: "#55557c", waterway: "#55557c",
    building: "#3a425c",
    // Plain blocks, no windows, soft and see-through as by day. The roof is lighter than the walls
    // (#505A74 against #3F4761) with a thin edge between them (#49516B), frames 10 and 14.
    buildingShadow: { color: "#0b0d1a", opacity: 0.32, offset: [2, 3], blur: 5 },
    buildings3d: { color: "#404a63", opacity: 0.82, heightScale: 1, minHeight: 4, gradient: false, rim: null },
    // Stacked ramps at night stand apart on near-black gaps (Extended Look 20:22, #160f1f
    // between the decks): dark, wider kerbs and a deeper shadow, where the day has pale kerbs.
    bridge: {
      lift: 5.0, face: 1.7, faceColor: "#1a1421", edge: "#17101f", edgeWidth: [1.1, 2.8, 4.8],
      shadow: { color: "#120c18", opacity: 0.75 },
    },
    light: { color: "#ddd4ee", intensity: 0.2 },
    rail: "#5a5868",
    // The pond in frame 16 sits in a dark plum rim (#3C3B44 seen).
    waterLine: "#3b3a48",
    trails: { color: "#55545f", solid: true },
    roads: {
      motorway: { fill: "#605f6b", casing: "#3c3a45" },
      major: { fill: "#605f6d", casing: null },
      mid: { fill: "#5e5d6b", casing: null },
      minor: { fill: "#5a5967", casing: null },
      service: { fill: "#52515e", casing: null },
    },
    // No lane dashes at night (none in the 18 frames); grey shoulders on the interstates as by day.
    laneDashes: null,
    laneStripes: { color: "#6a6976", groups: ["motorway"], share: 0.13, middle: false, minzoom: 13 },
  };

  const THEMES = {
    // GTA V. Colours measured 2026-09-28 from Gaurav's screenshots of the game's pause map
    // ("Game" style) and in-game radar: near-black land, flat grey roads with no outlines,
    // grey building blocks, no green, pale ice water, no labels, purple GPS route.
    gta5: {
      id: "gta5",
      skin: "gta",
      name: "GTA V",
      blurb: "Near-black land, flat grey roads, ice-pale water, one purple GPS route. The GTA V map.",
      page: "#111111",
      dark: true,
      map: {
        land: "#1e1e1e", residential: "#1e1e1e", industrial: "#1e1e1e",
        park: "#202020", wood: "#202020", grass: "#202020", sand: "#202020",
        water: "#bcc7cd", waterway: "#bcc7cd",
        waterLine: "#d6dee1",
        // The pause map's sea in hard terraces, measured 22 Nov along a line from the south-west
        // coast out to sea (Screenshot_1): light water at the shore, then steps of slate-blue packed
        // close down the drop-off to a flat dark deep. Seascape's depth areas (10, 20, 30, 50 m and
        // on) stand in for the game's terraces; shallower than 10 m keeps the water colour above,
        // which is also the game's lakes (#b4c1c6 to #c4ced3).
        depth: {
          // Darker sooner than the real depths would put it, so the sea goes slate within a few
          // bands of the coast as in the game (India's west coast shelves for 100 km).
          bands: [
            [10, "#a4b5b9"], [20, "#8e9fa9"], [30, "#7c8e98"], [50, "#687c85"], [100, "#5b6f78"], [200, "#4f636c"],
            [500, "#475862"], [1000, "#405158"], [2000, "#3a4b52"],
          ],
        },
        building: "#424242", buildingLine: null,
        // Flyovers seen from the radar's 45 degree tilt (see bridgeLayers).
        bridge: { lift: 4.0, face: 1.4, faceColor: "#626262", edge: "#7d7d7d", shadow: { color: "#000000", opacity: 0.6 } },
        rail: "#c4cfd5",
        roads: {
          motorway: { fill: "#bcbcbc", casing: null },
          major: { fill: "#b5b5b5", casing: null },
          mid: { fill: "#b5b5b5", casing: null },
          minor: { fill: "#b5b5b5", casing: null },
          service: { fill: "#9b9d9d", casing: null },
        },
        glow: null,
        labels: false,
        label: {
          road: "#dcdcd6", roadHalo: "#1c1f21", roadFont: "Noto Sans Regular",
          place: "#f0f0ea", placeHalo: "#16181a", placeFont: "Noto Sans Bold", placeSpacing: 0.08, upper: true,
          water: "#a9c3cf", waterHalo: "#2e4756",
        },
        // HUD_COLOUR_WAYPOINT (164, 76, 242), drawn flat with no outline like the pause map and radar.
        route: { line: "#a44cf2", casing: "#a44cf2", glow: null },
        // Blips: white pictograms with a black edge, from Gaurav's generated GTA V set
        // (tools/make_icons.py), or the game's own blips where a build has only those. Every kind
        // the set has an icon for, except bus stops and unclassified shops: Delhi has one every few
        // metres. Small, as on the game's radar: about two thirds of the player marker.
        pois: { prefix: "gta-", minzoom: 14.5, size: 0.72,
          only: ["cafe", "fastfood", "food", "bar", "hospital", "doctor", "pharmacy", "bank", "post", "hotel", "train",
            "barber", "clothes", "repair", "store", "market", "fuel", "charging", "police", "theatre", "landmark",
            "worship", "parking", "airport", "school", "gym", "toilet", "mall", "tyre"] },
      },
      variants: {
        // The radar while driving, not the pause map: GTA V's minimap is see-through, so it reads
        // lighter than the pause map's near-black, with dimmer roads and blocks barely apart from
        // the ground. Measured 13 Nov from Gaurav's screenshot 08 (city at night, tilted radar):
        // ground #3d3e3e, blocks #3c3e40, main road #747677; out of town (09) the ground goes olive.
        // The app uses it on a trip (OverworldViewModel.mapVariant).
        radar: {
          page: "#2f3131",
          dark: true,
          map: {
            land: "#3a3c3c", residential: "#3a3c3c", industrial: "#3a3c3c",
            park: "#454b42", wood: "#434940", grass: "#474d44", sand: "#4e4c46",
            building: "#434547",
            bridge: { lift: 4.0, face: 1.4, faceColor: "#5a5c5c", edge: "#6c6e6e", shadow: { color: "#000000", opacity: 0.45 } },
            roads: {
              motorway: { fill: "#7d7f80", casing: null },
              major: { fill: "#76787a", casing: null },
              mid: { fill: "#747677", casing: null },
              minor: { fill: "#747677", casing: null },
              service: { fill: "#646667", casing: null },
            },
          },
        },
      },
      // Radar arrow (radar_centre): white left half, #b3b3b3 right half, black outline. The app
      // draws Gaurav's own drawing of it (tools/blips/); these colours are the web preview's.
      // Waypoint (radar_waypoint): four pointed petals in the waypoint colour, dark ring centre.
      marker: { fill: "#ffffff", shade: "#b3b3b3", stroke: "#000000", shape: "radar" },
      blip: { shape: "quatrefoil", fill: "#a44cf2", center: "#3a3a3a", stroke: "#000000" },
      hud: {
        // HUD panels: black at ~73%. Health green for arrival, waypoint purple for accents.
        bg: "rgba(0,0,0,0.73)", fg: "#fefefe", sub: "#b5b5b5", accent: "#a44cf2", good: "#359a47",
        border: "rgba(255,255,255,0.08)", font: "'Barlow Condensed', sans-serif", fontKey: "condensed", weight: 600, upper: true,
        card: "#522679",
      },
      overlay: null,
    },

    // RDR2. Cartography palette from Lee Martin's RDR2 map analysis (parchment, ink, water,
    // contour, pencil); navigation colours from the game's colors.xml: user waypoint and GPS
    // route COLOR_RED #CC0000, objective COLOR_YELLOW #FEF390, player COLOR_GREYLIGHT #D5D3D2.
    rdr2: {
      id: "rdr2",
      skin: "rdr",
      name: "Red Dead 2",
      blurb: "Parchment and ink: the RDR2 map. Ink roads, dotted railways, pencil landmarks, a red GPS route.",
      page: "#1b1611",
      dark: false,
      map: {
        land: "#dec29b", residential: "#dec29b", industrial: "#d8bb93",
        park: "#d5b98e", wood: "#cfb287", grass: "#d5b98e", sand: "#e3cca3",
        water: "#9e9985", waterway: "#9e9985",
        waterLine: "#8c8775", waterLineBlur: 4, waterLineOpacity: 0.85,
        building: "#e4cfaa", buildingLine: "#4d473f",
        rail: "#4d473f", railStyle: "dots", railDot: "#ebddc0",
        roadScale: 1.1,
        // Warm charcoal ink, measured 13 Nov from Gaurav's Red Dead Online captures (#4d473f to
        // #554f47, captures 02, 13, 17). Town streets are solid in the game (Saint Denis, 16), so
        // service lanes are too; only footpaths are dashed, like its trails.
        roads: {
          motorway: { fill: "#4d473f", casing: null },
          major: { fill: "#4d473f", casing: null },
          mid: { fill: "#4d473f", casing: null },
          minor: { fill: "#514b43", casing: null },
          service: { fill: "#57514a", casing: null },
        },
        trails: { color: "#4d473f", dash: [1.6, 1.6] },
        // A paper map seen from straight above: the surveyor's bridge, the road in a band of bare
        // paper with an ink parapet line along each side (see parapetLayers).
        bridge: { edge: "#4d473f", gap: true },
        patterns: { wood: "hatch", park: "stipple" }, patternsInCar: true, railDotsInCar: true,
        glow: null,
        roadLabels: false,
        // Black-disc blips, small as on the game's map: Gaurav's generated Red Dead set
        // (tools/make_icons.py), or the game's own where a build has only those. Every kind the set
        // has an icon for, except bus stops (Delhi has one every few metres).
        pois: { prefix: "poi-", size: 0.68,
          only: ["cafe", "fastfood", "food", "bar", "hospital", "doctor", "pharmacy", "bank", "post", "hotel", "train", "barber",
            "clothes", "repair", "store", "shop", "market", "fuel", "charging", "police", "theatre", "landmark", "worship", "parking",
            "airport", "school", "gym", "toilet", "mall", "tyre"] },
        // Android map lettering (bundled SDF glyphs, see tools/make_glyphs.py). The web preview
        // falls back to OpenFreeMap's Noto fonts below.
        fonts: { place: "RalewayBlack", city: "MerriweatherBlack", water: "CrimsonBoldItalic", pencil: "HomemadeApple" },
        label: {
          road: "#40423d", roadHalo: "#dec29b", roadFont: "Noto Sans Italic",
          place: "#40423d", placeHalo: "rgba(222,194,155,0.7)", placeFont: "Noto Sans Bold", placeSpacing: 0.22, upper: true,
          water: "#3b3d38", waterHalo: "rgba(158,153,133,0.6)", pencil: "#716454",
        },
        // No outline: the game's route is a chalky red band inside the road (captures 23-24); the
        // app gives it the grain (ThemedLayers' chalk pattern).
        route: { line: "#cc0000", casing: null, glow: null, chalk: true },
        // Towns in heavy slab capitals (BLACKWATER, RIGGS STATION), regions in huge letters spread
        // across the map (T H E  H E A R T L A N D S): captures 02, 11, 15, 25.
        townLabels: true,
        // Wildlife sketches in the open country, as on the game's paper map (captures 02, 11): here
        // what lives around Delhi, drawn for Overworld (tools/animals), by the kind of ground.
        animals: true,
      },
      variants: {
        // The radar while riding, not the pause map: the game's minimap is parchment at about 40%
        // over the world, so on a ride it reads as a dim olive-brown with soft, wide road bands and
        // no names. Measured 13 Nov: fill #6a6352 to #6f6550 over ground (captures 03, 21, 28),
        // roads #48423a to #524c3d. Water is the pause map's at the same strength (derived, no
        // capture shows water on the radar). The app uses it on a trip.
        radar: {
          page: "#2a251d",
          dark: true,
          map: {
            land: "#6c6452", residential: "#6c6452", industrial: "#695f4f",
            park: "#68614e", wood: "#655e4b", grass: "#68614e", sand: "#72695a",
            water: "#57584a", waterway: "#57584a", waterLine: "#4b4c40",
            building: "#6c6452", buildingLine: "#5a5244",
            rail: "#45403a", railDot: "#8a806c",
            roadScale: 1.3, roadBlur: 0.8,
            roads: {
              motorway: { fill: "#48423a", casing: null },
              major: { fill: "#48423a", casing: null },
              mid: { fill: "#4a443b", casing: null },
              minor: { fill: "#4d473d", casing: null },
              service: { fill: "#524c41", casing: null },
            },
            trails: { color: "#4d473d", dash: [1.6, 1.6] },
            bridge: { edge: "#48423a", gap: true },
            labels: false,
            townLabels: false,
            animals: false,
          },
        },
      },
      // blip_code_center: off-white teardrop with a ring (the app's is Gaurav's teardrop with the
      // game's ring, tools/make_pucks.py); blip_code_waypoint: red X through a ring.
      marker: { fill: "#d5d3d2", stroke: "#1b1a1a", shape: "teardrop" },
      blip: { shape: "crossring", fill: "#cc0000", center: "#cc0000", stroke: "#1b1a1a" },
      hud: {
        bg: "rgba(10,9,8,0.78)", fg: "#e6e6e6", sub: "#aca8a6", accent: "#cc0000", good: "#fef390",
        border: "rgba(230,230,230,0.22)", font: "'IM Fell English', serif", fontKey: "serif", weight: 400, upper: true,
        card: "#7a0e1d",
      },
      overlay: "paper",
    },

    // GTA VI, since the launch on 19 Nov 2026: the pause map, measured 22 Nov from real PS5
    // captures (Gaurav's research folder: the map at 2:45 pm and at 6:35 am). By day, mint-green
    // land, soft lavender-white blocks, wide pure-white streets, pale blue raised buildings and a
    // bright cyan sea over paler shallow shelves; before sunrise, navy blocks, lavender-grey
    // streets, slate buildings, teal-green land and an indigo sea. On a phone trip it turns into the
    // minimap ("radar", by time of day), the way GTA V and Red Dead do; the car keeps the pause map.
    gta6: {
      id: "gta6",
      skin: "gta6",
      name: "GTA VI",
      // The car shows the minimap at all times: it is what the game shows while you drive, and it is
      // the softer of the two (Gaurav, 23 Nov). The phone keeps the pause map for looking around and
      // turns into the minimap on a trip.
      carRadar: true,
      blurb: "Mint land, white streets, a cyan sea and one pink route. The Leonida map, indigo at night.",
      page: "#9ea5ad",
      dark: false,
      map: {
        // 23 Nov night, from gtabase.com/gta-6/map (his pick, sampled from his screenshot): muted, not
        // the mint-and-lavender of before. Town blocks mid grey #9EA5AD under pale lilac-grey roads
        // #D4D4DC, steel-blue buildings (#B4BFD1 enterable, #97A7BE not; one shade between here, see
        // the minimap note), soft cyan water #6FB2C5, deep green parks #4F876E, sage grass #6E9986. The plain ground is grey too (#A3A9AF): unmarked town
        // land in OpenStreetMap (most of Delhi) came out green, where gtabase's towns are grey.
        land: "#a3a9af", residential: "#9ea5ad", industrial: "#9ba2ab",
        park: "#4f876e", wood: "#4a7f66", grass: "#6e9986", sand: "#d9d3bb",
        // The sea: a pale cyan shelf along every shore (also lakes and rivers), a mint fringe at the
        // sand, deep cyan further out (#8ff3ff to #97fefe, #c0fbe8 to #dbfde2, #6feafc to #75ecfc).
        water: "#6fb2c5", waterway: "#6fb2c5",
        waterLine: "#8cc3cf",
        depth: { bands: [[10, "#69adc1"], [20, "#63a8bd"], [50, "#5da3b9"], [200, "#579eb5"]] },
        // Hills lit from the north-west: deeper green in the shade, warm cream on the lit side.
        hillshade: { strength: 0.25, shadow: "rgba(38, 104, 78, 0.55)", highlight: "rgba(255, 255, 238, 0.5)", accent: "rgba(38, 104, 78, 0.3)" },
        building: "#a8b5ca", buildingLine: null,
        // Raised buildings in periwinkle, clearly darker than the blocks they stand on (23 Nov, the
        // Ocean Beach map frame: buildings about a quarter darker than the ground, #8197B5 against
        // #CDCDD6 in that darker video grade); the light shades their walls deeper still.
        buildingShadow: { color: "#5f6670", opacity: 0.32, offset: [2, 3], blur: 5 },
        buildings3d: { color: "#a8b5ca", opacity: 0.96, heightScale: 1, minHeight: 4, facades: "-map" },
        bridge: { lift: 5.0, face: 1.7, faceColor: "#b4b8c3", edge: "#dcdce3", shadow: { color: "#6f7784", opacity: 0.35 } },
        light: { color: "#ffffff", intensity: 0.5 },
        rail: "#b8bcc6",
        roadScale: 1.6,
        roads: {
          motorway: { fill: "#dcdce3", casing: null },
          major: { fill: "#d6d6de", casing: null },
          mid: { fill: "#d4d4dc", casing: null },
          minor: { fill: "#d1d1d9", casing: null },
          service: { fill: "#c9c9d2", casing: null },
        },
        laneDashes: null,
        glow: null,
        labels: false,
        label: {
          road: "#4a4f62", roadHalo: "#d4d4dc", roadFont: "Noto Sans Regular",
          place: "#2c3040", placeHalo: "#b9bec6", placeFont: "Noto Sans Bold", placeSpacing: 0.08, upper: true,
          water: "#1f5866", waterHalo: "#8cc3cf",
        },
        route: VI_MINIMAP.route,
        // Bundled glyphs on Android for the place names (tools/make_glyphs.py).
        fonts: { place: "InterMedium", city: "BarlowCondensedBold", water: "InterMedium" },
        // Places are plain rounded tiles, the same as on the minimap: the game's have no point under
        // them (Gaurav, 23 Nov, from the launch captures; the pins with a point were dropped).
        pois: VI_MINIMAP.pois,
        viNames: { region: "#8d92a3" },
      },
      variants: {
        // Golden hour, DERIVED (no launch capture shows the pause map at dusk): each colour 42% of
        // the way from the day map to the night one, then 16% toward a sunset orange (#FF9E64).
        // Names stay darker than the ground, as by day. From 6 degrees of sun to 4 below.
        dusk: {
          page: "#9f9297",
          dark: false,
          map: {
            land: "#759477", residential: "#a09399", industrial: "#9e9198",
            park: "#6d9676", wood: "#6a9171", grass: "#759477", sand: "#b7a688",
            water: "#7fa1b1", waterway: "#7fa1b1",
            waterLine: "#9da9ae",
            depth: { bands: [[10, "#779eae"], [20, "#719bac"], [50, "#6d98aa"], [200, "#6995a7"]] },
            hillshade: { strength: 0.35, shadow: "rgba(48, 40, 64, 0.5)", highlight: "rgba(255, 196, 140, 0.35)", accent: "rgba(48, 40, 64, 0.3)" },
            building: "#9a92a5",
            buildingShadow: { color: "#4c4450", opacity: 0.32, offset: [2, 3], blur: 5 },
            buildings3d: { color: "#918ea8", opacity: 0.96, heightScale: 1, minHeight: 4, facades: "-map-dusk" },
            bridge: { lift: 5.0, face: 1.7, faceColor: "#a298a5", edge: "#cdc0c4", shadow: { color: "#6f6571", opacity: 0.45 } },
            light: { color: "#fff0e6", intensity: 0.4 },
            rail: "#aca0a9",
            roads: {
              motorway: { fill: "#d7c8c5", casing: null },
              major: { fill: "#d5c7c3", casing: null },
              mid: { fill: "#d3c5c0", casing: null },
              minor: { fill: "#d1c3bd", casing: null },
              service: { fill: "#c9bbba", casing: null },
            },
            label: { road: "#3a3440", roadHalo: "#d5c7c3", place: "#3a3440", placeHalo: "#a09399", water: "#2f4f5f", waterHalo: "#7fa1b1" },
            viNames: { region: "#5f5862" },
          },
        },
        // Before sunrise (the 6:35 am capture), from 4 degrees of sun below the horizon.
        night: {
          page: "#10132e",
          dark: true,
          map: {
            land: "#0b3b3d", residential: "#131632", industrial: "#121633",
            park: "#004542", wood: "#003d3b", grass: "#0b3b3d", sand: "#332f2a",
            water: "#2e3069", waterway: "#2e3069",
            waterLine: "#353c7f",
            depth: { bands: [[10, "#2a2d63"], [20, "#282b5e"], [50, "#26265a"], [200, "#232455"]] },
            hillshade: { strength: 0.35, shadow: "rgba(0, 12, 20, 0.6)", highlight: "rgba(70, 150, 150, 0.22)", accent: "rgba(0, 12, 20, 0.35)" },
            building: "#343764",
            buildingShadow: { color: "#141634", opacity: 0.32, offset: [2, 3], blur: 5 },
            buildings3d: { color: "#414a7a", opacity: 0.96, heightScale: 1, minHeight: 4, facades: "-map-night" },
            bridge: { lift: 5.0, face: 1.7, faceColor: "#4a4d70", edge: "#9a9cb0", shadow: { color: "#05061a", opacity: 0.6 } },
            light: { color: "#c9cdf0", intensity: 0.3 },
            rail: "#5d6080",
            roads: {
              motorway: { fill: "#8d8fa2", casing: null },
              major: { fill: "#8a8ca0", casing: null },
              mid: { fill: "#87899c", casing: null },
              minor: { fill: "#838598", casing: null },
              service: { fill: "#76788c", casing: null },
            },
            label: { road: "#e4e5ee", roadHalo: "#131632", place: "#e4e5ee", placeHalo: "#131632", water: "#b9bbe6", waterHalo: "#2e3069" },
            viNames: { region: "#6a6b7f" },
          },
        },
        radar: { page: "#c3c7c9", dark: false, map: VI_MINIMAP },
        "radar-dusk": { page: "#6e6c68", dark: true, map: { ...VI_MINIMAP, ...VI_MINIMAP_DUSK } },
        "radar-night": { page: "#1f1e2a", dark: true, map: { ...VI_MINIMAP, ...VI_MINIMAP_NIGHT } },
      },
      // The official minimap's player: a white arrow on a dark disc with a black rim, flat on the
      // screen (the app draws Gaurav's drawing of it, tools/blips/). Waypoint: a pink dot.
      marker: { fill: "#ffffff", shade: "#c9c9cc", stroke: "#000000", shape: "disc" },
      blip: { shape: "dot", fill: "#fb74a5", center: "#fb74a5", stroke: "#2a0f1c" },
      hud: {
        // Dark slate glass, like the mission HUD's boxes; mint for good news (the ally blip).
        bg: "rgba(28,27,38,0.86)", fg: "#f1f3f7", sub: "#a1a6b4", accent: "#fb74a5", good: "#4be3c4",
        border: "rgba(255,255,255,0.14)", font: "'Barlow Condensed', sans-serif", fontKey: "condensed", weight: 600, upper: true,
        card: "#7a2d52",
      },
      overlay: null,
    },

    // Algothrim: Gaurav's own world, a birthday gift (30 Nov 2026). Not a game: his portfolio,
    // thealgothrim.com, turned into a map. A black stage lit in crimson (#C9183C) like his key-art
    // posters: near-black ground, graphite roads, deep wine water with a crimson glow at the shore,
    // raised towers lit crimson with lit windows, one crimson route, names in the site's Unbounded and
    // Montserrat. The player is his white kite marker; the icons are his own set (Codex, Nov 2026).
    algo: {
      id: "algo",
      skin: "algo",
      name: "Algothrim",
      blurb: "A black stage lit in crimson: raised towers, one crimson route. My birthday gift to you, 30 Nov.",
      page: "#080808",
      dark: true,
      map: {
        land: "#080808", residential: "#0c0c0d", industrial: "#0e0e0f",
        park: "#121213", wood: "#101011", grass: "#111112", sand: "#161413",
        // Wine water, a shade darker out at sea, with a soft crimson rim along every shore.
        water: "#1d0810", waterway: "#1d0810",
        waterLine: "#7a1230", waterLineBlur: 3, waterLineOpacity: 0.9,
        depth: { bands: [[10, "#1a070e"], [30, "#16060c"], [100, "#12050a"], [500, "#0e0408"]] },
        // Near-black blocks, a shade off the ground (Gaurav, 23 Nov: "even more darker buildings").
        building: "#111113", buildingLine: null,
        // Raised blocks in near-black: plain walls for houses and shops, rows of windows (a few lit,
        // one in crimson) to 60 m, dark glass with crimson floor lines above, for the towers.
        buildings3d: { color: "#19191c", opacity: 0.96, heightScale: 1, minHeight: 4, facades: "", facadeSet: "algo" },
        // A dim, barely warm key light: the crimson comes from the windows and the towers' floor
        // lines (a crimson light turned every block plum, 23 Nov render).
        light: { color: "#fff0f2", intensity: 0.3 },
        bridge: { lift: 4.5, face: 1.5, faceColor: "#2a2a2e", edge: "#4a4a4f", shadow: { color: "#000000", opacity: 0.65 } },
        rail: "#45454a",
        roadScale: 1.25,
        roads: {
          motorway: { fill: "#8f8f94", casing: null },
          major: { fill: "#7a7a7f", casing: null },
          mid: { fill: "#66666b", casing: null },
          minor: { fill: "#4f4f53", casing: null },
          service: { fill: "#3b3b3f", casing: null },
        },
        laneDashes: { color: "#b4b4b9", groups: ["motorway", "major"] },
        glow: null,
        // Only the big names, his call (23 Nov): countries, states and cities, no roads or areas.
        labels: false,
        bigNames: { city: "#f2f2f2", minor: "#8c8c8c", font: "UnboundedSemiBold", citySpacing: 0.08, minorSpacing: 0.3 },
        // Bundled glyphs on Android (tools/make_glyphs.py): the portfolio's own type.
        fonts: { place: "MontserratSemiBold", city: "UnboundedSemiBold", water: "MontserratMediumItalic", road: "MontserratMedium" },
        label: {
          road: "#a3a3a8", roadHalo: "#080808", roadFont: "Noto Sans Regular",
          place: "#bfbfbf", placeHalo: "#080808", placeFont: "Noto Sans Bold", placeSpacing: 0.12, upper: true,
          city: "#f2f2f2", citySpacing: 0.08,
          water: "#e0506e", waterHalo: "#1d0810",
        },
        // Crimson with a brighter edge each side (a tenth of its width), and a soft glow on the phone.
        route: { line: "#dc1c45", casing: "#ff6b88", edge: 0.12, glow: "#c9183c" },
        // His own icon set (Codex): white pictograms on crimson rounded tiles. They ship in the public
        // build too: nothing in them is a game's.
        pois: { prefix: "algo-", minzoom: 15, size: 0.8,
          only: ["cafe", "fastfood", "food", "bar", "hospital", "doctor", "pharmacy", "bank", "post", "hotel", "train", "barber",
            "clothes", "repair", "store", "market", "fuel", "charging", "police", "theatre", "landmark", "worship", "parking",
            "airport", "school", "gym", "toilet", "mall", "tyre"] },
      },
      // The player: Gaurav's white kite with a black rim (tools/blips/algo.png). Waypoint: a crimson
      // diamond with a white rim.
      marker: { fill: "#ffffff", shade: "#d9d9d9", stroke: "#000000", shape: "kite" },
      blip: { shape: "diamond", fill: "#c9183c", center: "#ffffff", stroke: "#ffffff" },
      hud: {
        // The site's panels: near-black, a faint white hairline, crimson for what matters.
        // Good news (clear roads on the ETA) in a soft green like the other worlds' health green: a
        // pale crimson there read as a warning (emulator, 23 Nov).
        bg: "rgba(10,10,11,0.9)", fg: "#f2f2f2", sub: "#bfbfbf", accent: "#c9183c", good: "#62d394",
        border: "rgba(255,255,255,0.1)", font: "'Montserrat', sans-serif", fontKey: "sans", weight: 600, upper: false,
        card: "#3b0a17",
      },
      overlay: null,
    },
  };

  /** The theme's map palette for a time-of-day variant ("dusk", "night"), or its base palette. */
  function paletteFor(theme, variant) {
    const v = variant && theme.variants && theme.variants[variant];
    if (!v) return theme.map;
    const m = Object.assign({}, theme.map, v.map);
    m.roads = Object.assign({}, theme.map.roads, v.map.roads);
    if (theme.map.label) m.label = Object.assign({}, theme.map.label, v.map.label || {});
    return m;
  }

  // Road groups over OpenMapTiles transportation classes, drawn bottom to top.
  const GROUPS = [
    { key: "service", classes: ["service", "track"], minzoom: 14, w: [0, 0, 2.2, 6] },
    { key: "minor", classes: ["minor"], minzoom: 12, w: [0, 0.8, 4, 11] },
    { key: "mid", classes: ["secondary", "tertiary"], minzoom: 9, w: [0.6, 1.8, 6.5, 15] },
    { key: "major", classes: ["trunk", "primary"], minzoom: 6, w: [1, 2.6, 8.5, 19] },
    { key: "motorway", classes: ["motorway"], minzoom: 5, w: [1.2, 3, 9.5, 22] },
  ];

  function widthExpr(w, scale, add) {
    const a = add || [0, 0, 0, 0];
    return ["interpolate", ["exponential", 1.5], ["zoom"],
      10, w[0] * scale + a[0], 13, w[1] * scale + a[1], 16, w[2] * scale + a[2], 18, w[3] * scale + a[3]];
  }

  function classFilter(classes, brunnel) {
    const f = ["all",
      ["match", ["geometry-type"], ["LineString", "MultiLineString"], true, false],
      ["match", ["get", "class"], classes, true, false]];
    if (brunnel === "bridge") f.push(["==", ["get", "brunnel"], "bridge"]);
    else if (brunnel === "tunnel") f.push(["==", ["get", "brunnel"], "tunnel"]);
    else f.push(["match", ["get", "brunnel"], ["bridge", "tunnel"], false, true]);
    return f;
  }

  /**
   * A length on the ground as the camera sees it: [atZ16] dp at zoom 16, doubling with every zoom
   * level, the way a real height looks bigger as the map zooms in. As a line-translate when [offset].
   */
  function groundScaled(atZ16, offset) {
    const v = (x) => (offset ? ["literal", [0, x]] : x);
    return ["interpolate", ["exponential", 2], ["zoom"], 12, v(atZ16 / 16), 20, v(atZ16 * 16)];
  }

  /**
   * Under each flyover deck (drawn by roadLayers in the road's own colour, so it runs straight into
   * the ramps at both ends): its side and its shadow on the ground, offset down the screen by what
   * a 6 m high deck looks like from the theme's camera tilt (bridge.lift, dp at zoom 16; 0 for a map
   * seen from straight above). The offsets grow and shrink with zoom like a real height, so a
   * zoomed-out map doesn't get slabs of shadow.
   *
   * These go under the ordinary roads: where a bridge ends, the road it runs into covers the end
   * of its side and shadow. Drawn over the roads, they stopped in a hard square edge across the
   * road at every end of every bridge.
   */
  function bridgeUndersideLayers(m, car) {
    const b = m.bridge || {};
    const out = [];
    const scale = (car ? 1.25 : 1) * (m.roadScale || 1);
    const sortKey = { "line-sort-key": ["coalesce", ["get", "layer"], 1] };
    // OpenStreetMap marks a 15 m crossing over a drain exactly like a flyover. The side starts two
    // road widths into each bridge (a dash pattern opening with a gap), so the short ones the road
    // just runs across get no edge, only a faint soft shadow.
    const rise = { "line-dasharray": ["literal", [0, 2, 200, 0]] };
    for (const g of GROUPS) {
      const filter = classFilter(g.classes, "bridge");
      const minzoom = Math.max(g.minzoom, 13);
      // Round, blurred ends for the shadow; square ones for the side (round would turn the pattern's
      // empty first dash into a dot).
      const soft = { "line-cap": "round", "line-join": "round", ...sortKey };
      const layout = { "line-cap": "butt", "line-join": "round", ...sortKey };
      if (b.lift && b.shadow) {
        out.push({
          id: `bridge-${g.key}-shadow`, type: "line", source: "omt", "source-layer": "transportation", minzoom, filter, layout: soft,
          paint: {
            "line-color": b.shadow.color,
            "line-opacity": ["interpolate", ["linear"], ["zoom"], 13, 0, 14.5, b.shadow.opacity],
            "line-blur": groundScaled(b.lift * 0.35),
            "line-width": widthExpr(g.w, scale),
            "line-translate": groundScaled(b.lift, true),
            "line-translate-anchor": "viewport",
          },
        });
      }
      if (b.face) {
        out.push({
          id: `bridge-${g.key}-side`, type: "line", source: "omt", "source-layer": "transportation", minzoom, filter, layout,
          paint: {
            "line-color": b.faceColor,
            "line-opacity": ["interpolate", ["linear"], ["zoom"], 13, 0, 14.5, 1],
            "line-width": widthExpr(g.w, scale),
            "line-translate": groundScaled(b.face, true),
            "line-translate-anchor": "viewport",
            ...rise,
          },
        });
      }
    }
    return out;
  }

  /**
   * A thin kerb line along each side of a deck (bridge.edge), so it reads as passing over the roads
   * it crosses. Lines beside the deck, not an outline round it, so a bridge's ends draw nothing
   * across the road. On the paper map they stand off the deck past a band of bare paper (bridge.gap),
   * the surveyor's bridge.
   */
  function parapetLayers(m, scale, sortKey) {
    const b = m.bridge || {};
    if (!b.edge) return [];
    return GROUPS.map((g) => ({
      id: `bridge-${g.key}-parapet`, type: "line", source: "omt", "source-layer": "transportation",
      minzoom: Math.max(g.minzoom, 13), filter: classFilter(g.classes, "bridge"),
      layout: { "line-cap": "butt", "line-join": "round", ...sortKey },
      paint: {
        "line-color": b.edge,
        // [z14, z16, z18] when the theme sets a width, growing with the deck.
        "line-width": b.edgeWidth
          ? ["interpolate", ["linear"], ["zoom"], 14, b.edgeWidth[0], 16, b.edgeWidth[1], 18, b.edgeWidth[2]]
          : b.gap ? 1.1 : 0.9,
        "line-gap-width": widthExpr(g.w, scale, b.gap ? [1.6, 2, 3, 4] : [0, 0, 0, 0]),
        "line-opacity": ["interpolate", ["linear"], ["zoom"], 13, 0, 14.5, 1],
      },
    }));
  }

  function roadLayers(m, car, brunnel) {
    const scale = (car ? 1.25 : 1) * (m.roadScale || 1);
    const out = [];
    const tag = brunnel || "road";
    const faded = brunnel === "tunnel";
    // Flyovers stand above the roads they cross, like the games' minimaps: higher decks (OSM
    // "layer") drawn over lower ones, each with its side and its shadow below (bridgeLayers). The
    // app draws the route under "bridges-start" so it runs beneath a flyover, and the part of the
    // route that is on one above it.
    const sortKey = brunnel === "bridge" ? { "line-sort-key": ["coalesce", ["get", "layer"], 1] } : {};
    if (brunnel === "bridge") {
      out.push({
        id: "bridges-start", type: "line", source: "omt", "source-layer": "transportation",
        filter: ["boolean", false], paint: { "line-opacity": 0 },
      });
      if (m.bridge && m.bridge.gap) {
        // Bare paper either side of the deck, so the roads beneath stop short of it.
        for (const g of GROUPS) {
          out.push({
            id: `bridge-${g.key}-gap`, type: "line", source: "omt", "source-layer": "transportation",
            minzoom: Math.max(g.minzoom, 13), filter: classFilter(g.classes, brunnel),
            layout: { "line-cap": "butt", "line-join": "round", ...sortKey },
            paint: { "line-color": m.land, "line-width": widthExpr(g.w, scale, [1.6, 2, 3, 4]) },
          });
        }
      }
    }
    // Casings first, then fills, so crossings read cleanly. A deck has no outline of its own: an
    // edge darker than the road around every piece of bridge looked stamped onto the map.
    for (const g of GROUPS) {
      const spec = m.roads[g.key];
      const casing = brunnel === "bridge" ? null : spec.casing;
      if (!casing) continue;
      out.push({
        id: `${tag}-${g.key}-casing`, type: "line", source: "omt", "source-layer": "transportation",
        minzoom: Math.max(g.minzoom, 11), filter: classFilter(g.classes, brunnel),
        layout: { "line-cap": brunnel ? "butt" : "round", "line-join": "round", ...sortKey },
        paint: { "line-color": casing, "line-width": widthExpr(g.w, scale, [1, 1.6, 2.6, 4.5]), "line-opacity": faded ? 0.35 : 1 },
      });
    }
    for (const g of GROUPS) {
      const spec = m.roads[g.key];
      const paint = { "line-color": spec.fill, "line-width": widthExpr(g.w, scale), "line-opacity": faded ? 0.4 : 1 };
      // Soft-edged bands (Red Dead's radar) instead of crisp ink.
      if (m.roadBlur) paint["line-blur"] = m.roadBlur;
      if (spec.dash) paint["line-dasharray"] = spec.dash;
      out.push({
        id: `${tag}-${g.key}`, type: "line", source: "omt", "source-layer": "transportation",
        minzoom: g.minzoom, filter: classFilter(g.classes, brunnel),
        layout: { "line-cap": brunnel || spec.dash ? "butt" : "round", "line-join": "round", ...sortKey },
        paint,
      });
    }
    if (brunnel === "bridge") out.push(...parapetLayers(m, scale, sortKey));
    return out;
  }

  // Seabed depth areas from Open Waters' Seascape (CC BY 4.0: GEBCO plus regional surveys, z4 to
  // z15): ENC-style polygons with a depth range each (drval1 to drval2, metres in the "m" set).
  // Plain fills, so they stay crisp at every zoom and draw the same on Android.
  const SEABED_SOURCE = {
    type: "vector", minzoom: 4, maxzoom: 15,
    tiles: ["https://tiles.openwaters.io/seascape/{z}/{x}/{y}.pbf"],
    attribution: "Sea depth © Open Waters: Seascape (not for navigation)",
  };

  // Land elevation for hill shading: Terrain Tiles on AWS Open Data (terrarium), free and keyless,
  // land detail to z15 (SRTM, 3DEP, GMTED).
  const HILLS_SOURCE = {
    type: "raster-dem", encoding: "terrarium", tileSize: 256, maxzoom: 15,
    tiles: ["https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"],
    attribution: "Hills: Terrain Tiles (Mapzen; SRTM, USGS)",
  };

  // Depth bands as [shallowest metres of the band, colour], shallow first. Areas shallower than the
  // first band stay clear, so the flat water fill shows along the coast and on every lake and river.
  // The bands fade out by z13: off India the data is GEBCO's 450 m grid, which close up turns into
  // big angular shards (emulator, 22 Nov), so up close the sea is plain water.
  const DEPTH_FADE = [11.5, 13];
  function depthLayer(d) {
    const fill = ["step", ["get", "drval1"], d.bands[0][1]];
    for (const [m, c] of d.bands.slice(1)) fill.push(m, c);
    return {
      id: "water-depth", type: "fill", source: "seabed", "source-layer": "depare", maxzoom: DEPTH_FADE[1],
      filter: ["all", ["==", ["get", "sys"], "m"], [">=", ["get", "drval1"], d.bands[0][0]]],
      paint: { "fill-color": fill, "fill-opacity": ["interpolate", ["linear"], ["zoom"], DEPTH_FADE[0], 1, DEPTH_FADE[1], 0] },
    };
  }

  /**
   * Only the big names (Gaurav, 23 Nov): countries far out, states as you come in, cities until the
   * streets begin. [N]: city and minor (state, country) colours, the font, letter spacing and the
   * city sizes at zoom 5, 9 and 12. No outline, like GTA VI's pause map.
   */
  function bigNameLayers(N, custom, tag) {
    const font = custom ? N.font : "Noto Sans Bold";
    const up = ["upcase", NAME];
    const s = N.sizes || [14, 24, 32];
    const place = (id, cls, minzoom, maxzoom, size, color, spacing) => ({
      id: `label-${tag}-${id}`, type: "symbol", source: "omt", "source-layer": "place", minzoom, maxzoom,
      filter: ["==", ["get", "class"], cls],
      layout: { "text-field": up, "text-font": [font], "text-max-width": 14, "text-size": size, "text-letter-spacing": spacing },
      paint: { "text-color": color },
    });
    return [
      place("country", "country", 0, 6, ["interpolate", ["linear"], ["zoom"], 2, 12, 5, 20], N.minor, N.minorSpacing),
      place("state", "state", 4, 8.5, ["interpolate", ["linear"], ["zoom"], 4, 11, 8, 18], N.minor, N.minorSpacing),
      place("city", "city", 5, 13, ["interpolate", ["linear"], ["zoom"], 5, s[0], 9, s[1], 12, s[2]], N.city, N.citySpacing),
    ];
  }

  function buildStyle(theme, opts) {
    const car = !!(opts && opts.car);
    const m = paletteFor(theme, opts && opts.variant);
    const L = m.label;
    const labelScale = car ? 1.18 : 1;
    const text = (s) => (L.upper ? ["upcase", s] : s);
    const layers = [];

    layers.push({ id: "land", type: "background", paint: { "background-color": m.land } });
    layers.push({
      id: "landuse-residential", type: "fill", source: "omt", "source-layer": "landuse",
      filter: ["match", ["get", "class"], ["residential", "suburb", "neighbourhood"], true, false],
      paint: { "fill-color": m.residential },
    });
    layers.push({
      id: "landuse-industrial", type: "fill", source: "omt", "source-layer": "landuse",
      filter: ["match", ["get", "class"], ["commercial", "retail", "industrial", "railway"], true, false],
      paint: { "fill-color": m.industrial },
    });
    layers.push({
      id: "aeroway", type: "fill", source: "omt", "source-layer": "aeroway",
      filter: ["match", ["geometry-type"], ["Polygon", "MultiPolygon"], true, false],
      paint: { "fill-color": m.industrial },
    });
    layers.push({
      id: "landcover-wood", type: "fill", source: "omt", "source-layer": "landcover",
      filter: ["match", ["get", "class"], ["wood", "forest"], true, false],
      paint: { "fill-color": m.wood },
    });
    layers.push({
      id: "landcover-grass", type: "fill", source: "omt", "source-layer": "landcover",
      filter: ["match", ["get", "class"], ["grass", "farmland", "scrub"], true, false],
      paint: { "fill-color": m.grass, "fill-opacity": 0.8 },
    });
    layers.push({
      id: "landcover-sand", type: "fill", source: "omt", "source-layer": "landcover",
      filter: ["==", ["get", "class"], "sand"],
      paint: { "fill-color": m.sand },
    });
    layers.push({ id: "park", type: "fill", source: "omt", "source-layer": "park", paint: { "fill-color": m.park } });

    if (m.patterns && (!car || m.patternsInCar)) {
      layers.push({
        id: "pattern-wood", type: "fill", source: "omt", "source-layer": "landcover",
        filter: ["match", ["get", "class"], ["wood", "forest"], true, false],
        paint: { "fill-pattern": m.patterns.wood },
      });
      layers.push({ id: "pattern-park", type: "fill", source: "omt", "source-layer": "park", paint: { "fill-pattern": m.patterns.park } });
    }

    // Soft light and shade on the hills, like the "Visit Leonida" poster's relief (the in-game pause
    // map captures read flat; Gaurav chose it, 22 Nov). Same Terrain Tiles as Red Dead's contours.
    // The car too: on the DHU (S24 Ultra, 23 Nov) GTA VI's car map drew 58-59 fps with no stutters
    // and 11-13 ms CPU a frame with it, the same as without.
    if (m.hillshade) {
      layers.push({
        id: "hillshade", type: "hillshade", source: "hills",
        paint: {
          "hillshade-exaggeration": m.hillshade.strength,
          "hillshade-shadow-color": m.hillshade.shadow,
          "hillshade-highlight-color": m.hillshade.highlight,
          "hillshade-accent-color": m.hillshade.accent || m.hillshade.shadow,
          "hillshade-illumination-direction": 315,
        },
      });
    }

    const waterwayLayer = {
      id: "waterway", type: "line", source: "omt", "source-layer": "waterway",
      filter: ["!=", ["get", "brunnel"], "tunnel"],
      paint: {
        "line-color": m.waterway,
        "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 10, ["match", ["get", "class"], ["river", "canal"], 1.4, 0.4], 18, ["match", ["get", "class"], ["river", "canal"], 12, 3]],
      },
    };
    // Shoreline sits under the water fill: the fill hides its inner half, so the
    // seams where OSM splits a river into several polygons never show.
    if (m.waterLine && (!car || !m.waterLineBlur)) {
      layers.push({
        id: "water-line", type: "line", source: "omt", "source-layer": "water", minzoom: 9,
        filter: ["!=", ["get", "brunnel"], "tunnel"],
        paint: {
          "line-color": m.waterLine, "line-width": m.waterLineBlur ? 5 : 2.4,
          "line-blur": m.waterLineBlur || 0, "line-opacity": m.waterLineOpacity || 1,
        },
      });
    }
    layers.push({
      id: "water", type: "fill", source: "omt", "source-layer": "water",
      filter: ["!=", ["get", "brunnel"], "tunnel"],
      paint: { "fill-color": m.water },
    });
    // Sea depth, the way the games shade their seas: light over shallow shelves, dark over deep
    // water (GTA V's hard terraces). Seascape's depth areas start at the coast it was built from,
    // which can differ from OpenStreetMap's (land reclaimed since the survey), so the first band
    // starts 10 m down and the shallows keep the flat water colour.
    if (m.depth) layers.push(depthLayer(m.depth));
    layers.push(waterwayLayer);

    // A soft shadow down and right of every building (23 Nov, after Game Maps IRL's Leonyda: its
    // flat blocks never look sharp because a blurred shadow takes the hard edge). MapLibre can't blur
    // a fill, so: a faint nudged copy, plus a blurred line round its edge. Phone only.
    if (m.buildingShadow && !car) {
      const S = m.buildingShadow;
      const fade = (o) => ["interpolate", ["linear"], ["zoom"], 14, 0, 15, o];
      layers.push({
        id: "building-shadow", type: "fill", source: "omt", "source-layer": "building", minzoom: 14,
        paint: { "fill-color": S.color, "fill-opacity": fade(S.opacity * 0.7), "fill-translate": S.offset, "fill-antialias": false },
      });
      layers.push({
        id: "building-shadow-edge", type: "line", source: "omt", "source-layer": "building", minzoom: 14,
        paint: { "line-color": S.color, "line-opacity": fade(S.opacity), "line-width": S.blur, "line-blur": S.blur, "line-translate": S.offset },
      });
    }
    layers.push({
      id: "building", type: "fill", source: "omt", "source-layer": "building", minzoom: 14,
      // Raised buildings take over from the flat footprints as they grow (see building-3d).
      ...(m.buildings3d ? { maxzoom: 16 } : {}),
      paint: Object.assign(
        { "fill-color": m.building, "fill-opacity": ["interpolate", ["linear"], ["zoom"], 14, 0, 15, car && !m.buildings3d ? 0.6 : 0.9] },
        m.buildingLine ? { "fill-outline-color": m.buildingLine } : {}
      ),
    });

    if (m.animals) {
      // One sketch in the middle of a wood, a field, a lake: over the ground and the flat buildings, under the roads and
      // the names, which win where they meet, and well apart from each other.
      const polygon = ["match", ["geometry-type"], ["Polygon", "MultiPolygon"], true, false];
      const sketch = (id, sourceLayer, filter, image) =>
        layers.push({
          id: `animals-${id}`, type: "symbol", source: "omt", "source-layer": sourceLayer, minzoom: 12.5, maxzoom: 17,
          filter: ["all", polygon, filter],
          layout: {
            "icon-image": image,
            "icon-size": ["interpolate", ["linear"], ["zoom"], 12.5, 0.55, 16, 1],
            "icon-padding": car ? 110 : 70,
          },
          paint: { "icon-opacity": 0.72 },
        });
      const kind = ["coalesce", ["get", "subclass"], ["get", "class"], ""];
      sketch("wood", "landcover", ["match", ["get", "class"], ["wood", "forest"], true, false],
        ["match", kind, "forest", "animal-nilgai", "wood", "animal-jackal", "animal-monkey"]);
      sketch("grass", "landcover", ["match", ["get", "class"], ["grass", "farmland", "scrub", "sand"], true, false],
        ["match", kind, ["scrub", "heath"], "animal-boar", "farmland", "animal-crow-flying", ["meadow", "grassland"], "animal-nilgai", "park", "animal-peacock",
          "garden", "animal-pigeons", "golf_course", "animal-kite", ["sand", "beach"], "animal-crow", "animal-dog"]);
      sketch("park", "park", ["has", "class"], "animal-monkey");
      sketch("water", "water", ["!=", ["get", "brunnel"], "tunnel"],
        ["match", ["get", "class"], ["lake", "pond"], "animal-buffalo", "animal-heron"]);
    }


    const railFilter = ["all", ["match", ["get", "class"], ["rail", "transit"], true, false], ["!=", ["get", "brunnel"], "tunnel"]];
    const dotted = m.railStyle === "dots";
    // m.railDouble: two thin rails with the ground between (GTA VI's minimap, launch capture 24).
    layers.push({
      id: "rail", type: "line", source: "omt", "source-layer": "transportation", minzoom: 11, filter: railFilter,
      paint: Object.assign(
        { "line-color": m.rail, "line-width": ["interpolate", ["linear"], ["zoom"], 11, dotted ? 1.2 : 0.6, 18, dotted ? 5 : 2] },
        m.railDouble ? {
          "line-width": ["interpolate", ["linear"], ["zoom"], 12, 0.5, 18, 1.6],
          "line-gap-width": ["interpolate", ["linear"], ["zoom"], 12, 0.4, 15, 1.4, 18, 4],
        } : {}
      ),
    });
    if (dotted && (!car || m.railDotsInCar)) {
      // RDR2 railways: a thick ink line with light dots running along it.
      layers.push({
        id: "rail-dots", type: "line", source: "omt", "source-layer": "transportation", minzoom: 13, filter: railFilter,
        layout: { "line-cap": "round" },
        paint: { "line-color": m.railDot, "line-width": ["interpolate", ["linear"], ["zoom"], 13, 1.4, 18, 3], "line-dasharray": [0, 2.4] },
      });
    }
    if (m.railTies && !car) {
      layers.push({
        id: "rail-ties", type: "line", source: "omt", "source-layer": "transportation", minzoom: 13, filter: railFilter,
        paint: { "line-color": m.rail, "line-width": ["interpolate", ["linear"], ["zoom"], 13, 4, 18, 9], "line-dasharray": [0.18, 2.2] },
      });
    }

    if (m.glow && !car) {
      for (const g of GROUPS.filter((x) => m.glow.groups.includes(x.key))) {
        layers.push({
          id: `glow-${g.key}`, type: "line", source: "omt", "source-layer": "transportation", minzoom: g.minzoom,
          filter: classFilter(g.classes, null),
          layout: { "line-cap": "round", "line-join": "round" },
          paint: {
            "line-color": m.roads[g.key].fill, "line-opacity": m.glow.opacity, "line-blur": m.glow.blur,
            "line-width": widthExpr(g.w, m.glow.spread),
          },
        });
      }
    }

    if (m.trails && !car) {
      layers.push({
        id: "trails", type: "line", source: "omt", "source-layer": "transportation", minzoom: 15,
        filter: ["all", ["match", ["get", "class"], ["path"], true, false], ["match", ["get", "brunnel"], ["tunnel"], false, true]],
        // No dash for solid paths (GTA VI's pale footpaths between blocks, launch capture 34).
        paint: Object.assign(
          { "line-color": m.trails.color, "line-width": ["interpolate", ["linear"], ["zoom"], 15, m.trails.solid ? 1.4 : 0.8, 18, m.trails.solid ? 4 : 1.6] },
          m.trails.dash ? { "line-dasharray": m.trails.dash } : {}
        ),
      });
    }
    layers.push(...roadLayers(m, car, "tunnel"));
    layers.push(...bridgeUndersideLayers(m, car));
    layers.push(...roadLayers(m, car, null));
    // Lane dividers down the middle of each carriageway (OpenStreetMap draws dual carriageways
    // as one line per direction, so the middle of the line is between its lanes). A flyover's go
    // on its deck, or they stopped dead where every bridge began.
    const laneDashes = (id, brunnel) => {
      const classes = GROUPS.filter((g) => m.laneDashes.groups.includes(g.key)).flatMap((g) => g.classes);
      return {
        id, type: "line", source: "omt", "source-layer": "transportation", minzoom: 15,
        filter: ["all", ["match", ["get", "class"], classes, true, false],
          brunnel === "bridge" ? ["==", ["get", "brunnel"], "bridge"] : ["match", ["get", "brunnel"], ["tunnel", "bridge"], false, true]],
        layout: { "line-join": "round", ...(brunnel === "bridge" ? { "line-sort-key": ["coalesce", ["get", "layer"], 1] } : {}) },
        paint: {
          "line-color": m.laneDashes.color,
          "line-width": ["interpolate", ["linear"], ["zoom"], 15, 0.8, 18, 2.2],
          "line-dasharray": [3, 3],
          "line-opacity": ["interpolate", ["linear"], ["zoom"], 15, 0, 15.6, 0.9],
        },
      };
    };
    // Solid lane stripes instead: one down the middle of each carriageway and one along each edge,
    // each m.laneStripes.share of the road's width; middle: false keeps only the edges (GTA VI's
    // grey interstate shoulders, launch capture 24).
    const laneStripes = (id, brunnel) => {
      const S = m.laneStripes;
      const scale = (car ? 1.25 : 1) * (m.roadScale || 1);
      const layout = { "line-join": "round", ...(brunnel === "bridge" ? { "line-sort-key": ["coalesce", ["get", "layer"], 1] } : {}) };
      const paint = { "line-color": S.color, "line-opacity": ["interpolate", ["linear"], ["zoom"], S.minzoom || 15, 0, (S.minzoom || 15) + 0.6, 1] };
      return GROUPS.filter((g) => S.groups.includes(g.key)).flatMap((g) => [
        ...(S.middle === false ? [] : [{
          id: `${id}-${g.key}-middle`, type: "line", source: "omt", "source-layer": "transportation", minzoom: S.minzoom || 15,
          filter: classFilter(g.classes, brunnel), layout,
          paint: { ...paint, "line-width": widthExpr(g.w, scale * S.share) },
        }]),
        {
          id: `${id}-${g.key}-edges`, type: "line", source: "omt", "source-layer": "transportation", minzoom: S.minzoom || 15,
          filter: classFilter(g.classes, brunnel), layout,
          paint: { ...paint, "line-width": widthExpr(g.w, scale * S.share), "line-gap-width": widthExpr(g.w, scale * (1 - 2 * S.share)) },
        },
      ]);
    };
    if (m.laneDashes) layers.push(laneDashes("lane-dashes", null));
    if (m.laneStripes) layers.push(...laneStripes("lane-stripes", null));
    layers.push(...roadLayers(m, car, "bridge"));
    if (m.laneDashes) layers.push(laneDashes("lane-dashes-bridge", "bridge"));
    if (m.laneStripes) layers.push(...laneStripes("lane-stripes-bridge", "bridge"));
    if (m.buildings3d) {
      // GTA VI's minimap raises its buildings. Heights come from OpenStreetMap (render_height,
      // 5 m where nothing is tagged) at their real size, and grow in from zoom 14.5 to 16. A theme
      // can set maxHeight, but nothing in Rockstar's footage says the game caps its towers, so
      // GTA VI doesn't (Gaurav, 25 Oct).
      const B = m.buildings3d;
      const set = B.facadeSet || "vi";
      const raw = ["max", B.minHeight, ["*", B.heightScale, ["coalesce", ["get", "render_height"], 5]]];
      const height = B.maxHeight ? ["min", B.maxHeight, raw] : raw;
      layers.push({
        id: "building-3d", type: "fill-extrusion", source: "omt", "source-layer": "building", minzoom: 14.5,
        filter: ["!=", ["get", "hide_3d"], true],
        paint: {
          // Facades paint the walls by height (and the roof, which the cap below covers).
          ...(B.facades !== undefined
            ? { "fill-extrusion-pattern": ["step", ["coalesce", ["get", "render_height"], 5],
                `${set}-wall${B.facades}`, 12, `${set}-windows${B.facades}`, 60, `${set}-glass${B.facades}`] }
            : { "fill-extrusion-color": B.color }),
          "fill-extrusion-height": ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, height],
          "fill-extrusion-base": ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, ["*", B.heightScale, ["coalesce", ["get", "render_min_height"], 0]]],
          // Solid in the car: under full opacity MapLibre draws every building twice a frame (a depth
          // pass, then the colour), and among Cyber City's towers at the car's tilt that was a large
          // share of each frame (7 Nov drive, "so laggy"). Too little shows through at 0.94 to miss.
          "fill-extrusion-opacity": car ? 1 : B.opacity,
          // Walls darken toward the ground, so the blocks sit on the map instead of floating on it;
          // a theme can turn it off (B.gradient false: GTA VI's flat, frosted minimap blocks).
          "fill-extrusion-vertical-gradient": B.gradient !== false,
        },
      });
      if (B.facades !== undefined) {
        // A plain roof: a half-metre slab on top in the wall colour, over the patterned roof.
        layers.push({
          id: "building-3d-roof", type: "fill-extrusion", source: "omt", "source-layer": "building", minzoom: 14.5,
          filter: ["!=", ["get", "hide_3d"], true],
          paint: {
            "fill-extrusion-color": B.color,
            "fill-extrusion-height": ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, ["+", height, 0.5]],
            "fill-extrusion-base": ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, height],
            "fill-extrusion-opacity": car ? 1 : B.opacity,
          },
        });
      }
      if (B.rim && !car) {
        // A thin darker rim round each roof, as on GTA VI's minimap (launch capture 34). MapLibre
        // can't outline an extrusion, so: a dark slab at roof height drawn four times, each nudged a
        // pixel or so on screen (left, right, up, down), then a cap in the roof's colour exactly on
        // top; the nudged slabs show a hair's width round the cap, like a text halo. At roof height
        // and depth-tested, a nearer block still hides the rim of one behind it. Phone only: five more
        // passes over the buildings would cost the car its frame rate.
        const t = B.rim.width;
        const at = (dh) => ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, ["+", height, dh]];
        const fade = ["interpolate", ["linear"], ["zoom"], 15, 0, 15.8, B.opacity];
        [[t, 0], [-t, 0], [0, t], [0, -t]].forEach(([dx, dy], i) =>
          layers.push({
            id: `building-3d-rim-${i}`, type: "fill-extrusion", source: "omt", "source-layer": "building", minzoom: 15,
            filter: ["!=", ["get", "hide_3d"], true],
            paint: {
              "fill-extrusion-color": B.rim.color,
              "fill-extrusion-base": at(0),
              "fill-extrusion-height": at(0.3),
              "fill-extrusion-opacity": fade,
              "fill-extrusion-translate": [dx, dy],
              "fill-extrusion-translate-anchor": "viewport",
            },
          }));
        layers.push({
          id: "building-3d-rim-cap", type: "fill-extrusion", source: "omt", "source-layer": "building", minzoom: 15,
          filter: ["!=", ["get", "hide_3d"], true],
          paint: {
            "fill-extrusion-color": B.rim.roof || B.color,
            "fill-extrusion-base": at(0.3),
            "fill-extrusion-height": at(0.45),
            "fill-extrusion-opacity": fade,
          },
        });
      }
    }

    // Route: remaining part of the trip. The app updates the source while driving.
    const R = m.route;
    if (R.glow && !car) {
      layers.push({
        id: "route-glow", type: "line", source: "route", layout: { "line-cap": "round", "line-join": "round" },
        paint: { "line-color": R.glow, "line-opacity": 0.45, "line-blur": 8, "line-width": ["interpolate", ["linear"], ["zoom"], 10, 10, 18, 34] },
      });
    }
    if (R.casing) {
      // A thin edge of R.edge of the width each side (GTA VI), or a classic outline.
      const casing = R.edge ? [(car ? 4.5 : 3.5) / (1 - 2 * R.edge), (car ? 15 : 12) / (1 - 2 * R.edge)] : [car ? 7 : 5.5, car ? 22 : 18];
      layers.push({
        id: "route-casing", type: "line", source: "route", layout: { "line-cap": "round", "line-join": "round" },
        paint: { "line-color": R.casing, "line-width": ["interpolate", ["linear"], ["zoom"], 10, casing[0], 18, casing[1]] },
      });
    }
    layers.push({
      id: "route-line", type: "line", source: "route", layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": R.line, "line-width": ["interpolate", ["linear"], ["zoom"], 10, car ? 4.5 : 3.5, 18, car ? 15 : 12] },
    });

    // Fonts: bundled map-lettering glyphs on Android (opts.glyphs), OpenFreeMap's Noto otherwise.
    const custom = !!(opts && opts.glyphs && m.fonts);
    const F = custom
      ? { place: m.fonts.place, city: m.fonts.city, water: m.fonts.water, road: m.fonts.road || m.fonts.place, pencil: m.fonts.pencil }
      : { place: L.placeFont, city: L.placeFont, water: "Noto Sans Italic", road: L.roadFont, pencil: "Noto Sans Italic" };

    const firstLabel = layers.length;
    layers.push({
      id: "label-water", type: "symbol", source: "omt", "source-layer": "water_name", minzoom: 11,
      filter: ["match", ["geometry-type"], ["Point", "MultiPoint"], true, false],
      layout: { "text-field": NAME, "text-font": [F.water], "text-size": (custom ? 15 : 12) * labelScale, "text-letter-spacing": 0.12, "text-max-width": 8 },
      paint: { "text-color": L.water, "text-halo-color": L.waterHalo, "text-halo-width": 1.4 },
    });
    layers.push({
      id: "label-waterway", type: "symbol", source: "omt", "source-layer": "waterway", minzoom: 12,
      filter: ["match", ["get", "class"], ["river", "canal"], true, false],
      layout: { "text-field": NAME, "text-font": [F.water], "text-size": (custom ? 15 : 12) * labelScale, "text-letter-spacing": 0.12, "symbol-placement": "line" },
      paint: { "text-color": L.water, "text-halo-color": L.waterHalo, "text-halo-width": 1.4 },
    });
    const roadLabelLayout = m.roadLabels === false ? { visibility: "none" } : {};
    layers.push({
      id: "label-road-major", type: "symbol", source: "omt", "source-layer": "transportation_name", minzoom: 13,
      filter: ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary", "tertiary"], true, false],
      layout: Object.assign({
        "text-field": NAME, "text-font": [F.road], "symbol-placement": "line",
        "text-size": ["interpolate", ["linear"], ["zoom"], 13, 10.5 * labelScale, 18, 14 * labelScale],
        "text-letter-spacing": 0.04, "symbol-spacing": 320,
      }, roadLabelLayout),
      paint: { "text-color": L.road, "text-halo-color": L.roadHalo, "text-halo-width": 1.6 },
    });
    if (!car) {
      layers.push({
        id: "label-road-minor", type: "symbol", source: "omt", "source-layer": "transportation_name", minzoom: 15.5,
        filter: ["match", ["get", "class"], ["minor", "service"], true, false],
        layout: Object.assign({ "text-field": NAME, "text-font": [F.road], "symbol-placement": "line", "text-size": 10.5 }, roadLabelLayout),
        paint: { "text-color": L.road, "text-halo-color": L.roadHalo, "text-halo-width": 1.4, "text-opacity": 0.85 },
      });
    }
    if (m.pois) {
      // Shops and services as the game's blips: RDR2's black discs, GTA V's white pictograms,
      // GTA VI's rounded squares (icons ship in the app's local sprite, cut from the games' blips).
      // The car gets them too, a size up for arm's length (its Settings switch turns them off).
      const P = m.pois === true ? { prefix: "poi-" } : m.pois;
      // A kind with its own icon comes before the broader one it's part of: a theme without that
      // icon (not in its "only") leaves those places to the broader kind.
      const groups = [
        ["cafe", ["cafe"]],
        ["fastfood", ["fast_food", "food_court"]],
        ["food", ["restaurant", "fast_food", "food_court", "cafe", "ice_cream", "bakery"]],
        ["bar", ["bar", "pub", "beer", "alcohol_shop"]],
        ["hospital", ["hospital"]],
        ["doctor", ["hospital", "doctors", "clinic", "dentist"]],
        ["pharmacy", ["pharmacy", "chemist"]],
        ["bank", ["bank", "atm"]],
        ["post", ["post"]],
        ["hotel", ["lodging"]],
        ["train", ["railway"]],
        ["barber", ["hairdresser", "beauty"]],
        ["clothes", ["clothing_store", "shoes", "fashion"]],
        ["repair", ["car", "car_repair"]],
        ["store", ["grocery", "supermarket", "convenience", "department_store"]],
        // Shops OpenStreetMap doesn't classify: RDR2's general store; GTA V leaves them unmarked.
        ["shop", ["shop"], "store"],
        ["market", ["marketplace"]],
        ["fuel", ["fuel"]],
        ["police", ["police"]],
        ["theatre", ["cinema", "theatre"]],
        ["landmark", ["attraction", "museum", "monument", "castle"]],
        ["worship", ["place_of_worship"]],
        ["parking", ["parking"]],
        ["airport", ["aerodrome"]],
        ["school", ["school", "college"]],
        ["gym", ["fitness_centre", "sports_centre"]],
        ["toilet", ["toilets"]],
        ["mall", ["mall"]],
        ["charging", ["charging_station"]],
        ["tyre", ["tyres"]],
      ];
      // By class, then by subclass: OpenStreetMap files most small shops as class "shop" with the
      // kind in the subclass ("convenience", "beauty"), which by class alone got no blip.
      const byKey = (key, fallback) => {
        const e = ["match", ["get", key]];
        const seen = new Set();
        for (const [name, classes, iconName] of groups) {
          if (P.only && !P.only.includes(name)) continue;
          const fresh = classes.filter((c) => !seen.has(c) && (key === "class" || c !== "shop"));
          fresh.forEach((c) => seen.add(c));
          if (fresh.length) e.push(fresh, P.prefix + (iconName || name));
        }
        e.push(fallback);
        return e;
      };
      const icon = byKey("class", byKey("subclass", ""));
      // Only places that have a blip go into the layer at all. With no filter, all 2,285 places in
      // Connaught Place's tile (gates, bins, offices, bus stops that never show) went through
      // MapLibre's layout and collision on every move: the map crawled wherever icons were dense,
      // phone and car (8 Nov). With it, 1,364 (GTA) or 1,587 (Red Dead's general stores too).
      const iconClasses = new Set();
      const iconSubclasses = new Set();
      for (const [name, classes] of groups) {
        if (P.only && !P.only.includes(name)) continue;
        for (const c of classes) {
          iconClasses.add(c);
          if (c !== "shop") iconSubclasses.add(c);
        }
      }
      const hasIcon = ["any",
        ["match", ["get", "class"], [...iconClasses], true, false],
        ["match", ["get", "subclass"], [...iconSubclasses], true, false]];
      const rankAtMost = (n) => ["<=", ["coalesce", ["get", "rank"], 99], n];
      // Which icon wins a crowded corner goes by rank in four bands (1-5, 6-15, 16-30, the rest),
      // not by OpenStreetMap's rank itself: MapLibre draws a separate batch for every distinct
      // sort key in every tile, every frame, and Connaught Place has 314 of them. That was the
      // 8 fps on the phone there (8 Nov); place icons off ran at 120.
      const blipLayout = {
        "icon-image": icon,
        "icon-size": (P.size || 0.95) * (car ? 1.15 : 1),
        "symbol-sort-key": ["step", ["coalesce", ["get", "rank"], 99], 0, 6, 1, 16, 2, 31, 3],
        "icon-padding": 4,
        // GTA VI's pause map draws its places as pins standing on the spot (P.pins); tiles elsewhere.
        ...(P.pins ? { "icon-anchor": "bottom" } : {}),
      };
      const minzoom = P.minzoom || 15.5;
      // The most important places first (OpenStreetMap's rank, about 240 of Connaught Place's),
      // then more as you zoom in on the phone: rank 30 from zoom 16 and every place with a blip
      // from 17, so a pharmacy or a shirt shop still shows close up (why the old cap went, 30 Oct).
      // The car keeps the first set: a glance at speed needs the big ones, and it stays light.
      // Ids start "poi-blips" (Place icons off drops them all; a tap on any picks the place).
      layers.push({
        id: "poi-blips", type: "symbol", source: "omt", "source-layer": "poi", minzoom,
        ...(car ? {} : { maxzoom: 16 }),
        filter: ["all", hasIcon, rankAtMost(15)], layout: blipLayout,
      });
      if (!car) {
        layers.push({
          id: "poi-blips-near", type: "symbol", source: "omt", "source-layer": "poi", minzoom: Math.max(16, minzoom), maxzoom: 17,
          filter: ["all", hasIcon, rankAtMost(30)], layout: blipLayout,
        });
        layers.push({
          id: "poi-blips-close", type: "symbol", source: "omt", "source-layer": "poi", minzoom: 17,
          filter: hasIcon, layout: blipLayout,
        });
      }
    }
    if (m.fonts && m.fonts.pencil && !car) {
      // Parks and landmarks in pencil handwriting, the way the RDR2 map names farms and features.
      layers.push({
        id: "label-pencil", type: "symbol", source: "omt", "source-layer": "poi", minzoom: 15,
        filter: ["match", ["get", "class"], ["park", "garden", "attraction", "monument", "castle", "stadium"], true, false],
        layout: { "text-field": NAME, "text-font": [F.pencil], "text-size": custom ? 14 : 11, "text-max-width": 8, "text-optional": true },
        paint: { "text-color": L.pencil || L.place, "text-halo-color": L.placeHalo, "text-halo-width": 1 },
      });
    }
    // Red Dead's lettering (m.townLabels): neighbourhoods are its towns, in big heavy slab capitals
    // close together; cities are its regions, huge letters spread wide and fainter.
    const towns = custom && m.townLabels;
    layers.push({
      id: "label-place-local", type: "symbol", source: "omt", "source-layer": "place", minzoom: 12,
      filter: ["match", ["get", "class"], ["suburb", "neighbourhood", "quarter"], true, false],
      layout: {
        "text-field": text(NAME), "text-font": [towns ? F.city : F.place], "text-max-width": towns ? 9 : 7,
        "text-size": towns
          ? ["interpolate", ["linear"], ["zoom"], 12, 13 * labelScale, 16, 22 * labelScale]
          : ["interpolate", ["linear"], ["zoom"], 12, 10.5 * labelScale, 16, (custom ? 16 : 13) * labelScale],
        "text-letter-spacing": towns ? 0.08 : L.placeSpacing,
      },
      paint: { "text-color": L.place, "text-halo-color": L.placeHalo, "text-halo-width": towns ? 1.2 : 1.6, "text-opacity": car ? 0.8 : 0.9 },
    });
    layers.push({
      id: "label-place-city", type: "symbol", source: "omt", "source-layer": "place", maxzoom: 14,
      filter: ["match", ["get", "class"], ["city", "town", "village"], true, false],
      layout: {
        "text-field": text(NAME), "text-font": [F.city], "text-max-width": towns ? 30 : 8,
        "text-size": towns
          ? ["interpolate", ["linear"], ["zoom"], 5, 14, 12, 34 * labelScale]
          : ["interpolate", ["linear"], ["zoom"], 5, 11, 12, (custom ? 22 : 18) * labelScale],
        // Region names on the RDR2 map are set with very wide spacing.
        "text-letter-spacing": towns ? 1.1 : L.citySpacing !== undefined ? L.citySpacing : custom ? 0.6 : L.placeSpacing,
      },
      paint: { "text-color": L.city || L.place, "text-halo-color": L.placeHalo, "text-halo-width": 2, "text-opacity": towns ? 0.72 : 1 },
    });
    // Label-free themes (the GTA V map shows no names) hide the layers rather than drop them:
    // the app anchors its route line under "label-water", so that layer must exist.
    if (m.labels === false) {
      // Text labels only: the map's blips stay, like the game's pause map.
      for (const l of layers.slice(firstLabel)) if (!l.id.startsWith("poi-blips")) l.layout = Object.assign({}, l.layout, { visibility: "none" });
    }
    // GTA VI's pause map names its regions (m.viNames), copied from the launch captures (23 Nov): huge
    // condensed capitals ("MARIANA COUNTY", #6A6B7F at night), no outline. Only the big names, Gaurav's
    // call (23 Nov: the game has no small place names): countries, states and cities, no towns, areas
    // or roads. Phone only: the car maps carry no names.
    if (m.viNames && !car) {
      layers.push(...bigNameLayers(
        { city: m.viNames.region, minor: m.viNames.region, font: "BarlowCondensedBold", citySpacing: 0.02, minorSpacing: 0.12, sizes: [16, 28, 40] },
        custom, "vi"));
    }
    // Algothrim the same way (m.bigNames), in the site's Unbounded: white cities, grey states and countries.
    if (m.bigNames && !car) layers.push(...bigNameLayers(m.bigNames, custom, "big"));

    const style = {
      version: 8,
      name: `Overworld ${theme.name}${opts && opts.variant ? ` ${opts.variant}` : ""}${car ? " (car)" : ""}`,
      glyphs: custom ? opts.glyphs : `${OFM}/fonts/{fontstack}/{range}.pbf`,
      sources: {
        omt: { type: "vector", url: `${OFM}/planet` },
        ...(m.depth ? { seabed: SEABED_SOURCE } : {}),
        ...(m.hillshade ? { hills: HILLS_SOURCE } : {}),
        route: { type: "geojson", data: { type: "FeatureCollection", features: [] } },
      },
      layers,
    };
    // Lights the raised buildings: walls a shade darker than roofs, tinted with the time of day.
    if (m.light) style.light = { anchor: "viewport", color: m.light.color, intensity: m.light.intensity, position: [1.15, 210, 30] };
    return style;
  }

  // Cartographic fill patterns, drawn at runtime (on Android these ship in the sprite).
  function patternImage(id, theme) {
    const size = 16;
    const c = document.createElement("canvas");
    c.width = c.height = size;
    const g = c.getContext("2d");
    if (id === "hatch") {
      g.strokeStyle = "rgba(92, 88, 48, 0.42)";
      g.lineWidth = 1;
      for (let o = -size; o < size * 2; o += 6) {
        g.beginPath(); g.moveTo(o, size); g.lineTo(o + size, 0); g.stroke();
      }
    } else if (id === "stipple") {
      g.fillStyle = "rgba(92, 88, 48, 0.35)";
      for (const [x, y] of [[3, 4], [11, 2], [7, 10], [14, 12], [1, 13]]) g.fillRect(x, y, 1.3, 1.3);
    } else return null;
    return g.getImageData(0, 0, size, size);
  }

  window.Overworld = { THEMES, buildStyle, patternImage, paletteFor };
})();
