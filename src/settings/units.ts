// Display units. Everything below the UI stays SI (m/s, m, °C); these helpers convert only
// for display. Worklet-safe so dashboard mappers can use them on the UI thread.
export type Units = { speed: 'kmh' | 'mph'; temp: 'c' | 'f' };

export const METRIC: Units = { speed: 'kmh', temp: 'c' };

export const UNITS_KEY = 'ui.units';

const M_PER_MILE = 1609.344;

/** m/s → display speed. */
export function speedOf(mps: number, u: Units): number {
  'worklet';
  return u.speed === 'mph' ? (mps * 3600) / M_PER_MILE : mps * 3.6;
}

export function speedUnit(u: Units): string {
  'worklet';
  return u.speed === 'mph' ? 'mph' : 'km/h';
}

/** Metres → display distance (km or mi); follows the speed unit. */
export function distOf(m: number, u: Units): number {
  'worklet';
  return u.speed === 'mph' ? m / M_PER_MILE : m / 1000;
}

export function distUnit(u: Units): string {
  'worklet';
  return u.speed === 'mph' ? 'mi' : 'km';
}

/** Wh/km → Wh per display distance unit. */
export function perDistOf(whPerKm: number, u: Units): number {
  'worklet';
  return u.speed === 'mph' ? (whPerKm * M_PER_MILE) / 1000 : whPerKm;
}

/** °C → display temperature. */
export function tempOf(c: number, u: Units): number {
  'worklet';
  return u.temp === 'f' ? c * 1.8 + 32 : c;
}

/** A temperature difference (or rate) in °C → display units. */
export function tempDeltaOf(c: number, u: Units): number {
  'worklet';
  return u.temp === 'f' ? c * 1.8 : c;
}

export function tempUnit(u: Units): string {
  'worklet';
  return u.temp === 'f' ? '°F' : '°C';
}

/** A stored value, or metric when it is missing or unreadable. */
export function parseUnits(json: string | null): Units {
  if (!json) return METRIC;
  try {
    const raw = JSON.parse(json) as Partial<Units>;
    return {
      speed: raw?.speed === 'mph' ? 'mph' : 'kmh',
      temp: raw?.temp === 'f' ? 'f' : 'c',
    };
  } catch {
    return METRIC;
  }
}
