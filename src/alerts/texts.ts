// Plain-language alert texts, keyed by the native alert key. The background
// notification uses the same titles (native side).
import type { ActiveAlert } from '@modules/vesc';

import type { Names } from '@/lib/names';
import { METRIC, tempDeltaOf, tempOf, tempUnit, type Units } from '@/settings/units';

export type AlertText = { title: string; detail: string };

function who(a: ActiveAlert, n: Names): string {
  if (a.controllerId == null) return '';
  return `${n[a.controllerId] ?? `Controller ${a.controllerId}`} · `;
}

/** [n]: the rider's controller names, used instead of "Controller <id>". */
export function alertText(a: ActiveAlert, u: Units = METRIC, n: Names = {}): AlertText {
  const crit = a.severity === 'critical';
  switch (a.key) {
    case 'fet_temp':
      return {
        title: crit ? 'Controller power limited' : 'Controller getting hot',
        detail: `${who(a, n)}FET at ${tempOf(a.value, u).toFixed(0)} ${tempUnit(u)}`,
      };
    case 'motor_temp':
      return {
        title: crit ? 'Motor power limited' : 'Motor getting hot',
        detail: `${who(a, n)}motor at ${tempOf(a.value, u).toFixed(0)} ${tempUnit(u)}`,
      };
    case 'fet_temp_rise':
    case 'motor_temp_rise':
      return {
        title: 'Temperature rising fast',
        detail: `${who(a, n)}${a.key === 'fet_temp_rise' ? 'FET' : 'motor'} +${tempDeltaOf(a.value * 60, u).toFixed(0)} ${tempUnit(u)}/min`,
      };
    case 'low_battery':
      return {
        title: crit ? 'Battery almost empty' : 'Battery low',
        detail: `${a.value.toFixed(0)} % left`,
      };
    case 'battery_cut':
      return {
        title: crit ? 'Battery power cut' : 'Battery power cut soon',
        detail: `${a.value.toFixed(1)} V, the controller limits power below its cut voltage`,
      };
    case 'low_cell':
      return { title: 'Low cell voltage', detail: `${a.value.toFixed(2)} V per cell under load` };
    case 'sag':
      return { title: 'Heavy voltage sag', detail: `${a.value.toFixed(0)} % below the resting voltage` };
    case 'diverge':
      return {
        title: 'Controller voltages differ',
        detail: `${a.value.toFixed(1)} V apart: check wiring, fuse and connectors`,
      };
    case 'duty':
      return {
        title: crit ? 'At maximum duty' : 'Near maximum duty',
        detail: `${who(a, n)}${(a.value * 100).toFixed(0)} %: no headroom left for speed or torque`,
      };
    case 'fault':
      return {
        title: a.cleared === 1 ? 'Controller fault (cleared)' : 'Controller fault',
        detail: `${who(a, n)}${faultName(a.value)}`,
      };
    case 'link_stale':
      return {
        title: 'Controller not responding',
        detail: `${who(a, n)}no data for ${(a.value / 1000).toFixed(0)} s`,
      };
    case 'slip':
      return { title: 'Wheel slip', detail: `${a.value.toFixed(0)} slips in the last minute` };
    default:
      return { title: 'Alert', detail: a.key };
  }
}

const FAULTS: Record<number, string> = {
  1: 'Over voltage',
  2: 'Under voltage',
  3: 'Gate driver (DRV)',
  4: 'Over current',
  5: 'FET over temperature',
  6: 'Motor over temperature',
  7: 'Gate driver over voltage',
  8: 'Gate driver under voltage',
  9: 'MCU under voltage',
  10: 'Rebooted by the watchdog',
  14: 'Flash corruption',
  18: 'Unbalanced currents',
  19: 'Brake (BRK)',
};

export function faultName(code: number): string {
  return FAULTS[code] ?? `FAULT_${code}`;
}

/** Banner identity: a new firing of the same alert is a new banner. */
export function alertId(a: ActiveAlert): string {
  return `${a.key}:${a.controllerId ?? '-'}:${a.sinceMs}`;
}

export const WARNING_HIDE_MS = 6_000;

/**
 * Which alert the banner shows: the most severe warning or critical alert not dismissed;
 * warnings hide after 6 s on screen. `firstSeen` maps alert ids to when the UI first saw them.
 */
export function bannerAlert(
  alerts: ActiveAlert[],
  dismissed: ReadonlySet<string>,
  firstSeen: ReadonlyMap<string, number>,
  now: number,
): ActiveAlert | null {
  const rank = { critical: 2, warning: 1, info: 0 } as const;
  let best: ActiveAlert | null = null;
  for (const a of alerts) {
    if (a.severity === 'info' || dismissed.has(alertId(a))) continue;
    if (a.severity === 'warning' && now - (firstSeen.get(alertId(a)) ?? now) >= WARNING_HIDE_MS) continue;
    if (best == null || rank[a.severity] > rank[best.severity]) best = a;
  }
  return best;
}
