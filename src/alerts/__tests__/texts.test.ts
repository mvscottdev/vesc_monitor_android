import type { ActiveAlert } from '@modules/vesc';

import { alertId, alertText, bannerAlert, WARNING_HIDE_MS } from '../texts';

const a = (over: Partial<ActiveAlert>): ActiveAlert => ({
  key: 'duty',
  controllerId: 17,
  severity: 'warning',
  value: 0.91,
  sinceMs: 1000,
  notify: 1,
  cleared: 0,
  ...over,
});

describe('alert texts', () => {
  it('names the controller and the value', () => {
    expect(alertText(a({}))).toEqual({
      title: 'Near maximum duty',
      detail: 'Controller 17 · 91 %: no headroom left for speed or torque',
    });
    expect(alertText(a({ key: 'fault', severity: 'critical', value: 6 })).detail).toBe(
      'Controller 17 · Motor over temperature',
    );
    expect(alertText(a({ key: 'fault', value: 99 })).detail).toContain('FAULT_99');
    expect(alertText(a({ key: 'low_battery', controllerId: null, value: 18 })).detail).toBe('18 % left');
  });

  it("uses the rider's controller name", () => {
    expect(alertText(a({}), undefined, { 17: 'Front' }).detail).toMatch(/^Front · 91 %/);
  });
});

describe('banner choice', () => {
  const none = new Set<string>();
  it('shows the most severe, never info, and hides warnings after 6 s', () => {
    const warn = a({});
    const crit = a({ key: 'fet_temp', severity: 'critical', value: 93 });
    const info = a({ key: 'slip', severity: 'info', controllerId: null });
    const seen = new Map([
      [alertId(warn), 0],
      [alertId(crit), 0],
    ]);
    expect(bannerAlert([warn, crit, info], none, seen, 100)).toBe(crit);
    expect(bannerAlert([warn, info], none, seen, 100)).toBe(warn);
    expect(bannerAlert([warn, info], none, seen, WARNING_HIDE_MS)).toBeNull();
    expect(bannerAlert([crit], new Set([alertId(crit)]), seen, 100)).toBeNull();
    expect(bannerAlert([crit], none, seen, 60_000)).toBe(crit);
  });
});
