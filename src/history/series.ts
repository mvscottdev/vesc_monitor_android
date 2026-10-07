// Chart geometry for a stored ride: a min/max band and a mean line per metric, as SVG
// path strings (Skia parses them). Pure, so it is tested without a canvas.
import type { RideSeries, SeriesBand } from '@modules/vesc';

import {
  distOf,
  distUnit,
  METRIC,
  speedOf,
  speedUnit,
  tempDeltaOf,
  tempOf,
  tempUnit,
  type Units,
} from '@/settings/units';

export type ChartKey = 'speed' | 'power' | 'voltage' | 'temps' | 'duty';

export type ChartSpec = {
  key: ChartKey;
  label: string;
  unit: string;
  /** Bands drawn on this chart, first is primary. */
  bands: (s: RideSeries) => SeriesBand[];
};

const EMPTY: SeriesBand = { mean: [], min: [], max: [] };

/** True when the ride stored any speed (older rides and untrusted ratios have none). */
export function hasSpeed(s: RideSeries): boolean {
  return (s.speedMps?.mean ?? []).some((x) => x != null);
}

export const CHARTS: ChartSpec[] = [
  { key: 'speed', label: 'Speed', unit: 'km/h', bands: (s) => [s.speedMps ?? EMPTY] },
  { key: 'power', label: 'Power', unit: 'kW', bands: (s) => [s.powerW] },
  { key: 'voltage', label: 'Voltage', unit: 'V', bands: (s) => [s.voltageV] },
  { key: 'temps', label: 'Temps', unit: '°C', bands: (s) => [s.tempMotorC, s.tempFetC] },
  { key: 'duty', label: 'Duty', unit: '%', bands: (s) => [s.duty] },
];

/** The charts with their units in the rider's display units (the paths stay in SI). */
export function chartsFor(u: Units): ChartSpec[] {
  return CHARTS.map((c) =>
    c.key === 'speed' ? { ...c, unit: speedUnit(u) } : c.key === 'temps' ? { ...c, unit: tempUnit(u) } : c,
  );
}

export type Range = { lo: number; hi: number };

/** Value range over the bands, padded; zero kept in view for power. */
export function rangeOf(bands: SeriesBand[], includeZero: boolean): Range | null {
  let lo = Infinity;
  let hi = -Infinity;
  for (const b of bands) {
    for (const x of b.min) if (x != null) lo = Math.min(lo, x);
    for (const x of b.max) if (x != null) hi = Math.max(hi, x);
  }
  if (!Number.isFinite(lo)) return null;
  if (includeZero) {
    lo = Math.min(lo, 0);
    hi = Math.max(hi, 0);
  }
  const pad = (hi - lo) * 0.05 || Math.abs(hi) * 0.05 || 1;
  return { lo: lo - pad, hi: hi + pad };
}

const f = (x: number) => x.toFixed(1);

/** Band (filled min..max) and line (mean) paths; gaps split the line and the band. */
export function bandPaths(
  b: SeriesBand,
  r: Range,
  w: number,
  h: number,
  xs?: number[],
): { band: string; line: string } {
  const n = b.mean.length;
  const x = (i: number) => (xs ? (xs[i] ?? 0) * w : n <= 1 ? w / 2 : (i / (n - 1)) * w);
  const y = (v: number) => h - ((v - r.lo) / (r.hi - r.lo)) * h;
  let line = '';
  let band = '';
  let i = 0;
  while (i < n) {
    if (b.mean[i] == null) {
      i++;
      continue;
    }
    let j = i;
    while (j < n && b.mean[j] != null) j++;
    // Segment i..j-1
    line += `M${f(x(i))} ${f(y(b.mean[i]!))}`;
    for (let k = i + 1; k < j; k++) line += `L${f(x(k))} ${f(y(b.mean[k]!))}`;
    band += `M${f(x(i))} ${f(y(b.max[i] ?? b.mean[i]!))}`;
    for (let k = i + 1; k < j; k++) band += `L${f(x(k))} ${f(y(b.max[k] ?? b.mean[k]!))}`;
    for (let k = j - 1; k >= i; k--) band += `L${f(x(k))} ${f(y(b.min[k] ?? b.mean[k]!))}`;
    band += 'Z';
    i = j;
  }
  return { band, line };
}

/**
 * Distance at each bucket's middle (m), integrated from the mean speed; null when the
 * ride stored no speed or barely moved (under 10 m), so the chart stays on time.
 */
export function distances(s: RideSeries): number[] | null {
  const v = s.speedMps?.mean;
  if (!v || v.length === 0) return null;
  const dt = s.bucketMs / 1000;
  const out: number[] = [];
  let sum = 0;
  for (const x of v) {
    const step = (x ?? 0) * dt;
    out.push(sum + step / 2);
    sum += step;
  }
  return sum < 10 ? null : out;
}

/** Horizontal position of each bucket as a fraction 0..1: by time, or by distance travelled. */
export function xFractions(s: RideSeries, d: number[] | null): number[] {
  const n = s.buckets;
  if (d && d.length === n) {
    const lo = d[0] ?? 0;
    const span = (d[n - 1] ?? 0) - lo;
    if (span > 0) return d.map((x) => (x - lo) / span);
  }
  return Array.from({ length: n }, (_, i) => (n <= 1 ? 0.5 : i / (n - 1)));
}

/** Bucket whose position is nearest a touch at [px] across a chart [w] wide. */
export function bucketAtX(px: number, w: number, xs: number[]): number {
  if (xs.length === 0 || w <= 0) return 0;
  const f = px / w;
  let best = 0;
  for (let i = 1; i < xs.length; i++) if (Math.abs(xs[i]! - f) < Math.abs(xs[best]! - f)) best = i;
  return best;
}

/** Bucket under a touch at [px] across a chart [w] wide. */
export function bucketAt(px: number, w: number, n: number): number {
  if (n <= 1 || w <= 0) return 0;
  return Math.max(0, Math.min(n - 1, Math.round((px / w) * (n - 1))));
}

/** Every main metric at one bucket, for the scrub readout. */
export function readout(s: RideSeries, i: number, u: Units = METRIC, dist?: number[] | null): string {
  const parts: string[] = [];
  const tMin = (i * s.bucketMs) / 60000;
  parts.push(`${Math.floor(tMin)}:${String(Math.floor((tMin % 1) * 60)).padStart(2, '0')}`);
  const at = dist?.[i];
  if (at != null) parts.push(`${distOf(at, u).toFixed(2)} ${distUnit(u)}`);
  const sp = s.speedMps?.mean[i];
  if (sp != null) parts.push(`${speedOf(sp, u).toFixed(0)} ${speedUnit(u)}`);
  const p = s.powerW.mean[i];
  if (p != null) parts.push(`${(p / 1000).toFixed(2)} kW`);
  const v = s.voltageV.mean[i];
  if (v != null) parts.push(`${v.toFixed(1)} V`);
  const m = s.tempMotorC.max[i];
  if (m != null) parts.push(`motor ${tempOf(m, u).toFixed(0)} ${tempUnit(u)}`);
  const t = s.tempFetC.max[i];
  if (t != null) parts.push(`FET ${tempOf(t, u).toFixed(0)} ${tempUnit(u)}`);
  const d = s.duty.max[i];
  if (d != null) parts.push(`duty ${(d * 100).toFixed(0)} %`);
  return parts.join(' · ');
}

/** Alert keys by stored index; mirrors the native list, append-only. */
export const ALERT_KEYS = [
  'fet_temp',
  'motor_temp',
  'fet_temp_rise',
  'motor_temp_rise',
  'low_battery',
  'battery_cut',
  'low_cell',
  'sag',
  'diverge',
  'duty',
  'fault',
  'link_stale',
  'slip',
];

const SEVERITY = ['', 'info', 'warning', 'critical'] as const;

export type AlertMark = { bucket: number; key: string; severity: 'info' | 'warning' | 'critical' };

/** Where an alert starts in the ride (a change of the stored code), warnings and critical only. */
export function alertMarks(s: RideSeries): AlertMark[] {
  const codes = s.alert?.max ?? [];
  const out: AlertMark[] = [];
  let prev = 0;
  codes.forEach((c, i) => {
    const code = c ?? 0;
    if (code !== prev && code > 0) {
      const severity = SEVERITY[code % 4];
      const key = ALERT_KEYS[Math.floor(code / 4)];
      if (key && (severity === 'warning' || severity === 'critical')) out.push({ bucket: i, key, severity });
    }
    prev = code;
  });
  return out;
}

/** Ride time of a bucket as m:ss. */
export function bucketTime(s: RideSeries, i: number): string {
  const sec = Math.floor((i * s.bucketMs) / 1000);
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;
}

/** What the ride list shows beside the summary, from a coarse series of the ride. */
export type RowStats = {
  /** Sparkline values (speed in m/s, or power in W for rides without speed). */
  spark: number[];
  sparkIsSpeed: boolean;
  distanceM: number | null;
  maxSpeedMps: number | null;
  alerts: number;
};

export function rowStats(s: RideSeries): RowStats {
  const speed = hasSpeed(s);
  const band = speed ? s.speedMps! : s.powerW;
  const spark = band.mean.map((x) => x ?? 0);
  const total = speed
    ? (s.speedMps!.mean.reduce<number>((a, x) => a + (x ?? 0), 0) * s.bucketMs) / 1000
    : null;
  let max: number | null = null;
  if (speed) for (const x of s.speedMps!.max) if (x != null) max = max == null ? x : Math.max(max, x);
  return {
    spark,
    sparkIsSpeed: speed,
    distanceM: total,
    maxSpeedMps: max,
    alerts: alertMarks(s).length,
  };
}

/** A sparkline as an SVG path across [w] × [h], scaled to its own range. */
export function sparkPath(values: number[], w: number, h: number): string {
  if (values.length < 2 || w <= 0 || h <= 0) return '';
  let lo = Infinity;
  let hi = -Infinity;
  for (const v of values) {
    lo = Math.min(lo, v);
    hi = Math.max(hi, v);
  }
  const span = hi - lo || 1;
  return values
    .map((v, i) => {
      const x = (i / (values.length - 1)) * w;
      const y = h - ((v - lo) / span) * h;
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join('');
}

/** "Today", "This week" or empty, for the ride list's second line. */
export function rideAge(startWallMs: number, nowMs: number): string {
  const start = new Date(nowMs);
  start.setHours(0, 0, 0, 0);
  if (startWallMs >= start.getTime()) return 'Today';
  if (nowMs - startWallMs < 7 * 86_400_000) return 'This week';
  return '';
}

/** Display value = a × stored value + b for a chart, in the rider's units. */
export function chartScale(key: ChartKey, u: Units): { a: number; b: number } {
  switch (key) {
    case 'speed':
      return { a: speedOf(1, u), b: 0 };
    case 'power':
      return { a: 0.001, b: 0 };
    case 'temps':
      return { a: tempDeltaOf(1, u), b: tempOf(0, u) };
    case 'duty':
      return { a: 100, b: 0 };
    default:
      return { a: 1, b: 0 };
  }
}

/** Round tick values covering [lo, hi], about [count] of them (1, 2 or 5 × 10ⁿ steps). */
export function niceTicks(lo: number, hi: number, count: number): number[] {
  if (!(hi > lo) || count < 1) return [];
  const raw = (hi - lo) / count;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? 10 * mag;
  const out: number[] = [];
  // Round to the step's decimals so labels never show float noise (0.6000000000000001).
  const decimals = Math.max(0, -Math.floor(Math.log10(step) + 1e-9));
  for (let v = Math.ceil(lo / step) * step; v <= hi + step * 1e-9; v += step) {
    out.push(+(Math.round(v / step) * step).toFixed(decimals) + 0);
  }
  return out;
}
