import { controllerLabel, formatDuration, reconnectDetail, signalBars } from '../format';

describe('controllerLabel', () => {
  it('marks the local controller', () => {
    expect(controllerLabel(10, true)).toBe('ID 10 (local)');
    expect(controllerLabel(20, false)).toBe('ID 20');
  });
});

describe('formatDuration', () => {
  it('uses m:ss below an hour and h:mm:ss above', () => {
    expect(formatDuration(0)).toBe('0:00');
    expect(formatDuration(761_000)).toBe('12:41');
    expect(formatDuration(3_725_000)).toBe('1:02:05');
  });

  it('turns RSSI into signal bars', () => {
    expect(signalBars(-54)).toBe(4);
    expect(signalBars(-67)).toBe(3);
    expect(signalBars(-78)).toBe(2);
    expect(signalBars(-91)).toBe(0);
  });
});

describe('reconnectDetail', () => {
  it('names the attempt and whether the ride is still recording', () => {
    expect(reconnectDetail(3, true)).toBe('Attempt 3. The ride keeps recording.');
    expect(reconnectDetail(0, false)).toBe('Retrying.');
  });
});
