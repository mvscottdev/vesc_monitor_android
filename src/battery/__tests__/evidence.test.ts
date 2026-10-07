import type { BatteryInfo } from '@modules/vesc';

import {
  alternativeLine,
  capacityLine,
  confidenceLabel,
  evidenceLines,
  packName,
  packRange,
  stepCapacity,
  emptyLine,
  stepEmptyCell,
} from '../evidence';

const info: BatteryInfo = {
  confirmed: null,
  detected: { cells: 16, chemistry: 1, p: 0.86 },
  confidence: 3,
  confirmChemistry: 0,
  alternatives: [{ cells: 20, chemistry: 2, p: 0.1 }],
  vRest: 62.4,
  vMaxSeen: null,
  cutStartV: 48.0,
  cutEndV: 45.0,
  capacityAh: null,
  learnedAh: null,
  learnIntervals: 1,
  learnNeeded: 2,
};

describe('battery evidence', () => {
  it('explains a good NMC guess', () => {
    const lines = evidenceLines(info, { cells: 16, chemistry: 1 });
    expect(lines.map((l) => l.kind)).toEqual(['ok', 'ok', 'info']);
    expect(lines[0]?.text).toBe('62.4 V at rest is 3.90 V per cell, normal for NMC');
    expect(lines[1]?.text).toContain('3.00 V per cell, a typical empty point');
    expect(lines[2]?.text).toContain('about 67.2 V');
  });

  it('flags a pack the voltages contradict', () => {
    const lines = evidenceLines(info, { cells: 20, chemistry: 2 });
    expect(lines[0]?.kind).toBe('ok'); // 3.12 V/cell fits LFP rest
    expect(lines[1]?.kind).toBe('warn'); // 2.40 V/cell cutoff
    expect(evidenceLines(info, { cells: 12, chemistry: 1 })[0]?.kind).toBe('warn');
  });

  it('notes a full charge and missing inputs', () => {
    const full = { ...info, vMaxSeen: 67.1, cutStartV: null, vRest: null };
    expect(evidenceLines(full, { cells: 16, chemistry: 1 }).map((l) => l.kind)).toEqual([
      'info',
      'info',
      'ok',
    ]);
  });

  it('formats names and alternatives', () => {
    expect(packName({ cells: 16, chemistry: 2 })).toBe('16S LiFePO₄');
    expect(packRange({ cells: 13, chemistry: 1 })?.fullV).toBeCloseTo(54.6);
    expect(alternativeLine(info, info.alternatives[0]!)).toBe('3.12 V per cell · 10 % likely');
    expect(confidenceLabel(null)).toBe('Not yet');
  });

  it('says where the capacity comes from', () => {
    expect(capacityLine(info)).toMatch(/^Learning: 1 of 2/);
    expect(capacityLine({ ...info, learnedAh: 12.06 })).toBe('Learned from your rides: 12.1 Ah');
    expect(capacityLine({ ...info, capacityAh: 20, learnedAh: 12 })).toBe(
      'Entered by you: 20.0 Ah (learned 12.0 Ah)',
    );
    expect(stepCapacity(10, 0.5)).toBe(10.5);
    expect(stepCapacity(1.2, -5)).toBe(1);
    expect(stepCapacity(499, 5)).toBe(500);
  });
});

describe('empty point', () => {
  it('shows the default or the rider value with the pack voltage', () => {
    expect(emptyLine({ ...info, defaultEmptyCellV: 3 })).toBe(
      'Empty (0 %) at 3.00 V/cell (48.0 V) · default',
    );
    expect(emptyLine({ ...info, emptyCellV: 3.3, defaultEmptyCellV: 3 })).toBe(
      'Empty (0 %) at 3.30 V/cell (52.8 V) · yours',
    );
    expect(emptyLine({ ...info, detected: null })).toBeNull();
  });

  it('steps within range', () => {
    expect(stepEmptyCell(3, 0.01)).toBe(3.01);
    expect(stepEmptyCell(2.55, -0.1)).toBe(2.5);
    expect(stepEmptyCell(3.65, 0.1)).toBe(3.7);
  });
});
