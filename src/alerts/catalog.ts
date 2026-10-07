// The alert list as the rider sees it: grouped by what they care about, with the
// default trigger, the severities, and whether the connected hardware supports it.
import type { SessionEvent, TelemetryFrameV1 } from '@modules/vesc';

export type AlertGroup = 'Temperature' | 'Battery' | 'Power' | 'Faults' | 'Connection';

export type Sev = 'info' | 'warning' | 'critical';

export type CatalogEntry = {
  key: string;
  group: AlertGroup;
  title: string;
  trigger: string;
  /** Severities the alert can reach, lowest first. */
  severities: Sev[];
};

export const CATALOG: CatalogEntry[] = [
  {
    key: 'motor_temp',
    group: 'Temperature',
    title: 'Motor getting hot',
    trigger: 'at 76 / 85 / 92.5 °C (bldc defaults)',
    severities: ['info', 'warning', 'critical'],
  },
  {
    key: 'fet_temp',
    group: 'Temperature',
    title: 'Controller getting hot',
    trigger: 'FET at 76 / 85 / 92.5 °C (bldc defaults)',
    severities: ['info', 'warning', 'critical'],
  },
  {
    key: 'motor_temp_rise',
    group: 'Temperature',
    title: 'Motor temperature rising fast',
    trigger: 'more than 18 °C per minute',
    severities: ['warning'],
  },
  {
    key: 'fet_temp_rise',
    group: 'Temperature',
    title: 'Controller temperature rising fast',
    trigger: 'more than 42 °C per minute',
    severities: ['warning'],
  },
  {
    key: 'low_battery',
    group: 'Battery',
    title: 'Battery low',
    trigger: 'at 20 % / 10 %',
    severities: ['warning', 'critical'],
  },
  {
    key: 'battery_cut',
    group: 'Battery',
    title: 'Battery power cut soon',
    trigger: "1 V above the controller's cut voltage / halfway into it",
    severities: ['warning', 'critical'],
  },
  {
    key: 'low_cell',
    group: 'Battery',
    title: 'Low cell voltage',
    trigger: 'NMC 3.0 / 2.8 V, LFP 2.7 / 2.5 V per cell',
    severities: ['warning', 'critical'],
  },
  {
    key: 'sag',
    group: 'Battery',
    title: 'Heavy voltage sag',
    trigger: '20 % / 30 % below the resting voltage',
    severities: ['warning', 'critical'],
  },
  {
    key: 'diverge',
    group: 'Battery',
    title: 'Controller voltages differ',
    trigger: '1.5 V apart for 5 s',
    severities: ['warning'],
  },
  {
    key: 'duty',
    group: 'Power',
    title: 'Near maximum duty',
    trigger: '90 % / 94 % duty',
    severities: ['warning', 'critical'],
  },
  {
    key: 'slip',
    group: 'Power',
    title: 'Wheel slip',
    trigger: '3 slips within a minute',
    severities: ['info'],
  },
  {
    key: 'fault',
    group: 'Faults',
    title: 'Controller fault',
    trigger: 'any fault code, kept until dismissed',
    severities: ['warning', 'critical'],
  },
  {
    key: 'link_stale',
    group: 'Connection',
    title: 'Controller not responding',
    trigger: 'no data for 1 s / 3 s',
    severities: ['warning', 'critical'],
  },
];

export const GROUPS: AlertGroup[] = ['Temperature', 'Battery', 'Power', 'Faults', 'Connection'];

/**
 * Why an alert can't work with the connected vehicle, or null when it can (or nothing
 * is connected yet). Mirrors the native engine's input checks.
 */
export function unavailableReason(
  key: string,
  frame: TelemetryFrameV1 | null,
  session: SessionEvent | null,
): string | null {
  if (session?.state !== 'connected' || !frame) return null;
  const fresh = frame.vescs.filter((v) => v.fresh === 1);
  if (fresh.length === 0) return null;
  switch (key) {
    case 'motor_temp':
    case 'motor_temp_rise':
      return fresh.every((v) => v.tempMotorC == null) ? 'No temperature sensor on the motor' : null;
    case 'fet_temp':
    case 'fet_temp_rise':
      return fresh.every((v) => v.tempMosC == null) ? 'No controller temperature reading' : null;
    case 'low_battery':
    case 'low_cell':
      return frame.combined.cells == null ? 'Battery not detected yet' : null;
    case 'diverge':
      return frame.vescs.length < 2 ? 'Needs two or more controllers' : null;
    case 'slip':
      return frame.vescs.length < 2 ? 'Needs two or more controllers' : null;
    default:
      return null;
  }
}

/** Counts for the screen header: switched on, and unavailable on the connected vehicle. */
export function catalogCounts(
  disabled: Set<string>,
  frame: TelemetryFrameV1 | null,
  session: SessionEvent | null,
): { on: number; unavailable: number } {
  let on = 0;
  let unavailable = 0;
  for (const e of CATALOG) {
    if (unavailableReason(e.key, frame, session)) unavailable++;
    else if (!disabled.has(e.key)) on++;
  }
  return { on, unavailable };
}

/** How a rider-editable alert shows its thresholds: display = native value × scale. */
export type EditSpec = { prefix: string; unit: string; scale: number; step: number; digits: number };

export const EDIT: Record<string, EditSpec> = {
  motor_temp: { prefix: 'at', unit: '°C', scale: 1, step: 1, digits: 1 },
  fet_temp: { prefix: 'FET at', unit: '°C', scale: 1, step: 1, digits: 1 },
  motor_temp_rise: { prefix: 'more than', unit: '°C per minute', scale: 60, step: 3, digits: 0 },
  fet_temp_rise: { prefix: 'more than', unit: '°C per minute', scale: 60, step: 3, digits: 0 },
  low_battery: { prefix: 'at', unit: '%', scale: 1, step: 1, digits: 0 },
  sag: { prefix: '', unit: '% below the resting voltage', scale: 1, step: 1, digits: 0 },
  diverge: { prefix: '', unit: 'V apart for 5 s', scale: 1, step: 0.1, digits: 1 },
  duty: { prefix: 'at', unit: '% duty', scale: 100, step: 1, digits: 0 },
  link_stale: { prefix: 'no data for', unit: 's', scale: 0.001, step: 0.5, digits: 1 },
  slip: { prefix: '', unit: 'slips within a minute', scale: 1, step: 1, digits: 0 },
};

/** A display number without trailing zeros. */
export function displayValue(native: number, spec: EditSpec): string {
  return String(Number((native * spec.scale).toFixed(spec.digits)));
}

/** Trigger text from the thresholds in effect, e.g. "at 80 / 90 °C". */
export function triggerText(entry: CatalogEntry, thresholds: number[] | undefined): string {
  const spec = EDIT[entry.key];
  if (!spec || !thresholds || thresholds.length === 0) return entry.trigger;
  const values = thresholds.map((t) => displayValue(t, spec)).join(' / ');
  return [spec.prefix, values, spec.unit].filter(Boolean).join(' ');
}

/** One edit step on a threshold, in native units, rounded to the display precision. */
export function stepThreshold(native: number, spec: EditSpec, dir: 1 | -1): number {
  const shown = Number((native * spec.scale).toFixed(spec.digits)) + dir * spec.step;
  return Number(shown.toFixed(Math.max(spec.digits, 1))) / spec.scale;
}

/** True when the levels still escalate (rising alerts rise, falling alerts fall). */
export function escalates(thresholds: number[], rising: boolean): boolean {
  return thresholds.every((t, i) => i === 0 || (rising ? t > thresholds[i - 1]! : t < thresholds[i - 1]!));
}

/** Alerts that fire when the value falls (the rest fire when it rises). */
export const FALLING = new Set(['low_battery']);

/** Repeat-time steps and limit (seconds). */
export const REPEAT_STEP_S = 10;
export const REPEAT_MAX_S = 3600;

/** "Clears below 82 °C": where the first level stops, from the clear margin (hysteresis). */
export function clearText(first: number, margin: number, spec: EditSpec, rising: boolean): string {
  const at = rising ? first - margin : first + margin;
  return `Clears ${rising ? 'below' : 'above'} ${displayValue(at, spec)} ${spec.unit}`;
}

/** One step of the clear margin in native units; never below 0. */
export function stepMargin(native: number, spec: EditSpec, dir: 1 | -1): number {
  return Math.max(0, stepThreshold(native, spec, dir));
}

/** One step of the repeat time (seconds), within 0..REPEAT_MAX_S. */
export function stepRepeat(s: number, dir: 1 | -1): number {
  return Math.min(
    REPEAT_MAX_S,
    Math.max(0, Math.round(s / REPEAT_STEP_S) * REPEAT_STEP_S + dir * REPEAT_STEP_S),
  );
}
