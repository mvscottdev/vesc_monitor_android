// Mockup kit behaviour: draws RadialGauge, Sparkline and Chart from data-* attributes
// (the attributes mirror the future component props) and scales frames to narrow viewers.
// Sample data is deterministic so screenshots are stable.
(function () {
  var NS = 'http://www.w3.org/2000/svg';
  var shot = /[?&]shot=1/.test(location.search);
  if (shot) document.documentElement.classList.add('shot');

  function rng(seed) { var s = seed >>> 0; return function () { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
  function smooth(a, k) { var o = a.slice(); for (var p = 0; p < k; p++) for (var i = 1; i < o.length - 1; i++) o[i] = (o[i - 1] + o[i] * 2 + o[i + 1]) / 4; return o; }
  function el(tag, attrs, parent) { var e = document.createElementNS(NS, tag); for (var k in attrs) e.setAttribute(k, attrs[k]); if (parent) parent.appendChild(e); return e; }
  function cssVar(name) { return 'var(--' + name + ')'; }
  function colorOf(c) {
    var map = { speed: 'metric-speed', drive: 'metric-drive', regen: 'metric-regen', voltage: 'metric-voltage', volt: 'metric-voltage',
      battery: 'metric-battery', temp: 'metric-temp', duty: 'metric-duty', gps: 'metric-gps', accent: 'accent', ok: 'state-ok',
      warn: 'state-warn', crit: 'state-crit', muted: 'text-tertiary', secondary: 'text-secondary' };
    return cssVar(map[c] || c);
  }

  // ---- Sample series (values in real units) ----
  var N = 240;
  var gens = {
    'ride-speed': function () {
      var r = rng(7), v = [], cur = 0, target = 0;
      for (var i = 0; i < N; i++) {
        if (i % 22 === 0) target = r() < 0.18 ? 0 : 18 + r() * 30;
        cur += (target - cur) * 0.18 + (r() - 0.5) * 2.2; v.push(Math.max(0, cur));
      }
      return smooth(v, 2);
    },
    'ride-power': function () {
      var s = gens['ride-speed'](), r = rng(11), v = [];
      for (var i = 0; i < N; i++) { var a = (s[Math.min(N - 1, i + 1)] - s[Math.max(0, i - 1)]); v.push(Math.max(-900, s[i] * 28 + a * 260 + (r() - 0.5) * 180)); }
      return smooth(v, 1);
    },
    'ride-volt': function () {
      var p = gens['ride-power'](), v = [];
      for (var i = 0; i < N; i++) v.push(66.2 - i / N * 5.4 - Math.max(0, p[i]) * 0.0021 + Math.max(0, -p[i]) * 0.0012);
      return v;
    },
    'ride-temp-motor': function () { var r = rng(3), v = []; for (var i = 0; i < N; i++) v.push(24 + 58 * (1 - Math.exp(-i / 110)) + (r() - 0.5) * 1.2); return smooth(v, 3); },
    'ride-temp-fet': function () { var r = rng(5), v = []; for (var i = 0; i < N; i++) v.push(23 + 30 * (1 - Math.exp(-i / 80)) + (r() - 0.5) * 1); return smooth(v, 3); },
    'run-a': function () { var v = []; for (var i = 0; i < N; i++) { var t = i / N * 8; v.push(Math.min(64, 71 * (1 - Math.exp(-t / 2.35)))); } return v; },
    'run-b': function () { var v = []; for (var i = 0; i < N; i++) { var t = i / N * 8; v.push(Math.min(63, 70 * (1 - Math.exp(-t / 2.6)))); } return v; },
    'run-c': function () { var v = []; for (var i = 0; i < N; i++) { var t = i / N * 8; v.push(Math.min(61, 68 * (1 - Math.exp(-t / 2.95)))); } return v; },
    'run-power': function () { var v = []; for (var i = 0; i < N; i++) { var t = i / N * 8; v.push(t < 0.05 ? 0 : Math.min(3150, 3400 * (1 - Math.exp(-t / 0.35))) * Math.exp(-Math.max(0, t - 3.4) / 3)); } return v; },
    'run-volt': function () { var p = gens['run-power'](), v = []; for (var i = 0; i < N; i++) v.push(64.8 - p[i] * 0.0021); return v; },
    'live-volt': function () { var r = rng(21), v = []; for (var i = 0; i < 60; i++) v.push(62.6 - (i > 38 && i < 50 ? 2.6 : 0) + (r() - 0.5) * 0.5); return smooth(v, 2); },
    'live-power': function () { var r = rng(22), v = []; for (var i = 0; i < 60; i++) v.push(900 + 900 * Math.sin(i / 9) + (r() - 0.5) * 300); return smooth(v, 2); },
    'live-speed': function () { var r = rng(23), v = []; for (var i = 0; i < 60; i++) v.push(30 + 8 * Math.sin(i / 14) + (r() - 0.5) * 2); return smooth(v, 2); },
    'live-temp': function () { var v = []; for (var i = 0; i < 60; i++) v.push(80 + i * 0.23); return v; },
    'mini-1': function () { var r = rng(31), v = []; for (var i = 0; i < 40; i++) v.push(20 + r() * 25); return smooth(v, 3); },
    'mini-2': function () { var r = rng(32), v = []; for (var i = 0; i < 40; i++) v.push(10 + r() * 35); return smooth(v, 3); },
    'mini-3': function () { var r = rng(33), v = []; for (var i = 0; i < 40; i++) v.push(25 + r() * 15); return smooth(v, 3); },
    'mini-4': function () { var r = rng(34), v = []; for (var i = 0; i < 40; i++) v.push(15 + r() * 30); return smooth(v, 3); },
  };
  function series(spec) { return spec.indexOf(',') >= 0 ? spec.split(',').map(Number) : gens[spec](); }

  function pathOf(vals, x0, x1, y0, y1, lo, hi, frac) {
    var n = Math.max(2, Math.round(vals.length * (frac == null ? 1 : frac))), d = '';
    for (var i = 0; i < n; i++) {
      var x = x0 + (x1 - x0) * i / (vals.length - 1);
      var y = y1 - (y1 - y0) * (Math.min(hi, Math.max(lo, vals[i])) - lo) / (hi - lo);
      d += (i ? 'L' : 'M') + x.toFixed(1) + ' ' + y.toFixed(1);
    }
    return d;
  }

  // ---- RadialGauge: data-value data-min data-max data-warn data-peak data-ticks data-color data-sweep ----
  function gauge(svg) {
    var W = +svg.getAttribute('width'), H = +svg.getAttribute('height');
    var min = +(svg.dataset.min || 0), max = +svg.dataset.max, val = +svg.dataset.value;
    var sweep = +(svg.dataset.sweep || 240), sw = +(svg.dataset.stroke || 12);
    var cx = W / 2, cy = H / 2 + (sweep < 260 ? H * 0.06 : 0), R = Math.min(W, H) / 2 - sw - 4;
    var a0 = 90 + (360 - sweep) / 2;
    function pt(v, r) { var a = (a0 + sweep * (v - min) / (max - min)) * Math.PI / 180; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; }
    function arc(v0, v1, r) { var p0 = pt(v0, r), p1 = pt(v1, r), large = sweep * (v1 - v0) / (max - min) > 180 ? 1 : 0; return 'M' + p0[0].toFixed(1) + ' ' + p0[1].toFixed(1) + 'A' + r + ' ' + r + ' 0 ' + large + ' 1 ' + p1[0].toFixed(1) + ' ' + p1[1].toFixed(1); }
    svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H);
    el('path', { d: arc(min, max, R), stroke: 'color-mix(in srgb, var(--text-primary) 9%, transparent)', 'stroke-width': sw, fill: 'none', 'stroke-linecap': 'round' }, svg);
    if (svg.dataset.warn) el('path', { d: arc(+svg.dataset.warn, max, R), stroke: 'color-mix(in srgb, var(--state-warn) 30%, transparent)', 'stroke-width': sw, fill: 'none', 'stroke-linecap': 'round' }, svg);
    var g = el('path', { d: arc(min, Math.max(min + (max - min) * 0.005, val), R), stroke: colorOf(svg.dataset.color || 'accent'), 'stroke-width': sw, fill: 'none', 'stroke-linecap': 'round', class: 'gauge-val' }, svg);
    if (svg.dataset.glow) g.setAttribute('style', 'filter: drop-shadow(0 0 10px color-mix(in srgb, ' + colorOf(svg.dataset.color || 'accent') + ' 45%, transparent))');
    var ticks = (svg.dataset.ticks || '').split(',').filter(Boolean).map(Number);
    var step = +(svg.dataset.minor || 0);
    if (step) for (var v = min; v <= max + 1e-9; v += step) {
      var p0 = pt(v, R - sw / 2 - 6), p1 = pt(v, R - sw / 2 - (ticks.indexOf(v) >= 0 ? 13 : 9));
      el('line', { x1: p0[0], y1: p0[1], x2: p1[0], y2: p1[1], stroke: 'var(--text-tertiary)', 'stroke-width': ticks.indexOf(v) >= 0 ? 2 : 1 }, svg);
    }
    ticks.forEach(function (t) {
      var p = pt(t, R - sw / 2 - 25);
      var tx = el('text', { x: p[0], y: p[1] + 4, 'text-anchor': 'middle', fill: 'var(--text-tertiary)', style: 'font: 500 12px var(--font-display); font-variant-numeric: tabular-nums' }, svg);
      tx.textContent = t;
    });
    if (svg.dataset.peak) { var q0 = pt(+svg.dataset.peak, R - sw / 2 - 2), q1 = pt(+svg.dataset.peak, R + sw / 2 + 2); el('line', { x1: q0[0], y1: q0[1], x2: q1[0], y2: q1[1], stroke: 'var(--text-primary)', 'stroke-width': 3, 'stroke-linecap': 'round', opacity: 0.8 }, svg); }
  }

  // ---- Sparkline: data-series data-color data-min data-max data-area ----
  function spark(svg) {
    var W = +svg.getAttribute('width') || 200, H = +svg.getAttribute('height') || 40;
    var vals = series(svg.dataset.series), lo = svg.dataset.min != null ? +svg.dataset.min : Math.min.apply(null, vals), hi = svg.dataset.max != null ? +svg.dataset.max : Math.max.apply(null, vals);
    var col = colorOf(svg.dataset.color || 'accent');
    svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H); svg.setAttribute('preserveAspectRatio', 'none');
    var d = pathOf(vals, 0, W - 4, 2, H - 2, lo, hi);
    if (svg.dataset.area != null) {
      var id = 'g' + Math.random().toString(36).slice(2, 8), defs = el('defs', {}, svg), lg = el('linearGradient', { id: id, x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
      el('stop', { offset: '0', 'stop-color': col, 'stop-opacity': 0.28 }, lg); el('stop', { offset: '1', 'stop-color': col, 'stop-opacity': 0 }, lg);
      el('path', { d: d + 'L' + (W - 4) + ' ' + H + 'L0 ' + H + 'Z', fill: 'url(#' + id + ')', stroke: 'none' }, svg);
    }
    el('path', { d: d, fill: 'none', stroke: col, 'stroke-width': 2, 'vector-effect': 'non-scaling-stroke', 'stroke-linejoin': 'round' }, svg);
    var last = vals[vals.length - 1], ly = H - 2 - (H - 4) * (last - lo) / (hi - lo);
    el('circle', { cx: W - 4, cy: ly, r: 3, fill: col }, svg);
  }

  // ---- Chart: data-series="color:gen[:band][:dash][:frac=0.6];…" data-ymin data-ymax data-yticks data-xlabels data-scrub data-hlines ----
  function chart(svg) {
    var W = +svg.getAttribute('width'), H = +svg.getAttribute('height');
    var L = 34, Rr = 8, T = 8, B = 20;
    var lo = +svg.dataset.ymin, hi = +svg.dataset.ymax;
    svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H);
    var yt = (svg.dataset.yticks || '').split(',').filter(Boolean).map(Number);
    yt.forEach(function (v) {
      var y = H - B - (H - B - T) * (v - lo) / (hi - lo);
      el('line', { x1: L, x2: W - Rr, y1: y, y2: y, stroke: 'var(--divider)', 'stroke-width': 1 }, svg);
      var t = el('text', { x: L - 6, y: y + 3.5, 'text-anchor': 'end' }, svg); t.textContent = v;
    });
    var xl = (svg.dataset.xlabels || '').split(',').filter(Boolean);
    xl.forEach(function (s, i) {
      var x = L + (W - L - Rr) * i / (xl.length - 1);
      var t = el('text', { x: x, y: H - 5, 'text-anchor': i === 0 ? 'start' : i === xl.length - 1 ? 'end' : 'middle' }, svg); t.textContent = s;
    });
    (svg.dataset.hlines || '').split(';').filter(Boolean).forEach(function (h) {
      var p = h.split(':'), v = +p[0], y = H - B - (H - B - T) * (v - lo) / (hi - lo);
      el('line', { x1: L, x2: W - Rr, y1: y, y2: y, stroke: colorOf(p[1] || 'warn'), 'stroke-width': 1, 'stroke-dasharray': '4 4', opacity: 0.8 }, svg);
      if (p[2]) { var t = el('text', { x: W - Rr - 2, y: y - 4, 'text-anchor': 'end', style: 'fill:' + colorOf(p[1] || 'warn') }, svg); t.textContent = p[2]; }
    });
    (svg.dataset.series || '').split(';').filter(Boolean).forEach(function (s) {
      var p = s.split(':'), col = colorOf(p[0]), vals = series(p[1]), band = p.indexOf('band') > 1, dash = p.indexOf('dash') > 1;
      var fr = null; p.forEach(function (x) { if (x.indexOf('frac=') === 0) fr = +x.slice(5); });
      if (band) {
        var r = rng(vals.length + p[1].length), up = [], dn = [];
        for (var i = 0; i < vals.length; i++) { var e = (hi - lo) * (0.025 + r() * 0.04); up.push(vals[i] + e); dn.push(vals[i] - e); }
        var du = pathOf(up, L, W - Rr, T, H - B, lo, hi), dd = pathOf(dn.slice().reverse(), W - Rr, L, T, H - B, lo, hi);
        el('path', { d: du + dd.replace('M', 'L') + 'Z', fill: col, 'fill-opacity': 0.14, stroke: 'none' }, svg);
      }
      el('path', { d: pathOf(vals, L, W - Rr, T, H - B, lo, hi, fr), fill: 'none', stroke: col, 'stroke-width': 2, 'stroke-linejoin': 'round', 'stroke-dasharray': dash ? '5 4' : 'none', opacity: dash ? 0.8 : 1 }, svg);
      if (fr) { var i2 = Math.round(vals.length * fr) - 1, x = L + (W - L - Rr) * i2 / (vals.length - 1), y = H - B - (H - B - T) * (vals[i2] - lo) / (hi - lo); el('circle', { cx: x, cy: y, r: 4, fill: col }, svg); }
    });
    if (svg.dataset.scrub) {
      var x = L + (W - L - Rr) * +svg.dataset.scrub;
      el('line', { x1: x, x2: x, y1: T, y2: H - B, stroke: 'var(--text-primary)', 'stroke-width': 1, opacity: 0.5 }, svg);
    }
  }

  document.querySelectorAll('svg.gauge').forEach(gauge);
  document.querySelectorAll('svg.spark').forEach(spark);
  document.querySelectorAll('svg.chart').forEach(chart);

  // ---- Fit frames to narrow viewers (gallery only) ----
  function fit() {
    document.querySelectorAll('.frame-wrap').forEach(function (w) {
      var f = w.querySelector('.frame'); if (!f) return;
      var s = shot ? 1 : Math.min(1, w.clientWidth / f.offsetWidth);
      f.style.transform = s < 1 ? 'scale(' + s + ')' : '';
      w.style.height = (f.offsetHeight * s) + 'px';
    });
  }
  fit(); window.addEventListener('resize', fit);
})();
