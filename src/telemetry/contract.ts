import type { TelemetryFrameV1 } from '@modules/vesc';

const isNum = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v);
const isFlag = (v: unknown): boolean => v === 0 || v === 1;
const isNumOrNull = (v: unknown): boolean => v === null || isNum(v);
const isOptNum = (v: unknown): boolean => v === undefined || isNumOrNull(v);
const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null;

const COMBINED_OPTIONAL = [
  'currentInA',
  'currentMotorA',
  'powerW',
  'duty',
  'tempMosC',
  'tempMotorC',
  'peakPowerW',
  'peakRegenW',
  'faultCode',
  'faultControllerId',
  'speedMps',
  'speedSource',
  'speedWheelMissing',
  'slip',
  'maxSpeedMps',
  'tripM',
  'whUsed',
  'whPerKm',
  'socPct',
  'cellV',
  'cells',
  'chemistry',
  'socConfidence',
  'batteryConfirm',
  'sagV',
  'minVoltageV',
  'capacityAh',
  'rangeKm',
];
const VESC_OPTIONAL = [
  'ageMs',
  'tempMosC',
  'tempMotorC',
  'currentMotorA',
  'currentInA',
  'powerW',
  'duty',
  'erpm',
  'faultCode',
  'speedMps',
];

/** Checks a value against telemetry frame contract v1 (extra fields are allowed). */
export function isTelemetryFrameV1(v: unknown): v is TelemetryFrameV1 {
  if (!isObj(v) || v.v !== 1) return false;
  if (![v.seq, v.tMs, v.generation].every(isNum)) return false;
  const c = v.combined;
  if (!isObj(c) || !isNumOrNull(c.voltageV) || !isNum(c.fresh) || !isNum(c.total)) return false;
  if (!isFlag(c.partial) || !isFlag(c.divergent)) return false;
  if (!COMBINED_OPTIONAL.every((k) => isOptNum(c[k]))) return false;
  if (!Array.isArray(v.vescs)) return false;
  return v.vescs.every(
    (e: unknown) =>
      isObj(e) &&
      isNum(e.controllerId) &&
      isFlag(e.local) &&
      isNumOrNull(e.voltageV) &&
      isFlag(e.fresh) &&
      VESC_OPTIONAL.every((k) => isOptNum(e[k])),
  );
}
