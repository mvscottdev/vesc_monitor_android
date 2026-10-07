import {
  addPage,
  addWidget,
  grow,
  layoutKey,
  MAX_PAGES,
  nudge,
  parseLayout,
  removePage,
  removeWidget,
} from '../edit';
import { isValidPage, type Layout } from '../layout';
import { defaultLayout } from '../presets';

const two = [{ controllerId: 17 }, { controllerId: 81 }];

describe('dashboard edit', () => {
  const base = defaultLayout(two);

  it('refuses moves that overlap or leave the page', () => {
    const first = base.pages[0]!.widgets[0]!;
    expect(nudge(base, 0, first.id, -1, 0)).toBeNull();
    const cleared = removeWidget(base, 0, base.pages[0]!.widgets[1]!.id);
    expect(cleared.pages[0]!.widgets).toHaveLength(base.pages[0]!.widgets.length - 1);
  });

  it('adds at the minimum size and keeps the page valid', () => {
    const empty: Layout = { version: 1, pages: [{ ...base.pages[0]!, widgets: [] }] };
    const one = addWidget(empty, 0, 'voltage', { controllerId: 17 });
    expect(one?.pages[0]!.widgets[0]).toMatchObject({ id: 'voltage-1', x: 0, y: 0, w: 2, h: 1 });
    const bigger = grow(one!, 0, 'voltage-1', 1, 1);
    expect(bigger?.pages[0]!.widgets[0]).toMatchObject({ w: 3, h: 2 });
    expect(grow(one!, 0, 'voltage-1', -1, 0)).toBeNull();
    const twoW = addWidget(bigger!, 0, 'voltage', 'combined');
    expect(twoW?.pages[0]!.widgets[1]!.id).toBe('voltage-2');
    expect(isValidPage(twoW!.pages[0]!)).toBe(true);
  });

  it('a full page refuses another widget', () => {
    let full: Layout = { version: 1, pages: [{ ...base.pages[0]!, widgets: [] }] };
    for (let i = 0; i < 100; i++) {
      const next = addWidget(full, 0, 'voltage', 'combined');
      if (!next) break;
      full = next;
    }
    expect(full.pages[0]!.widgets.length).toBeGreaterThan(10);
    expect(addWidget(full, 0, 'voltage', 'combined')).toBeNull();
  });

  it('stores and validates layouts per controller set', () => {
    expect(layoutKey([81, 17])).toBe('ui.layout.17-81');
    expect(layoutKey([])).toBe('ui.layout.none');
    const json = JSON.stringify(base);
    expect(parseLayout(json, [17, 81])).toEqual(base);
    expect(parseLayout(json, [17])).toBeNull();
    expect(parseLayout('{bad', [17, 81])).toBeNull();
    expect(parseLayout(null, [])).toBeNull();
    const overlapping = {
      ...base,
      pages: [{ ...base.pages[0]!, widgets: [base.pages[0]!.widgets[0]!, base.pages[0]!.widgets[0]!] }],
    };
    expect(parseLayout(JSON.stringify(overlapping), [17, 81])).toBeNull();
  });

  it('adds and removes pages within limits', () => {
    const one: Layout = { version: 1, pages: [base.pages[0]!] };
    expect(removePage(one, 0)).toBeNull();
    const two = addPage(one)!;
    expect(two.pages).toHaveLength(2);
    expect(two.pages[1]!.widgets).toEqual([]);
    expect(new Set(two.pages.map((p) => p.id)).size).toBe(2);
    expect(removePage(two, 1)?.pages).toHaveLength(1);
    let full: Layout = one;
    while (full.pages.length < MAX_PAGES) full = addPage(full)!;
    expect(addPage(full)).toBeNull();
    expect(parseLayout(JSON.stringify(two), [17, 81])).toEqual(two);
  });
});
