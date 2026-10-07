import { backgroundNeedsAttention, vendorSteps } from '../vendors';

describe('vendor power-saver steps', () => {
  it('matches makers case-insensitively and falls back', () => {
    expect(vendorSteps('OnePlus').name).toBe('OnePlus / OPPO / realme');
    expect(vendorSteps('poco').name).toBe('Xiaomi / Redmi / POCO');
    expect(vendorSteps('google').steps).toEqual([]);
    expect(vendorSteps('acme').name).toBe('Your phone maker');
    expect(vendorSteps(null).steps.length).toBe(1);
  });

  it('flags background problems for the settings dot', () => {
    expect(backgroundNeedsAttention(null)).toBe(false);
    expect(backgroundNeedsAttention({ nearby: 1, notifications: 1, batteryOptimized: 0 })).toBe(false);
    expect(backgroundNeedsAttention({ nearby: 1, notifications: 1, batteryOptimized: 1 })).toBe(true);
    expect(backgroundNeedsAttention({ nearby: 0, notifications: 1, batteryOptimized: 0 })).toBe(true);
  });
});
