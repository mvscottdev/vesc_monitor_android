import type { SessionEvent } from '@modules/vesc';

import { setupSteps } from '../steps';

const base = {
  generation: 1,
  reason: null,
  mtu: 23,
  firmware: null,
  hardware: null,
  selective: true,
  depth: 1,
  forwardLocal: false,
  crcErrors: 0,
  unmatched: 0,
  notifications: 0,
  controllers: [],
  transport: 'ble',
  recording: null,
} satisfies Partial<SessionEvent>;

describe('setupSteps', () => {
  it('is empty when not connecting', () => {
    expect(setupSteps({ ...base, state: 'idle' })).toEqual([]);
  });

  it('marks the firmware done while looking for controllers', () => {
    const rows = setupSteps({
      ...base,
      state: 'connecting',
      setupStep: 'controllers',
      setupFound: 1,
      firmware: '6.05',
      hardware: '75_100',
    });
    expect(rows.map((r) => r.state)).toEqual(['done', 'done', 'now', 'todo']);
    expect(rows[1]?.label).toBe('Firmware 6.05');
    expect(rows[2]?.sub).toBe('CAN bus · 1 found');
  });

  it('is all done once live', () => {
    const rows = setupSteps({ ...base, state: 'connected' });
    expect(rows.every((r) => r.state === 'done')).toBe(true);
  });
});
