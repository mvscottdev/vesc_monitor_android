// Cell grid → pixels for the ride-mode page (values from the design tokens).
import { grid } from '@/theme/tokens';

import type { Rect } from './layout';

export type Box = { x: number; y: number; width: number; height: number };

export function cellBox(
  r: Rect,
  width: number,
  height: number,
  cols: number = grid.columns,
  rows: number = grid.rows,
): Box {
  const cw = (width - 2 * grid.padding - (cols - 1) * grid.gap) / cols;
  const ch = (height - 2 * grid.padding - (rows - 1) * grid.gap) / rows;
  return {
    x: grid.padding + r.x * (cw + grid.gap),
    y: grid.padding + r.y * (ch + grid.gap),
    width: r.w * cw + (r.w - 1) * grid.gap,
    height: r.h * ch + (r.h - 1) * grid.gap,
  };
}

/** Pixels from one cell to the next (cell plus gap), for turning a drag into cells. */
export function cellStep(
  width: number,
  height: number,
  cols: number = grid.columns,
  rows: number = grid.rows,
): { sx: number; sy: number } {
  const cw = (width - 2 * grid.padding - (cols - 1) * grid.gap) / cols;
  const ch = (height - 2 * grid.padding - (rows - 1) * grid.gap) / rows;
  return { sx: cw + grid.gap, sy: ch + grid.gap };
}

/** A drag of [dx] × [dy] pixels as whole cells (nearest). */
export function dragCells(
  dx: number,
  dy: number,
  step: { sx: number; sy: number },
): { dc: number; dr: number } {
  return {
    dc: step.sx > 0 ? Math.round(dx / step.sx) + 0 : 0,
    dr: step.sy > 0 ? Math.round(dy / step.sy) + 0 : 0,
  };
}
