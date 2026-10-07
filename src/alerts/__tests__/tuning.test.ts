import { clearText, EDIT, stepMargin, stepRepeat } from '../catalog';

describe('alert clear margin and repeat time', () => {
  it('says where the first level clears', () => {
    expect(clearText(85, 3, EDIT.motor_temp!, true)).toBe('Clears below 82 °C');
    expect(clearText(20, 2, EDIT.low_battery!, false)).toBe('Clears above 22 %');
  });

  it('never steps the margin below zero', () => {
    expect(stepMargin(0, EDIT.motor_temp!, -1)).toBe(0);
    expect(stepMargin(3, EDIT.motor_temp!, 1)).toBe(4);
  });

  it('steps the repeat time in 10 s within an hour', () => {
    expect(stepRepeat(60, 1)).toBe(70);
    expect(stepRepeat(0, -1)).toBe(0);
    expect(stepRepeat(3600, 1)).toBe(3600);
  });
});
