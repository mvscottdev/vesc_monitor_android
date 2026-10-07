import type { RunState } from '@modules/vesc';

import {
  bestBracket,
  curvePaths,
  formatRunTime,
  isBest,
  liveElapsedMs,
  parseRun,
  phaseText,
  runSplits,
  type SavedRun,
  toggleCompare,
  headline,
  phaseHint,
  speedAt,
} from '../run';

const run = (over: Partial<RunState>): RunState => ({
  state: 'idle',
  abort: null,
  elapsedMs: null,
  brackets: [
    { label: '0-30', rolloutMs: 2100, firstMotionMs: 2600 },
    { label: '0-60', rolloutMs: null, firstMotionMs: null },
  ],
  peakSpeedMps: 0,
  peakPowerW: 0,
  minVoltageV: null,
  maxTempMosC: null,
  maxTempMotorC: null,
  slip: 0,
  startEstimated: 0,
  sampleRateHz: 0,
  jitterP95Ms: 0,
  k: null,
  ...over,
});

describe('speed test texts', () => {
  it('formats times', () => {
    expect(formatRunTime(3777)).toBe('3.78 s');
    expect(formatRunTime(null)).toBe('—');
  });

  it('names phases and aborts', () => {
    expect(phaseText(run({ state: 'staged' }))).toBe('Ready: go when you want');
    expect(phaseText(run({ state: 'aborted', abort: 'lift_off' }))).toContain('braking');
    expect(phaseText(null)).toBe('Arm, stop fully, then launch');
  });

  it('extrapolates the timer only while running', () => {
    expect(liveElapsedMs(run({ state: 'running', elapsedMs: 1000 }), 5000, 5250)).toBe(1250);
    expect(liveElapsedMs(run({ state: 'done', elapsedMs: 1000 }), 5000, 5250)).toBeNull();
  });

  it('headline is the last reached bracket', () => {
    expect(bestBracket(run({}))).toBe('0-30 2.10 s');
  });
});

describe('saved runs', () => {
  it('parses a stored record and lists its splits', () => {
    const json = JSON.stringify({
      ...run({ state: 'done' }),
      curve: [
        [0, 0],
        [50, 12],
      ],
    });
    const saved = parseRun({ id: 3, startWallMs: 1000, json });
    expect(saved?.id).toBe(3);
    expect(saved?.curve).toHaveLength(2);
    expect(runSplits(saved!)).toBe('0-30 2.10 s');
    expect(parseRun({ id: 4, startWallMs: 0, json: '{' })).toBeNull();
  });
});

describe('compare runs', () => {
  const saved = (id: number, t30: number | null, curve: [number, number][]): SavedRun => ({
    ...run({ state: 'done', brackets: [{ label: '0-30', rolloutMs: t30, firstMotionMs: t30 }] }),
    id,
    startWallMs: id,
    curve,
  });
  const a = saved(1, 2000, [
    [0, 0],
    [1000, 200],
    [2000, 300],
  ]);
  const b = saved(2, 2500, [
    [0, 0],
    [4000, 450],
  ]);
  const c = saved(3, null, []);

  it('marks the best time among runs', () => {
    expect(isBest(a, [a, b, c], '0-30')).toBe(true);
    expect(isBest(b, [a, b, c], '0-30')).toBe(false);
    expect(isBest(c, [a, b, c], '0-30')).toBe(false);
  });

  it('keeps at most four runs selected', () => {
    let sel: number[] = [];
    for (const id of [1, 2, 3, 4, 5]) sel = toggleCompare(sel, id);
    expect(sel).toEqual([2, 3, 4, 5]);
    expect(toggleCompare(sel, 3)).toEqual([2, 4, 5]);
  });

  it('draws curves on a shared scale', () => {
    const { paths, maxMs, maxKmh } = curvePaths([a, b], 400, 100);
    expect(maxMs).toBe(4000);
    expect(maxKmh).toBe(50);
    expect(paths[0]).toBe('M0.0 100.0L100.0 60.0L200.0 40.0');
    expect(paths[1]).toBe('M0.0 100.0L400.0 10.0');
  });

  it('reads a saved curve at any time', () => {
    const curve: [number, number][] = [
      [0, 0],
      [100, 20],
      [300, 60],
    ];
    expect(speedAt(curve, 50)).toBeCloseTo(1);
    expect(speedAt(curve, 200)).toBeCloseTo(4);
    expect(speedAt(curve, 300)).toBeCloseTo(6);
    expect(speedAt(curve, 301)).toBeNull();
    expect(speedAt([], 0)).toBeNull();
  });

  it('picks the headline bracket', () => {
    const b = (label: string, rolloutMs: number | null) => ({ label, rolloutMs, firstMotionMs: rolloutMs });
    const base = { state: 'done' } as RunState;
    const run = (brackets: ReturnType<typeof b>[]) => ({ ...base, brackets }) as RunState;
    expect(headline(run([b('0-30', 1710), b('0-50', 3480), b('0-60', 4920), b('100 m', 7000)]))?.label).toBe(
      '0-60',
    );
    expect(headline(run([b('0-30', 1710), b('0-50', null), b('0-60', null), b('100 m', null)]))?.label).toBe(
      '0-30',
    );
    expect(headline(run([b('0-20 mph', 1500), b('0-30 mph', 2800), b('0-40 mph', 4500)]))?.label).toBe(
      '0-40 mph',
    );
    expect(headline(run([b('0-30', null)]))).toBeNull();
    expect(phaseHint(null)).toMatch(/Arm/);
  });
});

describe('phase words and hints', () => {
  it('covers every phase', () => {
    expect(phaseText(run({ state: 'armed' }))).toBe('Armed: stand still');
    expect(phaseText(run({ state: 'staged' }))).toBe('Ready: go when you want');
    expect(phaseText(run({ state: 'running' }))).toBe('Running');
    expect(phaseText(run({ state: 'done' }))).toBe('Done');
    expect(phaseText(run({ state: 'aborted', abort: null }))).toBe('Aborted');
    for (const state of ['idle', 'armed', 'staged', 'running', 'aborted'] as const) {
      expect(phaseHint(run({ state }))).not.toBe('');
    }
  });
});
