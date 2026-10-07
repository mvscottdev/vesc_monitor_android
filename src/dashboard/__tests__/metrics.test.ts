import { readFileSync } from 'fs';
import { join } from 'path';

import type { TelemetryFrameV1 } from '@modules/vesc';

import {
  cardModel,
  fmtPower,
  NO_VALUE,
  speedScaleKmh,
  speedScaleMph,
  tempZone,
  widgetModel,
} from '../metrics';
import type { Units } from '@/settings/units';

const IMPERIAL: Units = { speed: 'mph', temp: 'f' };

const golden = JSON.parse(
  readFileSync(join(__dirname, '../../../contract/telemetry-frame.v1.json'), 'utf8'),
) as TelemetryFrameV1;

const single: TelemetryFrameV1 = {
  ...golden,
  combined: { ...golden.combined, ...golden.vescs[0], fresh: 1, total: 1, voltageV: 48.3 },
  vescs: golden.vescs.slice(0, 1),
};

describe('widget models', () => {
  it('shows placeholders before the first frame, never zeros', () => {
    const m = widgetModel(null, 'power', 'combined');
    expect(m.value).toBe(NO_VALUE);
    expect(m.stale).toBe(false);
  });

  it('one controller: combined is that controller', () => {
    expect(widgetModel(single, 'voltage', 'combined').value).toBe('48.3');
    expect(widgetModel(single, 'current', 'combined').value).toBe('14.2');
  });

  it('two controllers: sums power, labels a partial combined value', () => {
    expect(widgetModel(golden, 'power', 'combined')).toMatchObject({ value: '1.47', unit: 'kW' });
    const partial = { ...golden, combined: { ...golden.combined, fresh: 1, partial: 1 as const } };
    expect(widgetModel(partial, 'voltage', 'combined').sub).toBe('1/2');
  });

  it('reads one controller by CAN ID', () => {
    expect(widgetModel(golden, 'current', { controllerId: 20 }).value).toBe('16.3');
  });

  it('a stale controller keeps its value, dimmed, with an age tag', () => {
    const stale = { ...golden, vescs: [{ ...golden.vescs[0]!, fresh: 0 as const, ageMs: 4200 }] };
    expect(widgetModel(stale, 'current', { controllerId: 10 })).toMatchObject({
      value: '14.2',
      stale: true,
      age: '4 s',
    });
    expect(cardModel(stale, 10)).toMatchObject({ stale: true, age: '4 s' });
  });

  it('power bar: drive right, regen left, scaled to the session peaks', () => {
    const regen = { ...golden, combined: { ...golden.combined, powerW: -320 } };
    const m = widgetModel(regen, 'power', 'combined');
    expect(m.fraction).toBeCloseTo(-0.5);
    expect(m.peak).toBeCloseTo(1);
    expect(m.peak2).toBeCloseTo(-1);
  });

  it('speed: live from the combined value, dashes when unknown or stale', () => {
    expect(widgetModel(golden, 'speed', 'combined')).toMatchObject({
      value: '30',
      label: 'VESC',
      sub: 'max 45',
      value2: '60',
    });
    expect(widgetModel(golden, 'speed', 'combined').peak).toBeCloseTo(0.75);
    const stale = { ...golden, combined: { ...golden.combined, fresh: 0 } };
    expect(widgetModel(stale, 'speed', 'combined').value).toBe(NO_VALUE);
    const unknown = {
      ...golden,
      combined: { ...golden.combined, speedMps: null, speedSource: 0, speedWheelMissing: 1 as const },
    };
    expect(widgetModel(unknown, 'speed', 'combined')).toMatchObject({
      value: NO_VALUE,
      sub: 'Set wheel size in VESC Tool',
    });
    expect(cardModel(golden, 20).speed).toBe('31');
  });

  it('speed scale rounds the session max up to the next 20, at least 60', () => {
    expect(speedScaleKmh(0)).toBe(60);
    expect(speedScaleKmh(61)).toBe(80);
    expect(speedScaleKmh(80)).toBe(80);
  });

  it('battery: SoC with ~ and confidence when not high, pack and V/cell', () => {
    expect(widgetModel(golden, 'battery', 'combined')).toMatchObject({
      value: '~63',
      sub: '13S NMC · medium',
      value2: '48.4 V · 3.72 V/cell',
    });
    const high = { ...golden, combined: { ...golden.combined, socConfidence: 3 } };
    expect(widgetModel(high, 'battery', 'combined').value).toBe('63');
    const low = { ...golden, combined: { ...golden.combined, socPct: 8 } };
    expect(widgetModel(low, 'battery', 'combined').zone).toBe('crit');
  });

  it('range from the learned capacity, with Wh/km and trip', () => {
    expect(widgetModel(golden, 'range', 'combined')).toMatchObject({
      value: '9.6',
      unit: 'km',
      sub: '18.0 Wh/km',
      value2: '3.3 km trip',
    });
  });

  it('range says learning without a capacity', () => {
    const learning = { ...golden, combined: { ...golden.combined, rangeKm: null } };
    expect(widgetModel(learning, 'range', 'combined')).toMatchObject({
      value: NO_VALUE,
      unit: 'km · learning',
    });
  });

  it('sag and min voltage', () => {
    expect(widgetModel(golden, 'sag', 'combined')).toMatchObject({ value: '2.4', value2: 'min 44.1 V' });
  });
});

describe('formatting and zones', () => {
  it('formats power in W below 1 kW', () => {
    expect(fmtPower(850)).toEqual({ value: '850', unit: 'W' });
    expect(fmtPower(1840)).toEqual({ value: '1.84', unit: 'kW' });
  });

  it('temperature zones warn 10 °C before derating and go crit at derating', () => {
    expect(tempZone(60)).toBe('ok');
    expect(tempZone(75)).toBe('warn');
    expect(tempZone(85)).toBe('crit');
    expect(tempZone(null)).toBe('none');
  });

  it('card names a fault', () => {
    const f = { ...golden, vescs: [{ ...golden.vescs[0]!, faultCode: 3 }] };
    expect(cardModel(f, 10)).toMatchObject({ fault: 'Fault 3', faulted: true });
  });

  it('follows imperial display units, zones still from °C', () => {
    const speed = widgetModel(golden, 'speed', 'combined', IMPERIAL);
    expect(speed.unit).toBe('mph');
    expect(Number(speed.value)).toBe(Math.round((Math.abs(golden.combined.speedMps!) * 3600) / 1609.344));
    expect(speedScaleMph(0)).toBe(40);
    expect(speedScaleMph(41)).toBe(50);
    const temp = widgetModel(golden, 'temperature', 'combined', IMPERIAL);
    expect(temp.unit).toBe('°F');
    expect(temp.zone).toBe(widgetModel(golden, 'temperature', 'combined').zone);
    const range = widgetModel(golden, 'range', 'combined', IMPERIAL);
    expect(range.unit).toBe('mi');
    expect(range.sub).toMatch(/Wh\/mi$/);
    expect(cardModel(golden, 20, IMPERIAL).speed).toBe(
      ((Math.abs(golden.vescs.find((v) => v.controllerId === 20)!.speedMps!) * 3600) / 1609.344).toFixed(0),
    );
  });
});
