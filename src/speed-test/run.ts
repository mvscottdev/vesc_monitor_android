// Speed-test texts: run times, phase labels and abort reasons (pure, tested).
import type { RunAbort, RunRecord, RunState } from '@modules/vesc';

export function formatRunTime(ms: number | null | undefined): string {
  return ms == null ? '—' : `${(ms / 1000).toFixed(2)} s`;
}

const ABORT: Record<RunAbort, string> = {
  false_start: 'False start: stopped again just after launching',
  rolling_start: 'Rolling start: launch from a full stop',
  lift_off: 'Throttle released or braking before the brackets were done',
  link: 'A controller stopped answering during the run',
  fault: 'A controller reported a fault',
  timeout: 'Run took too long',
  arm_timeout: 'No launch within 2 minutes',
  cancelled: 'Cancelled',
};

/** Headline line for the current phase. */
export function phaseText(run: RunState | null | undefined): string {
  switch (run?.state) {
    case 'armed':
      return 'Armed: stand still';
    case 'staged':
      return 'Ready: go when you want';
    case 'running':
      return 'Running';
    case 'done':
      return 'Done';
    case 'aborted':
      return run.abort ? ABORT[run.abort] : 'Aborted';
    default:
      return 'Arm, stop fully, then launch';
  }
}

/** Live timer value: the last event's elapsed time extrapolated to now while running. */
export function liveElapsedMs(
  run: RunState | null | undefined,
  receivedAt: number,
  now: number,
): number | null {
  if (run?.state !== 'running' || run.elapsedMs == null) return null;
  return run.elapsedMs + Math.max(0, now - receivedAt);
}

/** The result's headline time: the last reached speed bracket. */
export function bestBracket(run: RunState | null | undefined): string | null {
  const reached = run?.brackets.filter((b) => b.rolloutMs != null) ?? [];
  const last = reached[reached.length - 1];
  return last ? `${last.label} ${formatRunTime(last.rolloutMs)}` : null;
}

/** A saved run: the report plus its curve as [ms from the last standstill, km/h × 10]. */
export type SavedRun = RunState & { id: number; startWallMs: number; curve: [number, number][] };

/** Parses a stored run; null when the record is unreadable. */
export function parseRun(r: RunRecord): SavedRun | null {
  try {
    const v = JSON.parse(r.json) as Partial<SavedRun>;
    if (!Array.isArray(v.brackets)) return null;
    return { ...(v as SavedRun), id: r.id, startWallMs: r.startWallMs, curve: v.curve ?? [] };
  } catch {
    return null;
  }
}

/** One line per bracket reached, e.g. "0-30 2.10 s · 0-50 4.02 s". */
export function runSplits(run: RunState): string {
  const reached = run.brackets.filter((b) => b.rolloutMs != null);
  return reached.length === 0
    ? 'No bracket reached'
    : reached.map((b) => `${b.label} ${formatRunTime(b.rolloutMs)}`).join(' · ');
}

/** Up to this many runs overlay in the compare chart. */
export const COMPARE_MAX = 4;

/** Toggles a run in the compare selection, dropping the oldest pick beyond the limit. */
export function toggleCompare(selected: number[], id: number): number[] {
  if (selected.includes(id)) return selected.filter((x) => x !== id);
  return [...selected, id].slice(-COMPARE_MAX);
}

/** True when [run] has the fastest time for [label] among [runs] (ties count). */
export function isBest(run: SavedRun, runs: SavedRun[], label: string): boolean {
  const t = (r: SavedRun) => r.brackets.find((b) => b.label === label)?.rolloutMs ?? null;
  const mine = t(run);
  if (mine == null) return false;
  return runs.every((r) => {
    const o = t(r);
    return o == null || o >= mine;
  });
}

/** Speed curves as SVG paths over a shared time and speed scale. */
export function curvePaths(
  runs: SavedRun[],
  w: number,
  h: number,
): { paths: string[]; maxMs: number; maxKmh: number } {
  let maxMs = 0;
  let maxKmh = 0;
  for (const r of runs) {
    for (const [ms, k10] of r.curve) {
      maxMs = Math.max(maxMs, ms);
      maxKmh = Math.max(maxKmh, k10 / 10);
    }
  }
  maxKmh = Math.max(10, Math.ceil(maxKmh / 10) * 10);
  const paths = runs.map((r) =>
    r.curve
      .map(([ms, k10], i) => {
        const x = maxMs > 0 ? (ms / maxMs) * w : 0;
        const y = h - (k10 / 10 / maxKmh) * h;
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
      })
      .join(''),
  );
  return { paths, maxMs, maxKmh };
}

/** Speed (km/h) of a saved curve at [ms], linearly interpolated; null outside the curve. */
export function speedAt(curve: [number, number][], ms: number): number | null {
  const first = curve[0];
  const last = curve[curve.length - 1];
  if (!first || !last || ms < first[0] || ms > last[0]) return null;
  for (let i = 1; i < curve.length; i++) {
    const [t1, v1] = curve[i]!;
    const [t0, v0] = curve[i - 1]!;
    if (ms <= t1) return t1 === t0 ? v1 / 10 : (v0 + ((v1 - v0) * (ms - t0)) / (t1 - t0)) / 10;
  }
  return last[1] / 10;
}

/**
 * The result's headline bracket: the third speed bracket (0-60 km/h or 0-40 mph) when
 * reached, else the last bracket reached; null when nothing was reached.
 */
export function headline(run: RunState | null | undefined): RunState['brackets'][number] | null {
  const speed = run?.brackets.filter((b) => !/ m$/.test(b.label)) ?? [];
  const main = speed[2] ?? speed[speed.length - 1];
  if (main?.rolloutMs != null) return main;
  const reached = run?.brackets.filter((b) => b.rolloutMs != null) ?? [];
  return reached[reached.length - 1] ?? null;
}

/** One line under the phase while armed or running. */
export function phaseHint(run: RunState | null | undefined): string {
  switch (run?.state) {
    case 'armed':
      return 'Stand still. Ready shows once the wheel has stopped.';
    case 'staged':
      return 'Go when you like. The timer starts as the wheel turns.';
    case 'running':
      return 'Release the throttle or brake to end the run.';
    case 'aborted':
      return 'Arm again to retry.';
    default:
      return 'Tap Arm, stand still until Ready, then launch from a full stop.';
  }
}
