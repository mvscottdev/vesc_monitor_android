import { cellBox, cellStep, dragCells } from '../geometry';

describe('cell geometry', () => {
  // Reference screen 915 x 412 minus the 30 dp status strip.
  const W = 915;
  const H = 382;

  it('a full-page widget fills the page inside the padding', () => {
    const b = cellBox({ x: 0, y: 0, w: 12, h: 6 }, W, H);
    expect(b.x).toBe(10);
    expect(b.width).toBeCloseTo(W - 20);
    expect(b.y + b.height).toBeCloseTo(H - 10);
  });

  it('neighbours are one gap apart', () => {
    const a = cellBox({ x: 0, y: 0, w: 3, h: 3 }, W, H);
    const b = cellBox({ x: 3, y: 0, w: 5, h: 6 }, W, H);
    expect(b.x - (a.x + a.width)).toBeCloseTo(8);
  });

  it('turns a drag into whole cells', () => {
    const step = cellStep(1000, 500, 4, 2);
    expect(step.sx).toBeGreaterThan(0);
    expect(dragCells(step.sx * 1.4, -step.sy * 0.6, step)).toEqual({ dc: 1, dr: -1 });
    expect(dragCells(step.sx * 0.4, step.sy * 0.2, step)).toEqual({ dc: 0, dr: 0 });
    expect(dragCells(10, 10, { sx: 0, sy: 0 })).toEqual({ dc: 0, dr: 0 });
  });
});
