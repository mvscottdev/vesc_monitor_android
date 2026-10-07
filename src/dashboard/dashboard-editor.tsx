// Edit mode for one ride-mode page (edit-grid mockup): the live page underneath, an
// outline per widget on top. Tap selects; drag the selected widget to move it, pull its
// corner to resize, × removes; the floating bar has Undo, Add widget, Pages and Done.
// Every step goes through the pure layout engine, so an edit that would overlap is
// refused, never half-applied.
import { useState } from 'react';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN } from 'react-native-worklets';
import {
  Alert,
  type LayoutChangeEvent,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { DashboardPage } from './dashboard-page';
import {
  addPage,
  addWidget,
  grow,
  nudge,
  PER_CONTROLLER,
  removePage,
  removeWidget,
  WIDGET_NAMES,
} from './edit';
import { type Box, cellBox, cellStep, dragCells } from './geometry';
import { type Layout, SIZE_LIMITS, type WidgetSource, type WidgetType } from './layout';
import { PRESET_NAMES, type PresetName, presetLayout } from './presets';
import { Button } from '@/components/button';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

type Props = {
  layout: Layout;
  pageIndex: number;
  multi: boolean;
  localId: number | null;
  controllerIds: number[];
  onChange: (l: Layout) => void;
  onPageIndex: (i: number) => void;
  onReset: () => void;
  onDone: () => void;
};

const TYPES = Object.keys(SIZE_LIMITS) as WidgetType[];
const HANDLE = 22;

type Drag = { id: string; kind: 'move' | 'size'; dx: number; dy: number };

/** One widget's outline; the selected one can be dragged and has a resize corner. */
function Outline({
  label,
  box,
  on,
  drag,
  onTap,
  onRemove,
  onDrag,
  onDrop,
}: {
  label: string;
  box: Box;
  on: boolean;
  drag: Drag | null;
  onTap: () => void;
  onRemove: () => void;
  onDrag: (kind: Drag['kind'], dx: number, dy: number) => void;
  onDrop: (kind: Drag['kind'], dx: number, dy: number) => void;
}) {
  // Gesture callbacks run on the UI thread; the layout work happens back on JS.
  const move = Gesture.Pan()
    .enabled(on)
    .minDistance(6)
    .onUpdate((e) => {
      scheduleOnRN(onDrag, 'move', e.translationX, e.translationY);
    })
    .onEnd((e, success) => {
      // A cancelled gesture changes nothing (zero cells).
      scheduleOnRN(onDrop, 'move', success ? e.translationX : 0, success ? e.translationY : 0);
    });
  const size = Gesture.Pan()
    .minDistance(2)
    .onUpdate((e) => {
      scheduleOnRN(onDrag, 'size', e.translationX, e.translationY);
    })
    .onEnd((e, success) => {
      // A cancelled gesture changes nothing (zero cells).
      scheduleOnRN(onDrop, 'size', success ? e.translationX : 0, success ? e.translationY : 0);
    });
  const dx = drag?.kind === 'move' ? drag.dx : 0;
  const dy = drag?.kind === 'move' ? drag.dy : 0;
  const dw = drag?.kind === 'size' ? drag.dx : 0;
  const dh = drag?.kind === 'size' ? drag.dy : 0;
  return (
    <GestureDetector gesture={move}>
      <View
        style={[
          styles.outline,
          {
            left: box.x + dx,
            top: box.y + dy,
            width: Math.max(24, box.width + dw),
            height: Math.max(24, box.height + dh),
          },
          on && styles.outlineOn,
        ]}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={label}
          accessibilityHint={on ? 'Drag to move' : 'Select'}
          onPress={onTap}
          style={StyleSheet.absoluteFill}
        />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Remove ${label}`}
          onPress={onRemove}
          hitSlop={8}
          style={styles.remove}
        >
          <Text style={styles.removeText}>×</Text>
        </Pressable>
        {on ? (
          <GestureDetector gesture={size}>
            <View style={styles.corner} accessibilityLabel={`Resize ${label}`} />
          </GestureDetector>
        ) : null}
      </View>
    </GestureDetector>
  );
}
const UNDO_DEPTH = 30;

function sourceLabel(s: WidgetSource): string {
  return s === 'combined' ? 'Combined' : `Controller ${s.controllerId}`;
}

export function DashboardEditor({
  layout,
  pageIndex,
  multi,
  localId,
  controllerIds,
  onChange,
  onPageIndex,
  onReset,
  onDone,
}: Props) {
  const page = layout.pages[pageIndex];
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [selected, setSelected] = useState<string | null>(null);
  const [picker, setPicker] = useState(false);
  const [presets, setPresets] = useState(false);
  const [history, setHistory] = useState<Layout[]>([]);
  /** Every change goes through here so it can be undone. */
  const change = (next: Layout) => {
    setHistory((h) => [...h.slice(-(UNDO_DEPTH - 1)), layout]);
    onChange(next);
  };
  const undo = () => {
    const prev = history[history.length - 1];
    if (!prev) return;
    setHistory((h) => h.slice(0, -1));
    setSelected(null);
    onChange(prev);
    onPageIndex(Math.min(pageIndex, prev.pages.length - 1));
  };
  const [refused, setRefused] = useState<string | null>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [pages, setPages] = useState(false);
  if (!page) return null;
  const sel = page.widgets.find((w) => w.id === selected) ?? null;

  const apply = (next: Layout | null, why: string) => {
    if (next) {
      setRefused(null);
      change(next);
    } else {
      setRefused(why);
    }
  };
  const onLayout = (e: LayoutChangeEvent) =>
    setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const sources: WidgetSource[] = ['combined', ...controllerIds.map((controllerId) => ({ controllerId }))];
  const step = cellStep(size.w, size.h, page.cols, page.rows);
  const drop = (id: string, kind: Drag['kind'], dx: number, dy: number) => {
    setDrag(null);
    const { dc, dr } = dragCells(dx, dy, step);
    if (dc === 0 && dr === 0) return;
    if (kind === 'move') apply(nudge(layout, pageIndex, id, dc, dr), 'No room there');
    else apply(grow(layout, pageIndex, id, dc, dr), 'That size does not fit here');
  };

  return (
    <View style={styles.screen}>
      <View style={styles.bar}>
        <Text style={styles.title}>
          Editing page {pageIndex + 1} of {layout.pages.length}
        </Text>
        <Text style={styles.hint}>
          {refused ??
            (sel
              ? `${WIDGET_NAMES[sel.type]} · ${sel.w} × ${sel.h} · drag to move, pull the corner to resize`
              : 'Tap a widget to change it')}
        </Text>
      </View>
      <View style={styles.page} onLayout={onLayout}>
        <DashboardPage page={page} multi={multi} localId={localId} />
        {size.w > 0 &&
          page.widgets.map((w) => (
            <Outline
              key={w.id}
              label={WIDGET_NAMES[w.type]}
              box={cellBox(w, size.w, size.h, page.cols, page.rows)}
              on={w.id === selected}
              drag={drag?.id === w.id ? drag : null}
              onTap={() => setSelected(w.id === selected ? null : w.id)}
              onRemove={() => {
                if (w.id === selected) setSelected(null);
                change(removeWidget(layout, pageIndex, w.id));
              }}
              onDrag={(kind, dx, dy) => setDrag({ id: w.id, kind, dx, dy })}
              onDrop={(kind, dx, dy) => drop(w.id, kind, dx, dy)}
            />
          ))}
      </View>
      <View style={styles.tools}>
        <Button label="↶ Undo" disabled={history.length === 0} onPress={undo} />
        {sel ? (
          <>
            <Button
              label="←"
              onPress={() => apply(nudge(layout, pageIndex, sel.id, -1, 0), 'No room on the left')}
            />
            <Button
              label="→"
              onPress={() => apply(nudge(layout, pageIndex, sel.id, 1, 0), 'No room on the right')}
            />
            <Button
              label="↑"
              onPress={() => apply(nudge(layout, pageIndex, sel.id, 0, -1), 'No room above')}
            />
            <Button
              label="↓"
              onPress={() => apply(nudge(layout, pageIndex, sel.id, 0, 1), 'No room below')}
            />
          </>
        ) : (
          <Button label="+ Add widget" onPress={() => setPicker(true)} />
        )}
        <Button label="Pages" onPress={() => setPages(true)} />
        <Button kind="primary" label="✓ Done" onPress={onDone} />
      </View>
      <Modal visible={pages} transparent animationType="fade" onRequestClose={() => setPages(false)}>
        <View style={styles.scrim}>
          <View style={styles.sheet}>
            <Text style={styles.title}>
              Page {pageIndex + 1} of {layout.pages.length}
            </Text>
            <View style={styles.pickRow}>
              <Button
                label="‹ Previous"
                disabled={pageIndex === 0}
                onPress={() => onPageIndex(pageIndex - 1)}
              />
              <Button
                label="Next ›"
                disabled={pageIndex >= layout.pages.length - 1}
                onPress={() => onPageIndex(pageIndex + 1)}
              />
            </View>
            <View style={styles.pickRow}>
              <Button
                label="Add page"
                onPress={() => {
                  const next = addPage(layout);
                  apply(next, 'No more pages');
                  if (next) onPageIndex(next.pages.length - 1);
                }}
              />
              <Button
                label="Remove this page"
                disabled={layout.pages.length <= 1}
                onPress={() => {
                  const dropPage = () => {
                    const next = removePage(layout, pageIndex);
                    apply(next, 'The last page stays');
                    if (next) onPageIndex(Math.max(0, pageIndex - 1));
                  };
                  if (page.widgets.length === 0) dropPage();
                  else
                    Alert.alert('Remove this page?', `It has ${page.widgets.length} widgets.`, [
                      { text: 'Cancel', style: 'cancel' },
                      { text: 'Remove', style: 'destructive', onPress: dropPage },
                    ]);
                }}
              />
            </View>
            <View style={styles.pickRow}>
              <Button
                label="Preset…"
                onPress={() => {
                  setPages(false);
                  setPresets(true);
                }}
              />
              <Button
                label="Reset to default"
                onPress={() => {
                  setPages(false);
                  onReset();
                }}
              />
            </View>
            <Button label="Close" onPress={() => setPages(false)} />
          </View>
        </View>
      </Modal>
      <Modal visible={presets} transparent animationType="fade" onRequestClose={() => setPresets(false)}>
        <View style={styles.scrim}>
          <View style={styles.sheet}>
            <Text style={styles.title}>Main page preset</Text>
            <Text style={styles.hint}>Replaces the main page; controller pages stay.</Text>
            {(Object.keys(PRESET_NAMES) as PresetName[]).map((n) => (
              <Button
                key={n}
                label={PRESET_NAMES[n]}
                onPress={() => {
                  setPresets(false);
                  setSelected(null);
                  const preset = presetLayout(
                    n,
                    controllerIds.map((controllerId) => ({ controllerId })),
                  );
                  change({ ...layout, pages: [preset.pages[0]!, ...layout.pages.slice(1)] });
                  onPageIndex(0);
                }}
              />
            ))}
            <Button label="Cancel" onPress={() => setPresets(false)} />
          </View>
        </View>
      </Modal>
      <Modal visible={picker} transparent animationType="fade" onRequestClose={() => setPicker(false)}>
        <View style={styles.scrim}>
          <View style={styles.sheet}>
            <Text style={styles.title}>Add widget</Text>
            <ScrollView contentContainerStyle={styles.list}>
              {TYPES.map((t) => (
                <View key={t} style={styles.pickRow}>
                  <Text style={styles.pickName}>{WIDGET_NAMES[t]}</Text>
                  {(PER_CONTROLLER.includes(t) && multi ? sources : (['combined'] as WidgetSource[])).map(
                    (s) => (
                      <Button
                        key={sourceLabel(s)}
                        label={sourceLabel(s)}
                        onPress={() => {
                          setPicker(false);
                          apply(
                            addWidget(layout, pageIndex, t, s),
                            'No room on this page: remove or shrink a widget first',
                          );
                        }}
                      />
                    ),
                  )}
                </View>
              ))}
            </ScrollView>
            <Button label="Cancel" onPress={() => setPicker(false)} />
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[4],
    paddingHorizontal: space[4],
    paddingVertical: space[2],
  },
  title: { color: colors.textPrimary, fontSize: type.heading.size, fontFamily: fontFamily.uiSemiBold },
  hint: { color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  page: { flex: 1 },
  outline: {
    position: 'absolute',
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: colors.textTertiary,
    borderRadius: radius.md,
  },
  outlineOn: {
    borderWidth: 2,
    borderStyle: 'solid',
    borderColor: colors.accent,
    backgroundColor: colors.accentMuted,
  },
  tools: {
    position: 'absolute',
    bottom: space[3],
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    padding: space[2],
    backgroundColor: colors.bgRaised,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
  },
  remove: {
    position: 'absolute',
    top: 2,
    right: 2,
    width: HANDLE,
    height: HANDLE,
    borderRadius: HANDLE / 2,
    backgroundColor: colors.bgRaised,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeText: { color: colors.textSecondary, fontSize: type.body.size, lineHeight: type.body.size + 2 },
  corner: {
    position: 'absolute',
    right: 2,
    bottom: 2,
    width: HANDLE,
    height: HANDLE,
    borderRadius: HANDLE / 2,
    backgroundColor: colors.accent,
  },
  scrim: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', alignItems: 'center', justifyContent: 'center' },
  sheet: {
    width: '70%',
    maxHeight: '90%',
    backgroundColor: colors.bgRaised,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[4],
    gap: space[3],
  },
  list: { gap: space[2] },
  pickRow: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: space[2] },
  pickName: {
    width: 160,
    color: colors.textPrimary,
    fontSize: type.body.size,
    fontFamily: fontFamily.uiSemiBold,
  },
});
