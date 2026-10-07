import { collides, emptyPage, isValidPage, move, place, resize, type Page, type Widget } from '../layout';
import { defaultLayout, PRESET_NAMES, type PresetName, presetLayout } from '../presets';

const widget = (id: string, x: number, y: number, w: number, h: number): Widget => ({
  id,
  type: 'voltage',
  x,
  y,
  w,
  h,
  source: 'combined',
});

const page = (...widgets: Widget[]): Page => ({ ...emptyPage('p'), widgets });

describe('layout engine', () => {
  it('detects overlap but not touching edges', () => {
    expect(collides(widget('a', 0, 0, 2, 2), widget('b', 1, 1, 2, 2))).toBe(true);
    expect(collides(widget('a', 0, 0, 2, 2), widget('b', 2, 0, 2, 2))).toBe(false);
  });

  it('places a widget at the first free spot', () => {
    const p = place(page(widget('a', 0, 0, 3, 3)), {
      id: 'b',
      type: 'voltage',
      w: 3,
      h: 3,
      source: 'combined',
    });
    expect(p?.widgets[1]).toMatchObject({ x: 3, y: 0 });
  });

  it('returns null when the page is full', () => {
    const full = page(widget('a', 0, 0, 6, 6), widget('b', 6, 0, 6, 6));
    expect(place(full, { id: 'c', type: 'voltage', w: 2, h: 1, source: 'combined' })).toBeNull();
  });

  it('rejects a move onto another widget or off the page', () => {
    const p = page(widget('a', 0, 0, 2, 2), widget('b', 4, 0, 2, 2));
    expect(move(p, 'a', 3, 0)).toBeNull();
    expect(move(p, 'a', 11, 0)).toBeNull();
    expect(move(p, 'a', 0, 4)?.widgets[0]).toMatchObject({ x: 0, y: 4 });
  });

  it('keeps sizes within the widget limits', () => {
    const p = page(widget('a', 0, 0, 2, 2));
    expect(resize(p, 'a', 1, 1)).toBeNull();
    expect(resize(p, 'a', 7, 2)).toBeNull();
    expect(resize(p, 'a', 4, 3)?.widgets[0]).toMatchObject({ w: 4, h: 3 });
  });
});

describe('default layouts', () => {
  const ids = (n: number) => Array.from({ length: n }, (_, i) => ({ controllerId: 10 * (i + 1) }));

  it('one controller: main page with combined values, then a details page with its card', () => {
    const l = defaultLayout(ids(1));
    expect(l.pages).toHaveLength(2);
    expect((l.pages[0]?.widgets ?? []).every((w) => w.source === 'combined')).toBe(true);
    expect(l.pages[0]?.widgets.map((w) => w.type).sort()).toEqual([
      'battery',
      'power',
      'range',
      'sag',
      'speed',
      'temperature',
    ]);
    expect((l.pages[1]?.widgets ?? []).filter((w) => w.type === 'controller')).toHaveLength(1);
  });

  it('two controllers: main page plus a page with two cards', () => {
    const l = defaultLayout(ids(2));
    expect(l.pages).toHaveLength(2);
    expect((l.pages[1]?.widgets ?? []).filter((w) => w.type === 'controller')).toHaveLength(2);
  });

  it('five controllers: cards spread over two pages', () => {
    const l = defaultLayout(ids(5));
    const cards = l.pages.flatMap((p) => p.widgets.filter((w) => w.type === 'controller'));
    expect(cards).toHaveLength(5);
    expect(l.pages).toHaveLength(3);
  });

  it.each([0, 1, 2, 3, 4, 5, 8])('every page is valid with %i controllers', (n) => {
    expect(defaultLayout(ids(n)).pages.every(isValidPage)).toBe(true);
  });
});

describe('named presets', () => {
  it('every preset is valid for 1..5 controllers and keeps the controller pages', () => {
    for (const name of Object.keys(PRESET_NAMES) as PresetName[]) {
      for (let n = 0; n <= 5; n++) {
        const controllers = Array.from({ length: n }, (_, i) => ({ controllerId: i + 1 }));
        const l = presetLayout(name, controllers);
        expect(l.pages.every(isValidPage)).toBe(true);
        expect(l.pages.length).toBe(defaultLayout(controllers).pages.length);
      }
    }
  });
});
