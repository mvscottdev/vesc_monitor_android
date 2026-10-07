import { readFileSync } from 'fs';
import { join } from 'path';

import { isTelemetryFrameV1 } from '../contract';

const golden: unknown = JSON.parse(
  readFileSync(join(__dirname, '../../../contract/telemetry-frame.v1.json'), 'utf8'),
);

describe('telemetry frame contract v1', () => {
  it('accepts the golden sample', () => {
    expect(isTelemetryFrameV1(golden)).toBe(true);
  });

  it('rejects a frame with a wrong version or a missing field', () => {
    const g = golden as Record<string, unknown>;
    expect(isTelemetryFrameV1({ ...g, v: 2 })).toBe(false);
    expect(isTelemetryFrameV1({ ...g, combined: { ...(g.combined as object), partial: true } })).toBe(false);
    const { seq: _seq, ...noSeq } = g;
    expect(isTelemetryFrameV1(noSeq)).toBe(false);
  });

  it('accepts a slice-1 frame without the optional fields and rejects a wrong optional type', () => {
    const g = golden as { combined: Record<string, unknown>; vescs: Record<string, unknown>[] };
    const { powerW: _p, ...bareCombined } = g.combined;
    expect(isTelemetryFrameV1({ ...g, combined: bareCombined })).toBe(true);
    expect(isTelemetryFrameV1({ ...g, combined: { ...g.combined, powerW: '12' } })).toBe(false);
    expect(isTelemetryFrameV1({ ...g, vescs: [{ ...g.vescs[0], duty: true }] })).toBe(false);
  });

  it('allows unknown voltage and extra optional fields', () => {
    const g = golden as { combined: object; vescs: object[] };
    expect(isTelemetryFrameV1({ ...g, combined: { ...g.combined, voltageV: null }, extra: 1 })).toBe(true);
  });
});
