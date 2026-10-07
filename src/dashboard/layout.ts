// Pure layout engine for the ride-mode widget grid: widgets are {x, y, w, h} in cells
// and never overlap. No React, no native code: Jest-tested.
import { grid } from '@/theme/tokens';

export type WidgetType =
  | 'speed'
  | 'battery'
  | 'range'
  | 'sag'
  | 'voltage'
  | 'current'
  | 'power'
  | 'temperature'
  | 'duty'
  | 'controller';

/** Which controller a widget reads: the combined value or one controller by CAN ID. */
export type WidgetSource = 'combined' | { controllerId: number };

export type Rect = { x: number; y: number; w: number; h: number };

export type Widget = Rect & { id: string; type: WidgetType; source: WidgetSource };

export type Page = { id: string; cols: number; rows: number; widgets: Widget[] };

export type Layout = { version: 1; pages: Page[] };

type Size = { w: number; h: number };

/** Smallest and largest size per widget type, in cells. */
export const SIZE_LIMITS: Record<WidgetType, { min: Size; max: Size }> = {
  speed: { min: { w: 3, h: 2 }, max: { w: 12, h: 6 } },
  battery: { min: { w: 3, h: 2 }, max: { w: 6, h: 6 } },
  range: { min: { w: 3, h: 2 }, max: { w: 6, h: 6 } },
  sag: { min: { w: 3, h: 2 }, max: { w: 6, h: 6 } },
  voltage: { min: { w: 2, h: 1 }, max: { w: 6, h: 6 } },
  current: { min: { w: 2, h: 1 }, max: { w: 6, h: 6 } },
  duty: { min: { w: 2, h: 1 }, max: { w: 6, h: 6 } },
  power: { min: { w: 3, h: 2 }, max: { w: 12, h: 6 } },
  temperature: { min: { w: 3, h: 2 }, max: { w: 12, h: 6 } },
  controller: { min: { w: 3, h: 4 }, max: { w: 6, h: 6 } },
};

export function emptyPage(id: string): Page {
  return { id, cols: grid.columns, rows: grid.rows, widgets: [] };
}

export function collides(a: Rect, b: Rect): boolean {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
}

/** Inside the page and within the type's size limits. */
export function fits(page: Page, w: Pick<Widget, 'type'> & Rect): boolean {
  const lim = SIZE_LIMITS[w.type];
  const inside = w.x >= 0 && w.y >= 0 && w.x + w.w <= page.cols && w.y + w.h <= page.rows;
  const sized = w.w >= lim.min.w && w.h >= lim.min.h && w.w <= lim.max.w && w.h <= lim.max.h;
  return inside && sized && [w.x, w.y, w.w, w.h].every(Number.isInteger);
}

export function canPlace(page: Page, w: Widget): boolean {
  return fits(page, w) && page.widgets.every((o) => o.id === w.id || !collides(o, w));
}

/** Adds [w] at the first free spot (row by row) at its own size; null if the page is full. */
export function place(page: Page, w: Omit<Widget, 'x' | 'y'>): Page | null {
  for (let y = 0; y + w.h <= page.rows; y++) {
    for (let x = 0; x + w.w <= page.cols; x++) {
      const candidate = { ...w, x, y };
      if (canPlace(page, candidate)) return { ...page, widgets: [...page.widgets, candidate] };
    }
  }
  return null;
}

/** Moves or resizes a widget; null when the result would overlap or leave the limits. */
export function update(page: Page, id: string, rect: Partial<Rect>): Page | null {
  const current = page.widgets.find((w) => w.id === id);
  if (!current) return null;
  const next = { ...current, ...rect };
  if (!canPlace(page, next)) return null;
  return { ...page, widgets: page.widgets.map((w) => (w.id === id ? next : w)) };
}

export const move = (page: Page, id: string, x: number, y: number) => update(page, id, { x, y });

export const resize = (page: Page, id: string, w: number, h: number) => update(page, id, { w, h });

/** True if no two widgets overlap and every widget fits: the invariant of every stored layout. */
export function isValidPage(page: Page): boolean {
  return page.widgets.every(
    (w, i) => fits(page, w) && page.widgets.slice(i + 1).every((o) => !collides(w, o)),
  );
}
