// Every README picture, wide (desktop) and tall (phone). The README swaps them with
// <picture><source media="(max-width: 600px)">, so a phone gets a picture it can read.
// Devices stay whole on all four sides: tools/readme_images.py refuses to save a cut-off one.
// Phones sit over the car screen's lower left (its ETA card), so the route and the junction stay clear.
const PLUM = 'radial-gradient(900px 700px at 74% 58%, #4a1c3a 0%, rgba(74,28,58,0) 70%), linear-gradient(180deg,#120e17,#0b0a0f)';
const PLUM_TALL = 'radial-gradient(900px 900px at 60% 76%, #4a1c3a 0%, rgba(74,28,58,0) 70%), linear-gradient(180deg,#120e17,#0b0a0f)';
const PINK = '#fb74a5';

const WORLDS = {
  gta5: {
    bg: 'radial-gradient(900px 700px at 70% 55%, #2b1550 0%, rgba(43,21,80,0) 72%), linear-gradient(180deg,#0d0b12,#070609)',
    color: '#f2f0f6', accent: '#b98cff', line: '#9d5cff', head: ['Drive the', 'dark city'],
    sub: "GTA V's pause map: near-black land, flat grey roads, ice-pale water and a purple waypoint route.",
    car: 'car-gta5.webp', phone: 'phone-gta5-map.webp', glow: '#7c3cff',
    extras: (m) => [{ type: 'pop', src: 'card-gta5.png', x: m ? 360 : 930, y: m ? 1012 : 474, w: m ? 440 : 400, rot: -3 },
                    { type: 'sticker', src: 'puck_gta5.png', x: m ? 860 : 1400, y: m ? 1070 : 540, w: m ? 110 : 96, rot: 8 }],
  },
  rdr2: {
    bg: 'radial-gradient(1100px 800px at 62% 50%, #ead3ad 0%, #d8bb8c 60%, #c9a874 100%)',
    color: '#2a2620', accent: '#b3121b', line: '#c8102e', head: ['Ride the', 'frontier'],
    sub: "Red Dead 2's paper map: ink roads, dotted railways, pencil place names and a red trail to follow.",
    car: 'car-rdr2.webp', phone: 'phone-rdr2-drive.webp', glow: null,
    extras: (m) => [{ type: 'sticker', src: 'limit_rdr_40.png', x: m ? 400 : 960, y: m ? 1000 : 480, w: m ? 170 : 150, rot: -8 },
                    { type: 'sticker', src: 'puck_rdr2.png', x: m ? 660 : 1190, y: m ? 1080 : 570, w: m ? 100 : 90, rot: 6 }],
  },
  gta6: {
    bg: 'linear-gradient(160deg,#f0a05a 0%,#c8607a 34%,#4b2a6e 66%,#1d1536 100%)',
    color: '#fff6ee', accent: '#ffd27a', line: PINK, head: ['It follows', 'the sun'],
    sub: "GTA VI's minimap: raised buildings and a pink route. Pale by day, warm at golden hour, violet at night.",
    car: 'car-gta6-night.webp', phone: 'phone-gta6-drive.webp', glow: '#ff8fb5',
    extras: (m) => [{ type: 'pop', src: 'card-gta6.png', x: m ? 360 : 930, y: m ? 1004 : 468, w: m ? 440 : 400, rot: -3, radius: m ? 16 : 14 },
                    { type: 'sticker', src: 'limit_gta6_60.png', x: m ? 850 : 1390, y: m ? 1050 : 520, w: m ? 140 : 124, rot: 10 }],
  },
};

window.LAYOUTS = {
  hero: {
    w: 1600, h: 820, bg: PLUM, color: '#f7f2f8', accent: PINK,
    brand: { x: 80, y: 74, size: 30 },
    head: { x: 76, y: 236, size: 150, max: 640, lines: ['Real roads.', 'Game map.'] },
    sub: { x: 80, width: 500, size: 27, gap: 34, text: 'Turn-by-turn navigation drawn like GTA V, Red Dead 2 and GTA VI. Built for Android Auto, and the phone too.' },
    line: { width: 20, color: PINK, points: [[-40, 770], [300, 720], [640, 706], [900, 770], [1200, 712], [1640, 750]] },
    items: [
      { type: 'car', src: 'car-gta5.webp', x: 730, y: 92, w: 820, rot: -4, z: 20 },
      { type: 'phone', src: 'phone-gta6-drive.webp', x: 700, y: 330, w: 220, rot: -5, z: 22 },
      { type: 'phone', src: 'phone-rdr2-drive.webp', x: 900, y: 285, w: 240, rot: 4, z: 24 },
      { type: 'sticker', src: 'limit_gta6_60.png', x: 1400, y: 520, w: 140, rot: 8 },
      { type: 'sticker', src: 'puck_gta5.png', x: 1230, y: 630, w: 92, rot: -6 },
    ],
  },
  'hero-mobile': {
    w: 1080, h: 1350, bg: PLUM_TALL, color: '#f7f2f8', accent: PINK,
    brand: { x: 70, y: 70, size: 38 },
    head: { x: 64, y: 196, size: 200, max: 950, lines: ['Real roads.', 'Game map.'] },
    sub: { x: 70, width: 930, size: 38, text: 'Turn-by-turn navigation drawn like GTA V, Red Dead 2 and GTA VI, built for Android Auto.' },
    line: { width: 26, color: PINK, points: [[-40, 1300], [300, 1250], [600, 1296], [1120, 1222]] },
    items: [
      { type: 'car', src: 'car-gta5.webp', x: 120, y: 780, w: 900, rot: -4, z: 20 },
      { type: 'phone', src: 'phone-gta6-drive.webp', x: 40, y: 870, w: 210, rot: -5, z: 22 },
      { type: 'phone', src: 'phone-rdr2-drive.webp', x: 240, y: 830, w: 230, rot: 4, z: 24 },
      { type: 'sticker', src: 'limit_gta6_60.png', x: 870, y: 1140, w: 150, rot: 8 },
      { type: 'sticker', src: 'puck_gta5.png', x: 660, y: 1210, w: 100, rot: -6 },
    ],
  },
  phone: {
    w: 1600, h: 820, bg: PLUM, color: '#f7f2f8', accent: PINK,
    head: { x: 76, y: 190, size: 150, max: 560, lines: ['Your phone,', 'same worlds'] },
    sub: { x: 80, width: 520, size: 27, text: "Search, saved places, a route preview with live traffic, and settings drawn as each game's pause menu." },
    line: { width: 20, color: PINK, points: [[-40, 760], [400, 700], [800, 760], [1640, 690]] },
    items: [
      { type: 'phone', src: 'phone-gta5-map.webp', x: 700, y: 150, w: 270, rot: -6, z: 20 },
      { type: 'phone', src: 'phone-rdr2-settings.webp', x: 990, y: 110, w: 270, rot: 0, z: 24 },
      { type: 'phone', src: 'phone-gta6-drive.webp', x: 1280, y: 150, w: 270, rot: 6, z: 22 },
    ],
  },
  'phone-mobile': {
    w: 1080, h: 1280, bg: PLUM_TALL, color: '#f7f2f8', accent: PINK,
    head: { x: 64, y: 80, size: 180, max: 950, lines: ['Your phone,', 'same worlds'] },
    sub: { x: 70, y: 430, width: 930, size: 36, text: "Search, a route preview with live traffic, and settings as each game's pause menu." },
    line: { width: 26, color: PINK, points: [[-40, 1250], [400, 1200], [800, 1250], [1120, 1190]] },
    items: [
      { type: 'phone', src: 'phone-gta5-map.webp', x: 50, y: 600, w: 300, rot: -6, z: 20 },
      { type: 'phone', src: 'phone-rdr2-settings.webp', x: 390, y: 570, w: 300, rot: 0, z: 24 },
      { type: 'phone', src: 'phone-gta6-drive.webp', x: 730, y: 600, w: 300, rot: 6, z: 22 },
    ],
  },
  // The bezel the drive clip plays in: tools/readme_images.py lays each video frame into its screen.
  'clip-frame': {
    w: 960, h: 400, bg: '#0e0c12', grain: false, margin: 4,
    items: [{ type: 'car', src: null, x: 8.96, y: 16.96, w: 920, rot: 0, glare: false }],
  },
};

for (const [key, W] of Object.entries(WORLDS)) {
  window.LAYOUTS[`world-${key}`] = {
    w: 1600, h: 760, bg: W.bg, color: W.color, accent: W.accent,
    head: { x: 76, y: 170, size: 150, max: 530, lines: W.head },
    sub: { x: 80, width: 500, size: 27, text: W.sub },
    line: { width: 20, color: W.line, points: [[-40, 712], [400, 690], [800, 730], [1640, 650]] },
    items: [
      { type: 'car', src: W.car, x: 690, y: 70, w: 860, rot: -3, z: 20 },
      { type: 'phone', src: W.phone, x: 650, y: 250, w: 230, rot: -5, z: 24, glow: W.glow },
      ...W.extras(false),
    ],
  };
  window.LAYOUTS[`world-${key}-mobile`] = {
    w: 1080, h: 1220, bg: W.bg, color: W.color, accent: W.accent,
    head: { x: 64, y: 80, size: 180, max: 950, lines: W.head },
    sub: { x: 70, width: 930, size: 36, text: W.sub },
    line: { width: 26, color: W.line, points: [[-40, 1180], [400, 1140], [800, 1190], [1120, 1130]] },
    items: [
      { type: 'car', src: W.car, x: 120, y: 590, w: 900, rot: -3, z: 20 },
      { type: 'phone', src: W.phone, x: 40, y: 690, w: 240, rot: -5, z: 24, glow: W.glow },
      ...W.extras(true),
    ],
  };
}
