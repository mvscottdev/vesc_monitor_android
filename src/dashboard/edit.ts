// Edit-mode operations on a layout and its stored form. Pure: every operation returns a
// new layout, or null when the result would break the grid invariant.
import {
  emptyPage,
  type Layout,
  type Page,
  place,
  SIZE_LIMITS,
  update,
  isValidPage,
  type WidgetSource,
  type WidgetType,
} from './layout';

export const WIDGET_NAMES: Record<WidgetType, string> = {
  speed: 'Speed',
  battery: 'Battery',
  range: 'Range',
  sag: 'Voltage sag',
  voltage: 'Voltage',
  current: 'Current',
  power: 'Power',
  temperature: 'Temperature',
  duty: 'Duty',
  controller: 'Controller card',
};

/** Types whose value can come from one controller (the others are vehicle-wide). */
export const PER_CONTROLLER: WidgetType[] = [
  'voltage',
  'current',
  'power',
  'temperature',
  'duty',
  'controller',
];

function withPage(layout: Layout, index: number, page: Page | null): Layout | null {
  if (!page) return null;
  return { ...layout, pages: layout.pages.map((p, i) => (i === index ? page : p)) };
}

function widget(layout: Layout, index: number, id: string) {
  return layout.pages[index]?.widgets.find((w) => w.id === id);
}

export function nudge(layout: Layout, index: number, id: string, dx: number, dy: number): Layout | null {
  const w = widget(layout, index, id);
  const page = layout.pages[index];
  if (!w || !page) return null;
  return withPage(layout, index, update(page, id, { x: w.x + dx, y: w.y + dy }));
}

export function grow(layout: Layout, index: number, id: string, dw: number, dh: number): Layout | null {
  const w = widget(layout, index, id);
  const page = layout.pages[index];
  if (!w || !page) return null;
  return withPage(layout, index, update(page, id, { w: w.w + dw, h: w.h + dh }));
}

export function removeWidget(layout: Layout, index: number, id: string): Layout {
  const page = layout.pages[index];
  if (!page) return layout;
  return withPage(layout, index, { ...page, widgets: page.widgets.filter((w) => w.id !== id) }) ?? layout;
}

/** Adds a widget at its minimum size at the first free spot; null when the page is full. */
export function addWidget(
  layout: Layout,
  index: number,
  type: WidgetType,
  source: WidgetSource,
): Layout | null {
  const page = layout.pages[index];
  if (!page) return null;
  const taken = new Set(layout.pages.flatMap((p) => p.widgets.map((w) => w.id)));
  let n = 1;
  while (taken.has(`${type}-${n}`)) n++;
  const { min } = SIZE_LIMITS[type];
  return withPage(layout, index, place(page, { id: `${type}-${n}`, type, source, w: min.w, h: min.h }));
}

/** Storage key: layouts are kept per set of controllers. */
export function layoutKey(controllerIds: number[]): string {
  return `ui.layout.${[...controllerIds].sort((a, b) => a - b).join('-') || 'none'}`;
}

const TYPES = new Set(Object.keys(SIZE_LIMITS));

/** A stored layout, or null when it is unreadable, invalid or names a missing controller. */
export function parseLayout(json: string | null, controllerIds: number[]): Layout | null {
  if (!json) return null;
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    return null;
  }
  const l = raw as Layout;
  if (l?.version !== 1 || !Array.isArray(l.pages) || l.pages.length === 0) return null;
  for (const p of l.pages) {
    if (typeof p?.id !== 'string' || !Array.isArray(p.widgets) || !isValidPage(p)) return null;
    for (const w of p.widgets) {
      if (!TYPES.has(w.type)) return null;
      const s = w.source;
      if (s !== 'combined' && !(typeof s === 'object' && controllerIds.includes(s?.controllerId)))
        return null;
    }
  }
  return l;
}

export const MAX_PAGES = 6;

/** Appends an empty page; null at the page limit. */
export function addPage(layout: Layout): Layout | null {
  if (layout.pages.length >= MAX_PAGES) return null;
  const taken = new Set(layout.pages.map((p) => p.id));
  let n = layout.pages.length + 1;
  while (taken.has(`page-${n}`)) n++;
  return { ...layout, pages: [...layout.pages, emptyPage(`page-${n}`)] };
}

/** Removes a page; the last page always stays. */
export function removePage(layout: Layout, index: number): Layout | null {
  if (layout.pages.length <= 1 || !layout.pages[index]) return null;
  return { ...layout, pages: layout.pages.filter((_, i) => i !== index) };
}
