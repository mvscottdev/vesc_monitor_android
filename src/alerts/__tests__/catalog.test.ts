import { readFileSync } from 'fs';
import { join } from 'path';

import type { SessionEvent, TelemetryFrameV1 } from '@modules/vesc';

import {
  CATALOG,
  catalogCounts,
  EDIT,
  escalates,
  GROUPS,
  stepThreshold,
  triggerText,
  unavailableReason,
} from '../catalog';
import { alertText } from '../texts';

const golden = JSON.parse(
  readFileSync(join(__dirname, '../../../contract/telemetry-frame.v1.json'), 'utf8'),
) as TelemetryFrameV1;
const connected = { state: 'connected' } as SessionEvent;

describe('alert catalog', () => {
  it('every entry has a group and a banner text', () => {
    for (const e of CATALOG) {
      expect(GROUPS).toContain(e.group);
      expect(
        alertText({
          key: e.key,
          controllerId: null,
          severity: 'warning',
          value: 1,
          sinceMs: 0,
          notify: 0,
          cleared: 0,
        }).title,
      ).not.toBe('Alert');
    }
  });

  it('names missing inputs', () => {
    expect(unavailableReason('motor_temp', golden, connected)).toBeNull();
    const noSensor = { ...golden, vescs: golden.vescs.map((v) => ({ ...v, tempMotorC: null })) };
    expect(unavailableReason('motor_temp', noSensor, connected)).toBe('No temperature sensor on the motor');
    const single = { ...golden, vescs: golden.vescs.slice(0, 1) };
    expect(unavailableReason('diverge', single, connected)).toBe('Needs two or more controllers');
    expect(unavailableReason('motor_temp', noSensor, null)).toBeNull();
  });

  it('counts switched-on and unavailable alerts', () => {
    expect(catalogCounts(new Set(), golden, connected)).toEqual({ on: CATALOG.length, unavailable: 0 });
    const single = { ...golden, vescs: golden.vescs.slice(0, 1) };
    expect(catalogCounts(new Set(['sag']), single, connected)).toEqual({
      on: CATALOG.length - 3,
      unavailable: 2,
    });
  });

  it('shows thresholds in effect and steps them in display units', () => {
    const motor = CATALOG.find((e) => e.key === 'motor_temp')!;
    expect(triggerText(motor, [76, 85, 92.5])).toBe('at 76 / 85 / 92.5 °C');
    expect(triggerText(motor, undefined)).toBe(motor.trigger);
    const rise = CATALOG.find((e) => e.key === 'motor_temp_rise')!;
    expect(triggerText(rise, [0.3])).toBe('more than 18 °C per minute');
    expect(stepThreshold(0.9, EDIT.duty!, 1)).toBeCloseTo(0.91);
    expect(stepThreshold(1000, EDIT.link_stale!, -1)).toBeCloseTo(500);
    expect(stepThreshold(0.3, EDIT.motor_temp_rise!, 1)).toBeCloseTo(0.35);
    expect(escalates([20, 10], false)).toBe(true);
    expect(escalates([10, 20], false)).toBe(false);
    expect(escalates([85, 85], true)).toBe(false);
  });
});
