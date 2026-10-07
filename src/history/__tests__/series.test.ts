import type { RideSeries, SeriesBand } from '@modules/vesc';

import {
  ALERT_KEYS,
  alertMarks,
  bandPaths,
  bucketAt,
  bucketAtX,
  bucketTime,
  chartScale,
  distances,
  hasSpeed,
  niceTicks,
  rangeOf,
  readout,
  rideAge,
  rowStats,
  sparkPath,
  xFractions,
} from '../series';

const band = (mean: (number | null)[], spread = 1): SeriesBand => ({
  mean,
  min: mean.map((x) => (x == null ? null : x - spread)),
  max: mean.map((x) => (x == null ? null : x + spread)),
});

describe('ride series geometry', () => {
  it('pads the range and keeps zero for power', () => {
    expect(rangeOf([band([10, 20])], false)).toEqual({ lo: 8.4, hi: 21.6 });
    expect(rangeOf([band([10, 20])], true)?.lo).toBeLessThan(0);
    expect(rangeOf([band([null])], false)).toBeNull();
  });

  it('splits paths at gaps', () => {
    const { band: b, line } = bandPaths(band([0, 1, null, 2, 3]), { lo: -1, hi: 4 }, 100, 50);
    expect(line.match(/M/g)).toHaveLength(2);
    expect(b.match(/Z/g)).toHaveLength(2);
    expect(line.startsWith('M0.0 40.0L25.0 30.0')).toBe(true);
  });

  it('maps touches to buckets and reads every metric', () => {
    expect(bucketAt(50, 100, 5)).toBe(2);
    expect(bucketAt(-10, 100, 5)).toBe(0);
    expect(bucketAt(500, 100, 5)).toBe(4);
    const s: RideSeries = {
      startMs: 0,
      bucketMs: 30000,
      buckets: 3,
      powerW: band([100, 1500, 200]),
      voltageV: band([60, 58, 59]),
      tempFetC: band([30, 40, 50]),
      tempMotorC: band([null, null, null]),
      duty: band([0.1, 0.5, 0.2], 0),
      erpm: band([0, 0, 0]),
    };
    expect(readout(s, 1)).toBe('0:30 · 1.50 kW · 58.0 V · FET 41 °C · duty 50 %');
    expect(hasSpeed(s)).toBe(false);
    const withSpeed = { ...s, speedMps: band([5, 10, null], 0) };
    expect(hasSpeed(withSpeed)).toBe(true);
    expect(readout(withSpeed, 1)).toBe('0:30 · 36 km/h · 1.50 kW · 58.0 V · FET 41 °C · duty 50 %');
  });

  it('marks where warnings and critical alerts start', () => {
    const duty = ALERT_KEYS.indexOf('duty') * 4;
    const slip = ALERT_KEYS.indexOf('slip') * 4;
    const s = {
      startMs: 0,
      bucketMs: 30000,
      buckets: 6,
      alert: { mean: [], min: [], max: [0, duty + 2, duty + 2, duty + 3, slip + 1, 0] },
    } as unknown as RideSeries;
    expect(alertMarks(s)).toEqual([
      { bucket: 1, key: 'duty', severity: 'warning' },
      { bucket: 3, key: 'duty', severity: 'critical' },
    ]);
    expect(bucketTime(s, 3)).toBe('1:30');
    expect(alertMarks({ ...s, alert: undefined })).toEqual([]);
  });

  it('places buckets by distance travelled when asked', () => {
    const s: RideSeries = {
      startMs: 0,
      bucketMs: 10000,
      buckets: 4,
      powerW: band([1, 1, 1, 1]),
      voltageV: band([50, 50, 50, 50]),
      tempFetC: band([30, 30, 30, 30]),
      tempMotorC: band([30, 30, 30, 30]),
      duty: band([0, 0, 0, 0]),
      erpm: band([0, 0, 0, 0]),
      speedMps: band([0, 10, 10, null], 0),
    };
    // 0 m, then 100 m per 10 s bucket; values sit at each bucket's middle.
    expect(distances(s)).toEqual([0, 50, 150, 200]);
    expect(xFractions(s, distances(s))).toEqual([0, 0.25, 0.75, 1]);
    expect(xFractions(s, null)).toEqual([0, 1 / 3, 2 / 3, 1]);
    expect(bucketAtX(70, 100, [0, 0.25, 0.75, 1])).toBe(2);
    expect(readout(s, 2, undefined, distances(s))).toContain('0.15 km');
    expect(distances({ ...s, speedMps: band([0, 0.1, 0, 0], 0) })).toBeNull();
    const line = bandPaths(band([0, 1, 2, 3]), { lo: 0, hi: 3 }, 100, 30, [0, 0.25, 0.75, 1]).line;
    expect(line).toContain('L75.0 10.0');
  });

  it('summarises a ride for its list row', () => {
    const s: RideSeries = {
      startMs: 0,
      bucketMs: 10000,
      buckets: 3,
      powerW: band([100, 200, 300]),
      voltageV: band([50, 50, 50]),
      tempFetC: band([30, 30, 30]),
      tempMotorC: band([30, 30, 30]),
      duty: band([0, 0, 0]),
      erpm: band([0, 0, 0]),
      speedMps: band([5, 10, null], 1),
      alert: { mean: [0, 6, 6], min: [0, 6, 6], max: [0, 6, 6] },
    };
    expect(rowStats(s)).toEqual({
      spark: [5, 10, 0],
      sparkIsSpeed: true,
      distanceM: 150,
      maxSpeedMps: 11,
      alerts: 1,
    });
    const noSpeed = rowStats({ ...s, speedMps: undefined });
    expect(noSpeed.sparkIsSpeed).toBe(false);
    expect(noSpeed.distanceM).toBeNull();
    expect(sparkPath([0, 1, 0], 100, 10)).toBe('M0.0 10.0L50.0 0.0L100.0 10.0');
    expect(sparkPath([1], 100, 10)).toBe('');
    const now = new Date(2026, 9, 1, 12).getTime();
    expect(rideAge(now - 3_600_000, now)).toBe('Today');
    expect(rideAge(now - 3 * 86_400_000, now)).toBe('This week');
    expect(rideAge(now - 30 * 86_400_000, now)).toBe('');
  });

  it('scales charts to display units and picks round ticks', () => {
    expect(chartScale('speed', { speed: 'kmh', temp: 'c' }).a).toBeCloseTo(3.6);
    expect(chartScale('temps', { speed: 'kmh', temp: 'f' })).toEqual({ a: 1.8, b: 32 });
    expect(chartScale('power', { speed: 'kmh', temp: 'c' })).toEqual({ a: 0.001, b: 0 });
    expect(niceTicks(0, 60, 3)).toEqual([0, 20, 40, 60]);
    expect(niceTicks(41.3, 58.9, 4)).toEqual([45, 50, 55]);
    expect(niceTicks(0, 0.7, 6)).toEqual([0, 0.2, 0.4, 0.6]);
    expect(niceTicks(48.1, 48.2, 4)).toEqual([48.1, 48.15, 48.2]);
    expect(niceTicks(-0.4, 2.7, 3)).toEqual([0, 2]);
    expect(niceTicks(1, 1, 3)).toEqual([]);
  });
});
