import type { RideRow } from '@modules/vesc';

import { formatBytes, olderThan, usage } from '../usage';

const ride = (
  id: number,
  startWallMs: number,
  durationMs: number,
  bytes: number,
  state: RideRow['state'] = 'closed',
) => ({ id, startWallMs, durationMs, bytes, state }) as RideRow;

describe('storage usage', () => {
  const day = 86_400_000;
  const now = 100 * day;
  const rides = [
    ride(1, now - 40 * day, 3_600_000, 2_000_000),
    ride(2, now - day, 1_800_000, 1_000_000),
    ride(3, now - 50 * day, 0, 10, 'recording'),
  ];

  it('sums bytes and measures bytes per hour', () => {
    const u = usage(rides);
    expect(u.bytes).toBe(3_000_010);
    expect(u.hours).toBeCloseTo(1.5);
    expect(u.bytesPerHour).toBeCloseTo(2_000_006.7, 0);
    expect(usage([]).bytesPerHour).toBeNull();
  });

  it('picks old closed rides only', () => {
    expect(olderThan(rides, 30, now).map((r) => r.id)).toEqual([1]);
  });

  it('formats sizes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2_000_000)).toBe('1.9 MB');
    expect(formatBytes(4_096)).toBe('4 KB');
    expect(formatBytes(3 * 1024 ** 3)).toBe('3.00 GB');
  });
});
