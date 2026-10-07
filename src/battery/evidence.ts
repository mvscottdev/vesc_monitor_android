// Plain-language evidence for the battery confirm screen. The detector runs natively;
// this only explains its inputs against the chosen pack.
import type { BatteryInfo, PackCandidate } from '@modules/vesc';

type Cell = { name: string; short: string; empty: number; restMin: number; restMax: number; full: number };

/** Per-cell voltages by chemistry code; mirrors the native OCV tables. */
export const CHEMISTRIES: Record<number, Cell> = {
  1: { name: 'Li-ion NMC', short: 'NMC', empty: 3.0, restMin: 3.0, restMax: 4.22, full: 4.2 },
  2: { name: 'LiFePO₄', short: 'LFP', empty: 2.8, restMin: 2.5, restMax: 3.65, full: 3.65 },
};

export type EvidenceKind = 'ok' | 'info' | 'warn';
export type EvidenceLine = { kind: EvidenceKind; text: string };

export function packName(p: Pick<PackCandidate, 'cells' | 'chemistry'>): string {
  return `${p.cells}S ${CHEMISTRIES[p.chemistry]?.name ?? '?'}`;
}

export function packShort(p: Pick<PackCandidate, 'cells' | 'chemistry'>): string {
  return `${p.cells}S ${CHEMISTRIES[p.chemistry]?.short ?? '?'}`;
}

export function confidenceLabel(c: number | null): string {
  return c === 3 ? 'High' : c === 2 ? 'Medium' : c === 1 ? 'Low' : 'Not yet';
}

/** Full and empty pack voltages for a pack. */
export function packRange(
  p: Pick<PackCandidate, 'cells' | 'chemistry'>,
): { emptyV: number; fullV: number } | null {
  const c = CHEMISTRIES[p.chemistry];
  return c ? { emptyV: c.empty * p.cells, fullV: c.full * p.cells } : null;
}

const v1 = (x: number) => x.toFixed(1);
const v2 = (x: number) => x.toFixed(2);

export function evidenceLines(
  info: BatteryInfo,
  pack: Pick<PackCandidate, 'cells' | 'chemistry'>,
): EvidenceLine[] {
  const cell = CHEMISTRIES[pack.chemistry];
  if (!cell) return [];
  const out: EvidenceLine[] = [];
  if (info.vRest != null) {
    const per = info.vRest / pack.cells;
    const fits = per >= cell.restMin && per <= cell.restMax;
    out.push({
      kind: fits ? 'ok' : 'warn',
      text: `${v1(info.vRest)} V at rest is ${v2(per)} V per cell, ${fits ? 'normal' : 'not possible'} for ${cell.short}`,
    });
  } else {
    out.push({ kind: 'info', text: 'Waiting for a quiet reading (no throttle, no brake)' });
  }
  if (info.cutStartV != null) {
    const per = info.cutStartV / pack.cells;
    const typical = Math.abs(per - cell.empty) <= 0.3;
    out.push({
      kind: typical ? 'ok' : 'warn',
      text: `Controller cutoff ${v1(info.cutStartV)} V is ${v2(per)} V per cell, ${typical ? 'a typical empty point' : `unusual for ${cell.short}`}`,
    });
  } else {
    out.push({ kind: 'info', text: 'Controller battery cutoff is not set, so it gives no hint' });
  }
  const range = packRange(pack);
  if (info.vMaxSeen != null && range && info.vMaxSeen >= range.fullV - 0.05 * pack.cells) {
    out.push({ kind: 'ok', text: `Seen ${v1(info.vMaxSeen)} V at rest, a full charge` });
  } else if (range) {
    out.push({ kind: 'info', text: `Charge to full once to confirm (expect about ${v1(range.fullV)} V)` });
  }
  return out;
}

/** One line for an alternative: what its rest voltage per cell would be. */
export function alternativeLine(info: BatteryInfo, alt: PackCandidate): string {
  const per = info.vRest != null ? `${v2(info.vRest / alt.cells)} V per cell · ` : '';
  return `${per}${Math.round(alt.p * 100)} % likely`;
}

export const CELLS_MIN = 3;
export const CELLS_MAX = 36;

/** Where the capacity used for range comes from, in one line. */
export function capacityLine(info: BatteryInfo): string {
  if (info.capacityAh != null) {
    const learned = info.learnedAh != null ? ` (learned ${info.learnedAh.toFixed(1)} Ah)` : '';
    return `Entered by you: ${info.capacityAh.toFixed(1)} Ah${learned}`;
  }
  if (info.learnedAh != null) return `Learned from your rides: ${info.learnedAh.toFixed(1)} Ah`;
  return `Learning: ${info.learnIntervals} of ${info.learnNeeded} discharges of 30 % or more seen. Range shows once it is known.`;
}

export const CAPACITY_MIN_AH = 1;
export const CAPACITY_MAX_AH = 500;

export const EMPTY_CELL_MIN_V = 2.5;
export const EMPTY_CELL_MAX_V = 3.7;

/** An empty-point step (V per cell), kept in range and to two decimals. */
export function stepEmptyCell(v: number, delta: number): number {
  const next = Math.round((v + delta) * 100) / 100;
  return Math.min(EMPTY_CELL_MAX_V, Math.max(EMPTY_CELL_MIN_V, next));
}

/** "Empty at 3.00 V/cell (48.0 V) · default" for the battery screen; null without a pack. */
export function emptyLine(info: BatteryInfo): string | null {
  const v = info.emptyCellV ?? info.defaultEmptyCellV;
  const pack = info.confirmed ?? info.detected;
  if (v == null || !pack) return null;
  return `Empty (0 %) at ${v.toFixed(2)} V/cell (${(v * pack.cells).toFixed(1)} V) · ${info.emptyCellV != null ? 'yours' : 'default'}`;
}

/** A capacity step for the stepper, kept in range and to one decimal. */
export function stepCapacity(ah: number, delta: number): number {
  const next = Math.round((ah + delta) * 10) / 10;
  return Math.min(CAPACITY_MAX_AH, Math.max(CAPACITY_MIN_AH, next));
}
