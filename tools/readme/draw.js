// README images for Overworld Maps. The devices, the route line and the headline style come from the
// Play Store Graphics Kit (Work\Playbooks\Play Store Graphics Kit\kit.js), so the README, the store listing
// and the website look like one thing. layouts.js holds the pictures; tools/readme_images.py renders them.
(() => {
  const A = 'assets/';

  // Galaxy S26 Ultra front (the proportions of Promo Videos' src/shared/Galaxy.tsx).
  function phone(src, x, y, w, rot = 0, z = 20, glow = null) {
    const h = w * 163.6 / 78.1, R = w * .088, ring = w * .0085, sx = w * .024, sy = w * .026;
    const sw = w - 2 * sx, sh = h - 2 * sy, sr = w * .064, hole = w * .05;
    const key = (top, len) => `<div style="position:absolute;left:${w - w * .004}px;top:${h * top}px;width:${w * .011}px;height:${h * len}px;border-radius:${w * .006}px;background:linear-gradient(90deg,#2a2b30,#5d6067 55%,#24252a)"></div>`;
    return `<div class="device" style="position:absolute;left:${x}px;top:${y}px;width:${w}px;height:${h}px;transform:rotate(${rot}deg);z-index:${z}">
      ${glow ? `<div style="position:absolute;inset:${-w * .1}px;border-radius:${R * 2.4}px;background:${glow};filter:blur(${w * .12}px);opacity:.55"></div>` : ''}
      ${key(.189, .102)}${key(.368, .082)}
      <div style="position:absolute;inset:0;border-radius:${R}px;background:linear-gradient(140deg,#8d9097 0%,#3b3d43 14%,#1f2024 46%,#2f3136 78%,#74777e 100%);
        box-shadow:inset 0 0 0 1.6px rgba(255,255,255,.22),0 ${w * .07}px ${w * .16}px rgba(0,0,0,.55),0 ${w * .015}px ${w * .03}px rgba(0,0,0,.5)"></div>
      <div style="position:absolute;inset:${ring}px;border-radius:${R - ring}px;background:linear-gradient(160deg,#0d0d10,#030304 40%,#050506)"></div>
      <div style="position:absolute;left:${sx}px;top:${sy}px;width:${sw}px;height:${sh}px;border-radius:${sr}px;overflow:hidden;background:#000">
        <img src="${A}${src}" style="width:100%;height:100%;object-fit:cover;object-position:top"></div>
      <div style="position:absolute;left:${w / 2 - hole / 2}px;top:${w * .081 - hole / 2}px;width:${hole}px;height:${hole}px;border-radius:50%;
        background:radial-gradient(circle at 42% 38%,#34405a 0,#10141d 16%,#020203 46%,#000 70%);box-shadow:0 0 0 ${w * .0035}px #000"></div>
      <div style="position:absolute;inset:${ring}px;border-radius:${R - ring}px;background:linear-gradient(118deg,rgba(255,255,255,.11),rgba(255,255,255,.035) 22%,rgba(255,255,255,0) 38%);mix-blend-mode:screen"></div>
    </div>`;
  }

  // The car's wide screen (the Curvv's 1920 x 720): black glass in a thin bezel with a visible rim.
  function car(src, x, y, w, rot = 0, z = 20, glare = true) {
    const aspect = 1920 / 720, h = w / aspect, b = w * .012;
    return `<div class="device" style="position:absolute;left:${x}px;top:${y}px;width:${w + 2 * b}px;height:${h + 2 * b}px;transform:rotate(${rot}deg);z-index:${z};
        border-radius:${w * .03}px;background:linear-gradient(150deg,#3a3b40,#0b0b0d 30%,#151518 70%,#45464c);
        box-shadow:inset 0 0 0 1.5px rgba(255,255,255,.2),0 0 0 1px rgba(0,0,0,.9),0 ${w * .04}px ${w * .08}px rgba(0,0,0,.6)">
      <div style="position:absolute;left:${b}px;top:${b}px;width:${w}px;height:${h}px;border-radius:${w * .022}px;overflow:hidden;background:#000">
        ${src ? `<img src="${A}${src}" style="width:100%;height:100%;display:block">` : ''}</div>
      ${glare ? `<div style="position:absolute;left:${b}px;top:${b}px;width:${w}px;height:${h}px;border-radius:${w * .022}px;
        background:linear-gradient(115deg,rgba(255,255,255,.10),rgba(255,255,255,0) 35%)"></div>` : ''}
    </div>`;
  }

  // One smooth line through points: the route that runs through every Overworld picture.
  function line(L, W, H) {
    const p = L.points;
    let d = `M${p[0][0]},${p[0][1]}`;
    for (let i = 0; i < p.length - 1; i++) {
      const p0 = p[i - 1] || p[i], p1 = p[i], p2 = p[i + 1], p3 = p[i + 2] || p2;
      d += ` C${[p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6]},${[p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6]},${p2}`;
    }
    return `<svg width="${W}" height="${H}" style="position:absolute;left:0;top:0;z-index:10">
      <path d="${d}" fill="none" stroke="rgba(0,0,0,.35)" stroke-width="${L.width * 1.7}" stroke-linecap="round"/>
      <path d="${d}" fill="none" stroke="${L.color}" stroke-width="${L.width}" stroke-linecap="round"/></svg>`;
  }

  function item(it) {
    const { x = 0, y = 0, rot = 0 } = it;
    switch (it.type) {
      case 'phone': return phone(it.src, x, y, it.w, rot, it.z || 20, it.glow);
      case 'car': return car(it.src, x, y, it.w, rot, it.z || 20, it.glare !== false);
      case 'sticker': case 'pop':
        return `<img class="${it.type}" src="${A}${it.src}" style="left:${x}px;top:${y}px;width:${it.w}px;transform:rotate(${rot}deg);border-radius:${it.radius || 0}px;z-index:${it.z || 35}">`;
    }
    return '';
  }

  const id = new URLSearchParams(location.search).get('id');
  const L = window.LAYOUTS[id];
  const root = document.getElementById('c');
  root.style.width = L.w + 'px'; root.style.height = L.h + 'px';
  const out = [`<div class="bg" style="background:${L.bg}"></div>`];
  if (L.grain !== false) out.push('<div class="grain"></div>');
  if (L.line) out.push(line(L.line, L.w, L.h));
  if (L.brand) {
    const b = L.brand;
    out.push(`<div class="brand" style="left:${b.x}px;top:${b.y}px;gap:${b.size * .55}px;font-size:${b.size}px;color:${L.color}">
      <img src="${A}icon.png" style="width:${b.size * 2.1}px;height:${b.size * 2.1}px;border-radius:${b.size * .5}px">Overworld Maps</div>`);
  }
  if (L.head) {
    const h = L.head, last = h.lines.length - 1;
    const text = h.lines.map((t, i) => i === last ? `<span style="color:${L.accent}">${t}</span>` : t).join('<br>');
    out.push(`<h1 class="head" style="left:${h.x}px;top:${h.y}px;font-size:${h.size}px;color:${L.color};${h.center ? `width:${L.w}px;left:0;text-align:center;` : ''}">${text}</h1>`);
  }
  if (L.sub) {
    const s = L.sub;
    out.push(`<p class="sub" style="left:${s.x}px;top:${s.y}px;width:${s.width}px;font-size:${s.size}px;color:${L.color}">${s.text}</p>`);
  }
  (L.items || []).forEach((it) => out.push(item(it)));
  root.innerHTML = out.join('');

  // A headline never runs into the devices: it shrinks until it fits its column (head.max), and the
  // sentence sits under it wherever it ends.
  const h1 = root.querySelector('.head'), p = root.querySelector('.sub');
  if (h1 && L.head.max) {
    let size = L.head.size;
    while (h1.offsetWidth > L.head.max && size > 40) h1.style.fontSize = (size -= 2) + 'px';
  }
  if (h1 && p && L.sub.y == null) p.style.top = (L.head.y + h1.offsetHeight + (L.sub.gap || 28)) + 'px';
})();
