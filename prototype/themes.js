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
        // Blips: the game's white pictograms (T-shirt, scissors, spray gun...), and since 30 Oct the
        // sheet's house, meditating figure, camera, P and plane for hotels, temples, landmarks,
        // parking and airports (Gaurav wanted far more of the map marked). No food: GTA V has no
        // blip for it.
        // Small, as on the game's radar: about two thirds of the player marker.
        pois: { prefix: "gta-", minzoom: 14.5, size: 0.72,
          only: ["bar", "doctor", "pharmacy", "bank", "barber", "clothes", "repair", "store", "fuel", "police", "theatre",
            "hotel", "worship", "landmark", "parking", "airport"] },
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
        building: "#e4cfaa", buildingLine: "#40423d",
        rail: "#40423d", railStyle: "dots", railDot: "#ebddc0",
        roadScale: 1.1,
        roads: {
          motorway: { fill: "#40423d", casing: null },
          major: { fill: "#40423d", casing: null },
          mid: { fill: "#40423d", casing: null },
          minor: { fill: "#46453f", casing: null },
          service: { fill: "#55524a", casing: null, dash: [2.2, 1.4] },
        },
        trails: { color: "#40423d", dash: [1.6, 1.6] },
        // A paper map seen from straight above: the surveyor's bridge, the road in a band of bare
        // paper with an ink parapet line along each side (see parapetLayers).
        bridge: { edge: "#40423d", gap: true },
        patterns: { wood: "hatch", park: "stipple" }, patternsInCar: true, railDotsInCar: true,
        glow: null,
        roadLabels: false,
        // The game's black-disc blips, small as on its map (only the kinds the sheet has a blip for).
        pois: { prefix: "poi-", size: 0.68,
          only: ["food", "bar", "doctor", "pharmacy", "bank", "post", "hotel", "train", "barber", "clothes", "repair", "store", "shop",
            "market", "fuel", "police", "theatre", "landmark"] },
        // Android map lettering (bundled SDF glyphs, see tools/make_glyphs.py). The web preview
        // falls back to OpenFreeMap's Noto fonts below.
        fonts: { place: "RalewayBlack", city: "MerriweatherBlack", water: "CrimsonBoldItalic", pencil: "HomemadeApple" },
        label: {
          road: "#40423d", roadHalo: "#dec29b", roadFont: "Noto Sans Italic",
          place: "#40423d", placeHalo: "rgba(222,194,155,0.7)", placeFont: "Noto Sans Bold", placeSpacing: 0.22, upper: true,
          water: "#3b3d38", waterHalo: "rgba(158,153,133,0.6)", pencil: "#716454",
        },
        route: { line: "#cc0000", casing: "#7a0e1d", glow: null },
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

    // GTA VI. Measured 2026-10-09 from Rockstar's official "An Extended Look" (the 4K download
    // from rockstargames.com/VI): a sat-nav style minimap lit by the time of day. Pale warm-grey
    // ground and broad lighter roads, raised cool-grey buildings, grey-teal water, no names, and
    // one pink route and waypoint (#FB74A5, the same in every frame). At golden hour the same map
    // goes dim and warm; at night it turns violet slate over near-black water. The app picks the
    // palette from the sun ("variants" below).
    gta6: {
      id: "gta6",
      skin: "gta6",
      name: "GTA VI",
      blurb: "Pale sat-nav streets, raised buildings, one pink route. The Leonida minimap, turning violet at night.",
      page: "#bcb4af",
      dark: false,
      map: {
        land: "#beb6b1", residential: "#beb6b1", industrial: "#b9b4b0",
        park: "#8e9c8b", wood: "#879784", grass: "#93a190", sand: "#cec3b4",
        water: "#739ba2", waterway: "#739ba2",
        building: "#aeafb7", buildingLine: null,
        // Raised blocks: real heights where OpenStreetMap has them, a low default where it doesn't.
        buildings3d: { color: "#aeafb7", opacity: 0.94, heightScale: 1, minHeight: 4 },
        // Flyovers seen from the minimap's 60 degree tilt: a concrete side under the deck and its
        // shadow on the ground (see bridgeLayers).
        bridge: { lift: 5.0, face: 1.7, faceColor: "#9b928c", edge: "#aaa19b", shadow: { color: "#3a3430", opacity: 0.5 } },
        light: { color: "#ffffff", intensity: 0.5 },
        rail: "#a3a0a0",
        // Roads are wide surfaces, a shade lighter than the ground, with no outlines.
        roadScale: 1.6,
        roads: {
          motorway: { fill: "#d4cbc6", casing: null },
          major: { fill: "#d2c9c4", casing: null },
          mid: { fill: "#cfc7c2", casing: null },
          minor: { fill: "#ccc4bf", casing: null },
          service: { fill: "#c6beb9", casing: null },
        },
        // White dashed lane dividers along highways, like the minimap's multi-lane decks.
        laneDashes: { color: "#ebe5e1", groups: ["motorway", "major"] },
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
        // Shops and services as GTA VI's own blips: pale rounded squares with a slate rim (red for
        // medical), from the sheet Gaurav saved (tools/cut_vi_blips.py); personal builds only.
        pois: { prefix: "vi-", minzoom: 15.2, size: 0.85,
          only: ["food", "bar", "doctor", "pharmacy", "barber", "clothes", "repair", "store", "fuel"] },
      },
      variants: {
        // Golden hour: the minimap dims and warms; roads keep their lead over the ground.
        dusk: {
          page: "#6e6c68",
          dark: true,
          map: {
            land: "#6f6d69", residential: "#6f6d69", industrial: "#6b6a68",
            park: "#5b6759", wood: "#566355", grass: "#5f6b5d", sand: "#7e7666",
            water: "#2f5867", waterway: "#2f5867",
            building: "#5d6370",
            buildings3d: { color: "#5f6573", opacity: 0.94, heightScale: 1, minHeight: 4 },
            bridge: { lift: 5.0, face: 1.7, faceColor: "#4d4a45", edge: "#66634f", shadow: { color: "#1e1c1a", opacity: 0.55 } },
            light: { color: "#fff0e2", intensity: 0.38 },
            rail: "#5c5a58",
            roads: {
              motorway: { fill: "#857f6e", casing: null },
              major: { fill: "#83806f", casing: null },
              mid: { fill: "#7f7c72", casing: null },
              minor: { fill: "#7b7a74", casing: null },
              service: { fill: "#74736e", casing: null },
            },
            // Golden hour shows the highways' lanes: warm lanes between pale grey stripes down the
            // middle and along both edges of each carriageway (Extended Look 12:41 and 12:57, lanes
            // #756f63, stripes #787a77, a stripe about half a lane wide), not white dashes.
            laneDashes: null,
            laneStripes: { color: "#898c85", groups: ["motorway", "major"], share: 0.14 },
          },
        },
        // Night: violet slate ground and roads, near-black navy water, deep teal grass and parks
        // (the game's own driving footage at night: #13363a beside roads that match these).
        night: {
          page: "#1f1e2a",
          dark: true,
          map: {
            land: "#312c38", residential: "#312c38", industrial: "#2e2a36",
            park: "#15383b", wood: "#123133", grass: "#14363a", sand: "#3a3540",
            water: "#0d1120", waterway: "#0d1120",
            building: "#3c3546",
            buildings3d: { color: "#41394c", opacity: 0.95, heightScale: 1, minHeight: 4 },
            // Stacked ramps at night stand apart on near-black gaps (Extended Look 20:22, #160f1f
            // between the decks): dark, wider kerbs and a deeper shadow, where the day has pale kerbs.
            bridge: {
              lift: 5.0, face: 1.7, faceColor: "#1a1421", edge: "#17101f", edgeWidth: [1.1, 2.8, 4.8],
              shadow: { color: "#120c18", opacity: 0.75 },
            },
            light: { color: "#ddd4ee", intensity: 0.3 },
            rail: "#4a4552",
            roads: {
              motorway: { fill: "#4c4556", casing: null },
              major: { fill: "#4a4353", casing: null },
              mid: { fill: "#46404f", casing: null },
              minor: { fill: "#433d4b", casing: null },
              service: { fill: "#3d3845", casing: null },
            },
            laneDashes: { color: "#625b6e", groups: ["motorway", "major"] },
          },
        },
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
  };

  /** The theme's map palette for a time-of-day variant ("dusk", "night"), or its base palette. */
  function paletteFor(theme, variant) {
    const v = variant && theme.variants && theme.variants[variant];
    if (!v) return theme.map;
    const m = Object.assign({}, theme.map, v.map);
    m.roads = Object.assign({}, theme.map.roads, v.map.roads);
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
    layers.push({
      id: "waterway", type: "line", source: "omt", "source-layer": "waterway",
      filter: ["!=", ["get", "brunnel"], "tunnel"],
      paint: {
        "line-color": m.waterway,
        "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 10, ["match", ["get", "class"], ["river", "canal"], 1.4, 0.4], 18, ["match", ["get", "class"], ["river", "canal"], 12, 3]],
      },
    });

    layers.push({
      id: "building", type: "fill", source: "omt", "source-layer": "building", minzoom: 14,
      // Raised buildings take over from the flat footprints as they grow (see building-3d).
      ...(m.buildings3d ? { maxzoom: 16 } : {}),
      paint: Object.assign(
        { "fill-color": m.building, "fill-opacity": ["interpolate", ["linear"], ["zoom"], 14, 0, 15, car && !m.buildings3d ? 0.6 : 0.9] },
        m.buildingLine ? { "fill-outline-color": m.buildingLine } : {}
      ),
    });

    const railFilter = ["all", ["match", ["get", "class"], ["rail", "transit"], true, false], ["!=", ["get", "brunnel"], "tunnel"]];
    const dotted = m.railStyle === "dots";
    layers.push({
      id: "rail", type: "line", source: "omt", "source-layer": "transportation", minzoom: 11, filter: railFilter,
      paint: { "line-color": m.rail, "line-width": ["interpolate", ["linear"], ["zoom"], 11, dotted ? 1.2 : 0.6, 18, dotted ? 5 : 2] },
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
        paint: { "line-color": m.trails.color, "line-width": ["interpolate", ["linear"], ["zoom"], 15, 0.8, 18, 1.6], "line-dasharray": m.trails.dash },
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
    // Solid lane stripes instead (GTA VI at golden hour): one down the middle of each carriageway and
    // one along each edge, each m.laneStripes.share of the road's width.
    const laneStripes = (id, brunnel) => {
      const S = m.laneStripes;
      const scale = (car ? 1.25 : 1) * (m.roadScale || 1);
      const layout = { "line-join": "round", ...(brunnel === "bridge" ? { "line-sort-key": ["coalesce", ["get", "layer"], 1] } : {}) };
      const paint = { "line-color": S.color, "line-opacity": ["interpolate", ["linear"], ["zoom"], 15, 0, 15.6, 1] };
      return GROUPS.filter((g) => S.groups.includes(g.key)).flatMap((g) => [
        {
          id: `${id}-${g.key}-middle`, type: "line", source: "omt", "source-layer": "transportation", minzoom: 15,
          filter: classFilter(g.classes, brunnel), layout,
          paint: { ...paint, "line-width": widthExpr(g.w, scale * S.share) },
        },
        {
          id: `${id}-${g.key}-edges`, type: "line", source: "omt", "source-layer": "transportation", minzoom: 15,
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
      const raw = ["max", B.minHeight, ["*", B.heightScale, ["coalesce", ["get", "render_height"], 5]]];
      const height = B.maxHeight ? ["min", B.maxHeight, raw] : raw;
      layers.push({
        id: "building-3d", type: "fill-extrusion", source: "omt", "source-layer": "building", minzoom: 14.5,
        filter: ["!=", ["get", "hide_3d"], true],
        paint: {
          "fill-extrusion-color": B.color,
          "fill-extrusion-height": ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, height],
          "fill-extrusion-base": ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, ["*", B.heightScale, ["coalesce", ["get", "render_min_height"], 0]]],
          "fill-extrusion-opacity": B.opacity,
          // Walls darken toward the ground, so the blocks sit on the map instead of floating on it.
          "fill-extrusion-vertical-gradient": true,
        },
      });
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
      ? { place: m.fonts.place, city: m.fonts.city, water: m.fonts.water, road: m.fonts.place, pencil: m.fonts.pencil }
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
    if (m.pois && !car) {
      // Shops and services as the game's blips: RDR2's black discs, GTA V's white pictograms,
      // GTA VI's rounded squares (icons ship in the app's local sprite, cut from the games' blips).
      const P = m.pois === true ? { prefix: "poi-" } : m.pois;
      const groups = [
        ["food", ["restaurant", "fast_food", "food_court", "cafe", "ice_cream", "bakery"]],
        ["bar", ["bar", "pub", "beer", "alcohol_shop"]],
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
      layers.push({
        id: "poi-blips", type: "symbol", source: "omt", "source-layer": "poi", minzoom: P.minzoom || 15.5,
        // Every place with a blip, not only OpenStreetMap's top-ranked ones: in a dense area like
        // Chhatarpur the rank cap left a pharmacy and a shirt shop out of dozens of places.
        // Collisions still thin them (icon-padding), the important first (sort key).
        layout: {
          "icon-image": icon,
          "icon-size": P.size || 0.95, "symbol-sort-key": ["coalesce", ["get", "rank"], 99], "icon-padding": 4,
        },
      });
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
    layers.push({
      id: "label-place-local", type: "symbol", source: "omt", "source-layer": "place", minzoom: 12,
      filter: ["match", ["get", "class"], ["suburb", "neighbourhood", "quarter"], true, false],
      layout: {
        "text-field": text(NAME), "text-font": [F.place], "text-max-width": 7,
        "text-size": ["interpolate", ["linear"], ["zoom"], 12, 10.5 * labelScale, 16, (custom ? 16 : 13) * labelScale],
        "text-letter-spacing": L.placeSpacing,
      },
      paint: { "text-color": L.place, "text-halo-color": L.placeHalo, "text-halo-width": 1.6, "text-opacity": car ? 0.8 : 0.9 },
    });
    layers.push({
      id: "label-place-city", type: "symbol", source: "omt", "source-layer": "place", maxzoom: 14,
      filter: ["match", ["get", "class"], ["city", "town", "village"], true, false],
      layout: {
        "text-field": text(NAME), "text-font": [F.city], "text-max-width": 8,
        "text-size": ["interpolate", ["linear"], ["zoom"], 5, 11, 12, (custom ? 22 : 18) * labelScale],
        // Region names on the RDR2 map are set with very wide spacing.
        "text-letter-spacing": custom ? 0.6 : L.placeSpacing,
      },
      paint: { "text-color": L.place, "text-halo-color": L.placeHalo, "text-halo-width": 2 },
    });
    // Label-free themes (the GTA V map shows no names) hide the layers rather than drop them:
    // the app anchors its route line under "label-water", so that layer must exist.
    if (m.labels === false) {
      // Text labels only: the map's blips stay, like the game's pause map.
      for (const l of layers.slice(firstLabel)) if (l.id !== "poi-blips") l.layout = Object.assign({}, l.layout, { visibility: "none" });
    }

    const style = {
      version: 8,
      name: `Overworld ${theme.name}${opts && opts.variant ? ` ${opts.variant}` : ""}${car ? " (car)" : ""}`,
      glyphs: custom ? opts.glyphs : `${OFM}/fonts/{fontstack}/{range}.pbf`,
      sources: {
        omt: { type: "vector", url: `${OFM}/planet` },
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
