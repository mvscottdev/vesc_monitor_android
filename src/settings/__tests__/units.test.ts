import {
  distOf,
  METRIC,
  parseUnits,
  perDistOf,
  speedOf,
  speedUnit,
  tempDeltaOf,
  tempOf,
  tempUnit,
  type Units,
} from '../units';

const IMPERIAL: Units = { speed: 'mph', temp: 'f' };

describe('display units', () => {
  it('converts speed and distance', () => {
    expect(speedOf(10, METRIC)).toBeCloseTo(36);
    expect(speedOf(10, IMPERIAL)).toBeCloseTo(22.369, 3);
    expect(distOf(1609.344, IMPERIAL)).toBeCloseTo(1);
    expect(distOf(2500, METRIC)).toBeCloseTo(2.5);
    expect(perDistOf(20, IMPERIAL)).toBeCloseTo(32.187, 3);
    expect(speedUnit(IMPERIAL)).toBe('mph');
  });

  it('converts temperatures and differences', () => {
    expect(tempOf(100, IMPERIAL)).toBeCloseTo(212);
    expect(tempOf(-40, IMPERIAL)).toBeCloseTo(-40);
    expect(tempOf(85, METRIC)).toBe(85);
    expect(tempDeltaOf(10, IMPERIAL)).toBeCloseTo(18);
    expect(tempUnit(METRIC)).toBe('°C');
  });

  it('reads stored settings, falling back to metric', () => {
    expect(parseUnits(null)).toEqual(METRIC);
    expect(parseUnits('not json')).toEqual(METRIC);
    expect(parseUnits('{"speed":"mph","temp":"x"}')).toEqual({ speed: 'mph', temp: 'c' });
    expect(parseUnits(JSON.stringify(IMPERIAL))).toEqual(IMPERIAL);
  });
});
