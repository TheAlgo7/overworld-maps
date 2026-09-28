// Overworld theme engine.
// Each theme is a palette. buildStyle() turns it into a full MapLibre style
// (the same JSON spec MapLibre Native reads on Android and on the Android Auto
// surface), in two variants: the phone look and a calmer car-safe look.
// Data: OpenFreeMap vector tiles, OpenMapTiles schema, OpenStreetMap data.

(function () {
  const OFM = "https://tiles.openfreemap.org";
  const NAME = ["coalesce", ["get", "name_en"], ["get", "name:latin"], ["get", "name"]];

  // Palettes follow the September 2026 design research (Metro Crime, Frontier, Vice Coast).
  // They are original interpretations: no game artwork, fonts, icons or names.
  const THEMES = {
    metro: {
      id: "metro",
      name: "Metro Crime",
      blurb: "Asphalt blocks, pale grey roads, one magenta route. The crime-sandbox pause map.",
      page: "#111314",
      dark: true,
      map: {
        land: "#262a2d", residential: "#2b2f32", industrial: "#2e3235",
        park: "#2f3d31", wood: "#2c3a2f", grass: "#2f3d31", sand: "#3a3a33",
        water: "#4f7389", waterway: "#4f7389",
        building: "#34393c", buildingLine: null,
        rail: "#50565a",
        roads: {
          motorway: { fill: "#d8d8d2", casing: null },
          major: { fill: "#b9bab5", casing: null },
          mid: { fill: "#8f9295", casing: null },
          minor: { fill: "#6a6f72", casing: null },
          service: { fill: "#565b5e", casing: null },
        },
        glow: null,
        label: {
          road: "#dcdcd6", roadHalo: "#1c1f21", roadFont: "Noto Sans Regular",
          place: "#f0f0ea", placeHalo: "#16181a", placeFont: "Noto Sans Bold", placeSpacing: 0.08, upper: true,
          water: "#a9c3cf", waterHalo: "#2e4756",
        },
        route: { line: "#c449c7", casing: "#2e0f30", glow: null },
      },
      marker: { fill: "#f4f4ef", stroke: "#15181a" },
      hud: {
        bg: "rgba(20,22,24,0.86)", fg: "#f2f2ee", sub: "#a4a8aa", accent: "#c449c7", good: "#68a94a",
        border: "rgba(255,255,255,0.08)", font: "'Barlow Condensed', sans-serif", fontKey: "condensed", weight: 600, upper: true,
        card: "#5e2560",
      },
      overlay: null,
    },

    frontier: {
      id: "frontier",
      name: "Frontier",
      blurb: "Parchment land, ink roads, double-line highways, hatched forest, railway ties. The western atlas.",
      page: "#1b1611",
      dark: false,
      map: {
        land: "#d9c69c", residential: "#d2bf94", industrial: "#cdb98f",
        park: "#cbc394", wood: "#c4bc8a", grass: "#cbc394", sand: "#e0cb9c",
        water: "#a3b3a9", waterway: "#a3b3a9",
        waterLine: "#56685f",
        building: "#cdb98e", buildingLine: "#ad9870",
        rail: "#342d26", railTies: true,
        roads: {
          motorway: { fill: "#efe4cc", casing: "#342d26" },
          major: { fill: "#efe4cc", casing: "#342d26" },
          mid: { fill: "#4a3f33", casing: null },
          minor: { fill: "#756856", casing: null },
          service: { fill: "#8f826d", casing: null },
        },
        patterns: { wood: "hatch", park: "stipple" },
        glow: null,
        label: {
          road: "#342d26", roadHalo: "#e6d8b6", roadFont: "Noto Sans Italic",
          place: "#2c241d", placeHalo: "#e6d8b6", placeFont: "Noto Sans Bold", placeSpacing: 0.28, upper: true,
          water: "#43574f", waterHalo: "#bfcbc3",
        },
        route: { line: "#a44838", casing: "#efe4cc", glow: null },
      },
      marker: { fill: "#2c241d", stroke: "#efe4cc" },
      hud: {
        bg: "#efe4cc", fg: "#2c241d", sub: "#6b5b47", accent: "#a44838", good: "#4f6b35",
        border: "#342d26", font: "'IM Fell English', serif", fontKey: "serif", weight: 400, upper: false,
        card: "#3a2e22",
      },
      overlay: "paper",
    },

    vice: {
      id: "vice",
      name: "Vice Coast",
      blurb: "Night-navy land, sand boulevards, cyan water, one coral route. The beach-city night drive.",
      page: "#0b1520",
      dark: true,
      map: {
        land: "#152737", residential: "#182c3e", industrial: "#1a2f42",
        park: "#123a3a", wood: "#103535", grass: "#123a3a", sand: "#4a4536",
        water: "#114a5e", waterway: "#114a5e",
        waterLine: "#55c5d6", waterLineBlur: 3, waterLineOpacity: 0.6,
        building: "#1d3448", buildingLine: null,
        rail: "#2e4a60",
        roads: {
          motorway: { fill: "#eed3a3", casing: "#152737" },
          major: { fill: "#d6bd92", casing: "#152737" },
          mid: { fill: "#6f8aa0", casing: null },
          minor: { fill: "#3a5268", casing: null },
          service: { fill: "#2f4559", casing: null },
        },
        glow: { groups: ["motorway", "major"], opacity: 0.28, blur: 6, spread: 3.2 },
        label: {
          road: "#f7f5ef", roadHalo: "#152737", roadFont: "Noto Sans Regular",
          place: "#f7f5ef", placeHalo: "#152737", placeFont: "Noto Sans Bold", placeSpacing: 0.18, upper: true,
          water: "#8fe1ec", waterHalo: "#114a5e",
        },
        route: { line: "#f46f98", casing: "#3a0f22", glow: "#f46f98" },
      },
      marker: { fill: "#f7f5ef", stroke: "#55c5d6" },
      hud: {
        bg: "rgba(16,31,45,0.88)", fg: "#f7f5ef", sub: "#a9c6d3", accent: "#f46f98", good: "#55c5d6",
        border: "rgba(85,197,214,0.45)", font: "'Chakra Petch', sans-serif", fontKey: "tech", weight: 600, upper: true,
        card: "#a63d65",
      },
      overlay: null,
    },
  };

  // Road groups over OpenMapTiles transportation classes, drawn bottom to top.
  const GROUPS = [
    { key: "service", classes: ["service"], minzoom: 14, w: [0, 0, 2.2, 6] },
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
    const scale = car ? 1.25 : 1;
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
      out.push({
        id: `${tag}-${g.key}`, type: "line", source: "omt", "source-layer": "transportation",
        minzoom: g.minzoom, filter: classFilter(g.classes, brunnel),
        layout: { "line-cap": brunnel ? "butt" : "round", "line-join": "round" },
        paint: { "line-color": spec.fill, "line-width": widthExpr(g.w, scale), "line-opacity": faded ? 0.4 : 1 },
      });
    }
    return out;
  }

  function buildStyle(theme, opts) {
    const car = !!(opts && opts.car);
    const m = theme.map;
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

    if (m.patterns && !car) {
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
      paint: Object.assign(
        { "fill-color": m.building, "fill-opacity": ["interpolate", ["linear"], ["zoom"], 14, 0, 15, car ? 0.6 : 0.9] },
        m.buildingLine ? { "fill-outline-color": m.buildingLine } : {}
      ),
    });

    const railFilter = ["all", ["match", ["get", "class"], ["rail", "transit"], true, false], ["!=", ["get", "brunnel"], "tunnel"]];
    layers.push({
      id: "rail", type: "line", source: "omt", "source-layer": "transportation", minzoom: 11, filter: railFilter,
      paint: { "line-color": m.rail, "line-width": ["interpolate", ["linear"], ["zoom"], 11, 0.6, 18, 2] },
    });
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

    layers.push(...roadLayers(m, car, "tunnel"));
    layers.push(...roadLayers(m, car, null));
    layers.push(...roadLayers(m, car, "bridge"));

    // Route: remaining part of the trip. The app updates the source while driving.
    const R = m.route;
    if (R.glow && !car) {
      layers.push({
        id: "route-glow", type: "line", source: "route", layout: { "line-cap": "round", "line-join": "round" },
        paint: { "line-color": R.glow, "line-opacity": 0.45, "line-blur": 8, "line-width": ["interpolate", ["linear"], ["zoom"], 10, 10, 18, 34] },
      });
    }
    layers.push({
      id: "route-casing", type: "line", source: "route", layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": R.casing, "line-width": ["interpolate", ["linear"], ["zoom"], 10, car ? 7 : 5.5, 18, car ? 22 : 18] },
    });
    layers.push({
      id: "route-line", type: "line", source: "route", layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": R.line, "line-width": ["interpolate", ["linear"], ["zoom"], 10, car ? 4.5 : 3.5, 18, car ? 15 : 12] },
    });

    layers.push({
      id: "label-water", type: "symbol", source: "omt", "source-layer": "water_name", minzoom: 11,
      filter: ["match", ["geometry-type"], ["Point", "MultiPoint"], true, false],
      layout: { "text-field": NAME, "text-font": ["Noto Sans Italic"], "text-size": 12 * labelScale, "text-letter-spacing": 0.12, "text-max-width": 8 },
      paint: { "text-color": L.water, "text-halo-color": L.waterHalo, "text-halo-width": 1.4 },
    });
    layers.push({
      id: "label-waterway", type: "symbol", source: "omt", "source-layer": "waterway", minzoom: 12,
      filter: ["match", ["get", "class"], ["river", "canal"], true, false],
      layout: { "text-field": NAME, "text-font": ["Noto Sans Italic"], "text-size": 12 * labelScale, "text-letter-spacing": 0.12, "symbol-placement": "line" },
      paint: { "text-color": L.water, "text-halo-color": L.waterHalo, "text-halo-width": 1.4 },
    });
    layers.push({
      id: "label-road-major", type: "symbol", source: "omt", "source-layer": "transportation_name", minzoom: 13,
      filter: ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary", "tertiary"], true, false],
      layout: {
        "text-field": NAME, "text-font": [L.roadFont], "symbol-placement": "line",
        "text-size": ["interpolate", ["linear"], ["zoom"], 13, 10.5 * labelScale, 18, 14 * labelScale],
        "text-letter-spacing": 0.04, "symbol-spacing": 320,
      },
      paint: { "text-color": L.road, "text-halo-color": L.roadHalo, "text-halo-width": 1.6 },
    });
    if (!car) {
      layers.push({
        id: "label-road-minor", type: "symbol", source: "omt", "source-layer": "transportation_name", minzoom: 15.5,
        filter: ["match", ["get", "class"], ["minor", "service"], true, false],
        layout: { "text-field": NAME, "text-font": [L.roadFont], "symbol-placement": "line", "text-size": 10.5 },
        paint: { "text-color": L.road, "text-halo-color": L.roadHalo, "text-halo-width": 1.4, "text-opacity": 0.85 },
      });
    }
    layers.push({
      id: "label-place-local", type: "symbol", source: "omt", "source-layer": "place", minzoom: 12,
      filter: ["match", ["get", "class"], ["suburb", "neighbourhood", "quarter"], true, false],
      layout: {
        "text-field": text(NAME), "text-font": [L.placeFont], "text-max-width": 7,
        "text-size": ["interpolate", ["linear"], ["zoom"], 12, 10.5 * labelScale, 16, 13 * labelScale],
        "text-letter-spacing": L.placeSpacing,
      },
      paint: { "text-color": L.place, "text-halo-color": L.placeHalo, "text-halo-width": 1.6, "text-opacity": car ? 0.8 : 0.9 },
    });
    layers.push({
      id: "label-place-city", type: "symbol", source: "omt", "source-layer": "place", maxzoom: 14,
      filter: ["match", ["get", "class"], ["city", "town", "village"], true, false],
      layout: {
        "text-field": text(NAME), "text-font": [L.placeFont], "text-max-width": 8,
        "text-size": ["interpolate", ["linear"], ["zoom"], 5, 11, 12, 18 * labelScale],
        "text-letter-spacing": L.placeSpacing,
      },
      paint: { "text-color": L.place, "text-halo-color": L.placeHalo, "text-halo-width": 2 },
    });

    return {
      version: 8,
      name: `Overworld ${theme.name}${car ? " (car)" : ""}`,
      glyphs: `${OFM}/fonts/{fontstack}/{range}.pbf`,
      sources: {
        omt: { type: "vector", url: `${OFM}/planet` },
        route: { type: "geojson", data: { type: "FeatureCollection", features: [] } },
      },
      layers,
    };
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

  window.Overworld = { THEMES, buildStyle, patternImage };
})();
