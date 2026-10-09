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
        // Shop blips: the game's white pictograms (T-shirt, scissors, spray gun...). No food or hotels:
        // the GTA map only marks shops and services you can use.
        // Small, as on the game's radar: about two thirds of the player marker.
        pois: { prefix: "gta-", minzoom: 15, size: 0.72,
          only: ["bar", "doctor", "pharmacy", "bank", "barber", "clothes", "repair", "store", "fuel", "police", "theatre"] },
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
        patterns: { wood: "hatch", park: "stipple" }, patternsInCar: true, railDotsInCar: true,
        glow: null,
        roadLabels: false,
        // The game's black-disc blips, small as on its map.
        pois: { prefix: "poi-", size: 0.68 },
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
        buildings3d: { color: "#aeafb7", opacity: 0.94, heightScale: 1, minHeight: 4, maxHeight: 40 },
        light: { color: "#ffffff", intensity: 0.42 },
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
            buildings3d: { color: "#5f6573", opacity: 0.94, heightScale: 1, minHeight: 4, maxHeight: 40 },
            light: { color: "#fff0e2", intensity: 0.38 },
            rail: "#5c5a58",
            roads: {
              motorway: { fill: "#857f6e", casing: null },
              major: { fill: "#83806f", casing: null },
              mid: { fill: "#7f7c72", casing: null },
              minor: { fill: "#7b7a74", casing: null },
              service: { fill: "#74736e", casing: null },
            },
            laneDashes: { color: "#a39d8c", groups: ["motorway", "major"] },
          },
        },
        // Night: violet slate ground and roads, near-black navy water.
        night: {
          page: "#1f1e2a",
          dark: true,
          map: {
            land: "#312c38", residential: "#312c38", industrial: "#2e2a36",
            park: "#283130", wood: "#252d2c", grass: "#2a3332", sand: "#3a3540",
            water: "#0d1120", waterway: "#0d1120",
            building: "#3c3546",
            buildings3d: { color: "#41394c", opacity: 0.95, heightScale: 1, minHeight: 4, maxHeight: 40 },
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

  function roadLayers(m, car, brunnel) {
    const scale = (car ? 1.25 : 1) * (m.roadScale || 1);
    const out = [];
    const tag = brunnel || "road";
    const faded = brunnel === "tunnel";
    // Casings first, then fills, so crossings read cleanly.
    for (const g of GROUPS) {
      const spec = m.roads[g.key];
      const casing = spec.casing || (brunnel === "bridge" ? m.land : null);
      if (!casing) continue;
      out.push({
        id: `${tag}-${g.key}-casing`, type: "line", source: "omt", "source-layer": "transportation",
        minzoom: Math.max(g.minzoom, 11), filter: classFilter(g.classes, brunnel),
        layout: { "line-cap": brunnel ? "butt" : "round", "line-join": "round" },
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
        layout: { "line-cap": brunnel || spec.dash ? "butt" : "round", "line-join": "round" },
        paint,
      });
    }
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
    layers.push(...roadLayers(m, car, null));
    if (m.laneDashes) {
      // Lane dividers down the middle of each carriageway (OpenStreetMap draws dual carriageways
      // as one line per direction, so the middle of the line is between its lanes).
      const classes = GROUPS.filter((g) => m.laneDashes.groups.includes(g.key)).flatMap((g) => g.classes);
      layers.push({
        id: "lane-dashes", type: "line", source: "omt", "source-layer": "transportation", minzoom: 15,
        filter: ["all", ["match", ["get", "class"], classes, true, false], ["match", ["get", "brunnel"], ["tunnel"], false, true]],
        layout: { "line-join": "round" },
        paint: {
          "line-color": m.laneDashes.color,
          "line-width": ["interpolate", ["linear"], ["zoom"], 15, 0.8, 18, 2.2],
          "line-dasharray": [3, 3],
          "line-opacity": ["interpolate", ["linear"], ["zoom"], 15, 0, 15.6, 0.9],
        },
      });
    }
    layers.push(...roadLayers(m, car, "bridge"));
    if (m.buildings3d) {
      // GTA VI's minimap raises its buildings. Heights come from OpenStreetMap (render_height,
      // 5 m where nothing is tagged), capped so towers don't wall off the view, and grown in
      // from zoom 14.5 to 16.
      const B = m.buildings3d;
      const height = ["min", B.maxHeight, ["max", B.minHeight, ["*", B.heightScale, ["coalesce", ["get", "render_height"], 5]]]];
      layers.push({
        id: "building-3d", type: "fill-extrusion", source: "omt", "source-layer": "building", minzoom: 14.5,
        filter: ["!=", ["get", "hide_3d"], true],
        paint: {
          "fill-extrusion-color": B.color,
          "fill-extrusion-height": ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, height],
          "fill-extrusion-base": ["interpolate", ["linear"], ["zoom"], 14.5, 0, 16, ["*", B.heightScale, ["coalesce", ["get", "render_min_height"], 0]]],
          "fill-extrusion-opacity": B.opacity,
          "fill-extrusion-vertical-gradient": false,
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
      // Shops and services as the game's blips: RDR2's black discs, GTA V's white pictograms
      // (icons ship in the app's local sprite, cut from the games' blip textures).
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
      ];
      const icon = ["match", ["get", "class"]];
      for (const [name, classes, iconName] of groups) if (!P.only || P.only.includes(name)) icon.push(classes, P.prefix + (iconName || name));
      icon.push("");
      layers.push({
        id: "poi-blips", type: "symbol", source: "omt", "source-layer": "poi", minzoom: P.minzoom || 15.5,
        filter: ["<=", ["coalesce", ["get", "rank"], 99], 20],
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
