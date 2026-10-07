// Widget models: what each widget shows, computed from one telemetry frame.
// Pure worklet functions (run on the UI thread inside one mapper per widget) and
// Jest-tested on the JS side. Units are SI from the frame; display units follow the rider's setting.
import type { TelemetryFrameV1, VescFrameEntry } from '@modules/vesc';

import type { WidgetSource, WidgetType } from './layout';
import {
  distOf,
  distUnit,
  METRIC,
  perDistOf,
  speedOf,
  speedUnit,
  tempOf,
  tempUnit,
  type Units,
} from '@/settings/units';

export type Zone = 'none' | 'ok' | 'warn' | 'crit';

export type WidgetModel = {
  value: string;
  unit: string;
  /** Small line under or beside the value. */
  sub: string;
  zone: Zone;
  /** Bar fill 0..1; for power, signed: negative = regen. */
  fraction: number;
  /** Second value (temperature: MOSFET; battery, range, sag: the right-hand foot or header text). */
  value2: string;
  zone2: Zone;
  fraction2: number;
  /** Short source label (speed gauge chip, e.g. "VESC"). */
  label: string;
  /** Peak marks as fractions: power bar drive ≥ 0 and regen ≤ 0, speed gauge session max. */
  peak: number;
  peak2: number;
  stale: boolean;
  /** Age tag text for stale values, e.g. "4 s". */
  age: string;
};

export type CardModel = {
  motorA: string;
  batteryA: string;
  /** Value and unit together, e.g. "0.89 kW". */
  power: string;
  duty: string;
  temps: string;
  speed: string;
  fault: string;
  faulted: boolean;
  stale: boolean;
  age: string;
};

export const NO_VALUE = '––';

/** Temperature zones: warn from the derating start − 10 °C, crit from the derating start (bldc default 85 °C). */
export const TEMP_DERATING_START_C = 85;
export const TEMP_WARN_MARGIN_C = 10;
/** Duty zones from the design tokens. */
export const DUTY_WARN = 0.85;
export const DUTY_CRIT = 0.95;
/** Speed gauge scale: the session max rounded up to the next step, at least the minimum (km/h). */
export const SPEED_SCALE_MIN_KMH = 60;
export const SPEED_SCALE_STEP_KMH = 20;
/** Battery bar zones (display SoC %). */
export const SOC_WARN_PCT = 20;
export const SOC_CRIT_PCT = 10;

export const MPS_TO_KMH = 3.6;

/** Power bar spans grow with the session peaks but never below these (W). */
export const POWER_BAR_MIN_DRIVE_W = 1000;
export const POWER_BAR_MIN_REGEN_W = 500;

type Values = {
  voltageV: number | null;
  currentInA: number | null;
  currentMotorA: number | null;
  powerW: number | null;
  duty: number | null;
  tempMosC: number | null;
  tempMotorC: number | null;
  fresh: boolean;
  ageMs: number | null;
  partial: string;
};

export function fmt(v: number | null | undefined, decimals: number): string {
  'worklet';
  return v == null || !Number.isFinite(v) ? NO_VALUE : v.toFixed(decimals);
}

/** Watts as "850 W" or "1.84 kW". */
export function fmtPower(w: number | null | undefined): { value: string; unit: string } {
  'worklet';
  if (w == null || !Number.isFinite(w)) return { value: NO_VALUE, unit: 'kW' };
  return Math.abs(w) >= 1000
    ? { value: (w / 1000).toFixed(2), unit: 'kW' }
    : { value: w.toFixed(0), unit: 'W' };
}

export function tempZone(c: number | null | undefined): Zone {
  'worklet';
  if (c == null) return 'none';
  if (c >= TEMP_DERATING_START_C) return 'crit';
  if (c >= TEMP_DERATING_START_C - TEMP_WARN_MARGIN_C) return 'warn';
  return 'ok';
}

export function dutyZone(d: number | null | undefined): Zone {
  'worklet';
  if (d == null) return 'none';
  const a = Math.abs(d);
  return a >= DUTY_CRIT ? 'crit' : a >= DUTY_WARN ? 'warn' : 'ok';
}

/** The same scale in miles per hour. */
export const SPEED_SCALE_MIN_MPH = 40;
export const SPEED_SCALE_STEP_MPH = 10;

export function speedScaleKmh(maxKmh: number): number {
  'worklet';
  return Math.max(SPEED_SCALE_MIN_KMH, Math.ceil(maxKmh / SPEED_SCALE_STEP_KMH) * SPEED_SCALE_STEP_KMH);
}

export function speedScaleMph(maxMph: number): number {
  'worklet';
  return Math.max(SPEED_SCALE_MIN_MPH, Math.ceil(maxMph / SPEED_SCALE_STEP_MPH) * SPEED_SCALE_STEP_MPH);
}

export function socZone(pct: number | null | undefined): Zone {
  'worklet';
  if (pct == null) return 'none';
  return pct < SOC_CRIT_PCT ? 'crit' : pct < SOC_WARN_PCT ? 'warn' : 'none';
}

const CHEMISTRY = ['', 'NMC', 'LFP'];
const CONFIDENCE = ['', 'low', 'medium', 'high'];

function ageText(ageMs: number | null): string {
  'worklet';
  return ageMs == null ? '' : `${Math.max(1, Math.round(ageMs / 1000))} s`;
}

function clamp01(x: number): number {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

function entryValues(e: VescFrameEntry | undefined): Values {
  'worklet';
  return {
    voltageV: e?.voltageV ?? null,
    currentInA: e?.currentInA ?? null,
    currentMotorA: e?.currentMotorA ?? null,
    powerW: e?.powerW ?? null,
    duty: e?.duty ?? null,
    tempMosC: e?.tempMosC ?? null,
    tempMotorC: e?.tempMotorC ?? null,
    fresh: e?.fresh === 1,
    ageMs: e?.ageMs ?? null,
    partial: '',
  };
}

/** The values a widget reads: combined over fresh controllers, or one controller. */
export function sourceValues(frame: TelemetryFrameV1 | null, source: WidgetSource): Values {
  'worklet';
  if (!frame) return entryValues(undefined);
  if (source !== 'combined') {
    let found: VescFrameEntry | undefined;
    for (const e of frame.vescs) if (e.controllerId === source.controllerId) found = e;
    return entryValues(found);
  }
  const c = frame.combined;
  let youngest: number | null = null;
  for (const e of frame.vescs) {
    const a = e.ageMs ?? null;
    if (a != null && (youngest == null || a < youngest)) youngest = a;
  }
  return {
    voltageV: c.voltageV,
    currentInA: c.currentInA ?? null,
    currentMotorA: c.currentMotorA ?? null,
    powerW: c.powerW ?? null,
    duty: c.duty ?? null,
    tempMosC: c.tempMosC ?? null,
    tempMotorC: c.tempMotorC ?? null,
    fresh: c.fresh > 0,
    ageMs: youngest,
    partial: c.partial === 1 && c.fresh > 0 ? `${c.fresh}/${c.total}` : '',
  };
}

const EMPTY: WidgetModel = {
  value: NO_VALUE,
  unit: '',
  sub: '',
  zone: 'none',
  fraction: 0,
  value2: NO_VALUE,
  zone2: 'none',
  fraction2: 0,
  label: '',
  peak: 0,
  peak2: 0,
  stale: false,
  age: '',
};

/** Model of one widget. A stale source keeps its last value, dimmed, with an age tag. */
export function widgetModel(
  frame: TelemetryFrameV1 | null,
  type: WidgetType,
  source: WidgetSource,
  u: Units = METRIC,
): WidgetModel {
  'worklet';
  const v = sourceValues(frame, source);
  const known = frame != null && v.ageMs != null;
  const base = { ...EMPTY, stale: known && !v.fresh, age: known && !v.fresh ? ageText(v.ageMs) : '' };
  switch (type) {
    case 'voltage':
      return { ...base, value: fmt(v.voltageV, 1), unit: 'V', sub: v.partial };
    case 'current':
      return {
        ...base,
        value: fmt(v.currentInA, 1),
        unit: 'A',
        sub: v.currentMotorA == null ? '' : `motor ${v.currentMotorA.toFixed(1)} A`,
      };
    case 'duty':
      return {
        ...base,
        value: v.duty == null ? NO_VALUE : Math.abs(v.duty * 100).toFixed(0),
        unit: '%',
        zone: dutyZone(v.duty),
        fraction: v.duty == null ? 0 : clamp01(Math.abs(v.duty)),
      };
    case 'power': {
      const p = fmtPower(v.powerW);
      const drive = Math.max(POWER_BAR_MIN_DRIVE_W, frame?.combined.peakPowerW ?? 0);
      const regen = Math.max(POWER_BAR_MIN_REGEN_W, -(frame?.combined.peakRegenW ?? 0));
      const w = v.powerW ?? 0;
      return {
        ...base,
        value: p.value,
        unit: p.unit,
        sub: v.currentInA == null ? '' : `${v.currentInA.toFixed(1)} A`,
        fraction: w >= 0 ? clamp01(w / drive) : -clamp01(-w / regen),
        // Peaks are session-wide, so they only mark the combined power bar.
        peak: source === 'combined' ? clamp01((frame?.combined.peakPowerW ?? 0) / drive) : 0,
        peak2: source === 'combined' ? -clamp01(-(frame?.combined.peakRegenW ?? 0) / regen) : 0,
      };
    }
    case 'temperature':
      return {
        ...base,
        value: v.tempMotorC == null ? NO_VALUE : tempOf(v.tempMotorC, u).toFixed(0),
        unit: tempUnit(u),
        zone: tempZone(v.tempMotorC),
        fraction: v.tempMotorC == null ? 0 : clamp01(v.tempMotorC / TEMP_DERATING_START_C),
        value2: v.tempMosC == null ? NO_VALUE : tempOf(v.tempMosC, u).toFixed(0),
        zone2: tempZone(v.tempMosC),
        fraction2: v.tempMosC == null ? 0 : clamp01(v.tempMosC / TEMP_DERATING_START_C),
      };
    case 'speed': {
      // Never show a speed we can't trust: unknown or stale speed is dashes, not an old number.
      const c = frame?.combined;
      const mps = source === 'combined' ? c?.speedMps : null;
      const live = c != null && c.fresh > 0 && mps != null;
      const kmh = live ? speedOf(Math.abs(mps), u) : null;
      const maxKmh = speedOf(c?.maxSpeedMps ?? 0, u);
      const scale = u.speed === 'mph' ? speedScaleMph(maxKmh) : speedScaleKmh(maxKmh);
      const hint =
        c?.speedWheelMissing === 1
          ? 'Set wheel size in VESC Tool'
          : c?.speedSource === 1 || maxKmh > 0
            ? ''
            : 'Speed shows once the wheel turns';
      return {
        ...base,
        value: kmh == null ? NO_VALUE : kmh.toFixed(0),
        unit: speedUnit(u),
        sub: hint !== '' ? hint : maxKmh > 0 ? `max ${maxKmh.toFixed(0)}` : '',
        label: c?.speedSource === 1 ? 'VESC' : 'No speed source',
        zone: c?.slip === 1 ? 'warn' : 'none',
        fraction: kmh == null ? 0 : clamp01(kmh / scale),
        peak: clamp01(maxKmh / scale),
        value2: `${scale}`,
        stale: false,
        age: '',
      };
    }
    case 'battery': {
      const c = frame?.combined;
      const soc = c?.socPct ?? null;
      const conf = c?.socConfidence ?? 0;
      const approx = conf !== 3 || c?.chemistry === 2;
      const cells = c?.cells ?? null;
      const what = cells == null ? 'Detecting pack' : `${cells}S ${CHEMISTRY[c?.chemistry ?? 0] ?? ''}`;
      const note = cells != null && conf !== 3 ? ` · ${CONFIDENCE[conf] ?? ''}` : '';
      return {
        ...base,
        value: soc == null ? NO_VALUE : `${approx ? '~' : ''}${soc.toFixed(0)}`,
        unit: '%',
        zone: socZone(soc),
        fraction: soc == null ? 0 : clamp01(soc / 100),
        sub: `${what}${note}${c?.batteryConfirm === 1 ? ' · confirm' : ''}`,
        value2: `${fmt(v.voltageV, 1)} V · ${fmt(c?.cellV, 2)} V/cell`,
      };
    }
    case 'range': {
      const c = frame?.combined;
      const tripKm = distOf(c?.tripM ?? 0, u);
      const km = c?.rangeKm == null ? null : distOf(c.rangeKm * 1000, u);
      const d = distUnit(u);
      return {
        ...base,
        // Range needs the learned capacity; until then it says so instead of guessing.
        value: km == null ? NO_VALUE : km >= 100 ? km.toFixed(0) : km.toFixed(1),
        unit: km == null ? `${d} · learning` : d,
        sub: `${c?.whPerKm == null ? NO_VALUE : perDistOf(c.whPerKm, u).toFixed(1)} Wh/${d}`,
        value2: `${tripKm.toFixed(1)} ${d} trip`,
      };
    }
    case 'sag': {
      const c = frame?.combined;
      const sag = c?.sagV ?? null;
      return {
        ...base,
        value: fmt(sag, 1),
        unit: 'V',
        sub: sag == null ? 'Waiting for a rest reading' : '',
        value2: c?.minVoltageV == null ? '' : `min ${c.minVoltageV.toFixed(1)} V`,
      };
    }
    case 'controller':
      return base;
  }
}

/** Model of a controller card. */
export function cardModel(
  frame: TelemetryFrameV1 | null,
  controllerId: number,
  u: Units = METRIC,
): CardModel {
  'worklet';
  let e: VescFrameEntry | undefined;
  if (frame) for (const x of frame.vescs) if (x.controllerId === controllerId) e = x;
  const v = entryValues(e);
  const p = fmtPower(v.powerW);
  const fault = e?.faultCode ?? 0;
  const known = e != null && v.ageMs != null;
  return {
    motorA: fmt(v.currentMotorA, 1),
    batteryA: fmt(v.currentInA, 1),
    power: `${p.value} ${p.unit}`,
    duty: v.duty == null ? NO_VALUE : Math.abs(v.duty * 100).toFixed(0),
    temps: `${v.tempMotorC == null ? NO_VALUE : tempOf(v.tempMotorC, u).toFixed(0)} · ${
      v.tempMosC == null ? NO_VALUE : tempOf(v.tempMosC, u).toFixed(0)
    }`,
    speed: v.fresh && e?.speedMps != null ? speedOf(Math.abs(e.speedMps), u).toFixed(0) : NO_VALUE,
    fault: fault === 0 ? 'No fault' : `Fault ${fault}`,
    faulted: fault !== 0,
    stale: known && !v.fresh,
    age: known && !v.fresh ? ageText(v.ageMs) : '',
  };
}
