// Storage use from the ride list: totals, measured bytes per hour of riding, and the
// rides older than a cutoff. Pure, so it is tested without the database.
import type { RideRow } from '@modules/vesc';

export type Usage = { rides: number; bytes: number; hours: number; bytesPerHour: number | null };

export function usage(rides: RideRow[]): Usage {
  const bytes = rides.reduce((a, r) => a + r.bytes, 0);
  const ms = rides.reduce((a, r) => a + r.durationMs, 0);
  const hours = ms / 3_600_000;
  return { rides: rides.length, bytes, hours, bytesPerHour: hours >= 0.05 ? bytes / hours : null };
}

/** Closed rides that started before [nowMs] minus [days]; the open ride is never included. */
export function olderThan(rides: RideRow[], days: number, nowMs: number): RideRow[] {
  const cutoff = nowMs - days * 86_400_000;
  return rides.filter((r) => r.state !== 'recording' && r.startWallMs < cutoff);
}

export function formatBytes(b: number): string {
  if (b < 1024) return `${b} B`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(0)} KB`;
  if (b < 1024 ** 3) return `${(b / 1024 / 1024).toFixed(1)} MB`;
  return `${(b / 1024 ** 3).toFixed(2)} GB`;
}

/** [olderThan] at the current wall time. */
export function olderThanNow(rides: RideRow[], days: number): RideRow[] {
  return olderThan(rides, days, Date.now());
}

/** The wall clock, kept out of components (render must stay pure). */
export function wallNow(): number {
  return Date.now();
}
