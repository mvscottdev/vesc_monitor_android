// Default ride-mode layouts, chosen by the number of detected controllers.
// Single controller: the main page plus a details page. Several: the same main page
// with combined values plus controller pages (one card per controller). Nothing
// assumes a fixed count.
import { emptyPage, type Layout, type Page, type Widget } from './layout';

type Controller = { controllerId: number };

const MAX_CARDS_PER_PAGE = 4;

const w = (id: string, type: Widget['type'], x: number, y: number, ww: number, h: number): Widget => ({
  id,
  type,
  x,
  y,
  w: ww,
  h,
  source: 'combined',
});

function mainPage(): Page {
  return {
    ...emptyPage('main'),
    widgets: [
      w('battery', 'battery', 0, 0, 3, 3),
      w('range', 'range', 0, 3, 3, 3),
      w('speed', 'speed', 3, 0, 5, 6),
      w('power', 'power', 8, 0, 4, 2),
      w('temperature', 'temperature', 8, 2, 4, 2),
      w('sag', 'sag', 8, 4, 4, 2),
    ],
  };
}

/** One controller: voltage, currents, duty and power next to its card. */
function detailsPage(c: Controller): Page {
  return {
    ...emptyPage('details'),
    widgets: [
      w('details-voltage', 'voltage', 0, 0, 4, 3),
      w('details-current', 'current', 4, 0, 4, 3),
      w('details-duty', 'duty', 0, 3, 4, 3),
      w('details-power', 'power', 4, 3, 4, 3),
      card(c, 8, 4),
    ],
  };
}

function card(c: Controller, x: number, w: number): Widget {
  return {
    id: `card-${c.controllerId}`,
    type: 'controller',
    x,
    y: 0,
    w,
    h: 6,
    source: { controllerId: c.controllerId },
  };
}

/** Two controllers: combined column on the left, two cards on the right. */
function pairPage(controllers: Controller[]): Page {
  const left = (id: string, type: Widget['type'], y: number): Widget => ({
    id: `pair-${id}`,
    type,
    x: 0,
    y,
    w: 4,
    h: 2,
    source: 'combined',
  });
  return {
    ...emptyPage('controllers-1'),
    widgets: [
      left('voltage', 'voltage', 0),
      left('power', 'power', 2),
      left('duty', 'duty', 4),
      ...controllers.map((c, i) => card(c, 4 + i * 4, 4)),
    ],
  };
}

/** Three or more controllers: up to four cards per page, sharing the width. */
function cardPages(controllers: Controller[]): Page[] {
  const pages: Page[] = [];
  for (let i = 0; i < controllers.length; i += MAX_CARDS_PER_PAGE) {
    const group = controllers.slice(i, i + MAX_CARDS_PER_PAGE);
    const width = Math.min(6, Math.floor(12 / group.length));
    pages.push({
      ...emptyPage(`controllers-${pages.length + 1}`),
      widgets: group.map((c, j) => card(c, j * width, width)),
    });
  }
  return pages;
}

export function defaultLayout(controllers: Controller[]): Layout {
  const pages = [mainPage()];
  const only = controllers.length === 1 ? controllers[0] : undefined;
  if (only) pages.push(detailsPage(only));
  else if (controllers.length === 2) pages.push(pairPage(controllers));
  else if (controllers.length > 2) pages.push(...cardPages(controllers));
  return { version: 1, pages };
}

/** Main page variants the rider can pick in edit mode; controller pages stay the same. */
export type PresetName = 'commute' | 'performance' | 'minimal';

export const PRESET_NAMES: Record<PresetName, string> = {
  commute: 'Commute',
  performance: 'Performance',
  minimal: 'Minimal',
};

/** Power and heat first: big power bar, duty and both temperatures next to the speed. */
function performancePage(): Page {
  return {
    ...emptyPage('main'),
    widgets: [
      w('speed', 'speed', 0, 0, 5, 6),
      w('power', 'power', 5, 0, 7, 2),
      w('temperature', 'temperature', 5, 2, 7, 2),
      w('duty', 'duty', 5, 4, 3, 2),
      w('sag', 'sag', 8, 4, 4, 2),
    ],
  };
}

/** Just speed and battery, large. */
function minimalPage(): Page {
  return {
    ...emptyPage('main'),
    widgets: [
      w('speed', 'speed', 0, 0, 7, 6),
      w('battery', 'battery', 7, 0, 5, 3),
      w('range', 'range', 7, 3, 5, 3),
    ],
  };
}

/** The default layout for these controllers with the chosen main page. */
export function presetLayout(name: PresetName, controllers: Controller[]): Layout {
  const base = defaultLayout(controllers);
  const main = name === 'performance' ? performancePage() : name === 'minimal' ? minimalPage() : mainPage();
  return { ...base, pages: [main, ...base.pages.slice(1)] };
}
