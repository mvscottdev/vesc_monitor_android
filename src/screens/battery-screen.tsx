// Battery detection and confirmation: the guess, its confidence and evidence, the
// alternatives, and manual entry. Detection runs natively; this sends the choice.
import Vesc, { type BatteryInfo, type PackCandidate } from '@modules/vesc';
import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import {
  alternativeLine,
  capacityLine,
  CELLS_MAX,
  CELLS_MIN,
  CHEMISTRIES,
  confidenceLabel,
  evidenceLines,
  type EvidenceKind,
  packName,
  packShort,
  stepCapacity,
  emptyLine,
  stepEmptyCell,
} from '@/battery/evidence';
import { Button } from '@/components/button';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

type Pack = Pick<PackCandidate, 'cells' | 'chemistry'>;

function useBatteryInfo(): { info: BatteryInfo | null; loaded: boolean; reload: () => void } {
  const [info, setInfo] = useState<BatteryInfo | null>(null);
  const [loaded, setLoaded] = useState(false);
  const reload = useCallback(() => {
    Vesc.batteryInfo()
      .then((i) => {
        setInfo(i);
        setLoaded(true);
      })
      .catch(() => setLoaded(true));
  }, []);
  useEffect(() => {
    const first = setTimeout(reload, 0);
    const id = setInterval(reload, 2000);
    return () => {
      clearTimeout(first);
      clearInterval(id);
    };
  }, [reload]);
  return { info, loaded, reload };
}

/** Confidence bar fill (%): yours is full, then high / medium / low. */
function confFill(confirmed: boolean, c: number | null): number {
  if (confirmed) return 100;
  return c === 3 ? 90 : c === 2 ? 60 : c === 1 ? 30 : 0;
}

const MARK: Record<EvidenceKind, string> = { ok: '✓', info: 'i', warn: '!' };
const MARK_COLOR: Record<EvidenceKind, string> = {
  ok: colors.state.ok,
  info: colors.state.info,
  warn: colors.state.warn,
};

export function BatteryScreen() {
  const { info, loaded, reload } = useBatteryInfo();
  const [manual, setManual] = useState<Pack | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [capacity, setCapacity] = useState<number | null>(null);
  const [capError, setCapError] = useState<string | null>(null);
  const [empty, setEmpty] = useState<number | null>(null);
  const [emptyError, setEmptyError] = useState<string | null>(null);
  const saveEmpty = (v: number | null) => {
    Vesc.setEmptyCell(v)
      .then((reason) => {
        setEmptyError(reason);
        if (reason == null) setEmpty(null);
        reload();
      })
      .catch(() => setEmptyError('Could not save.'));
  };
  const saveCapacity = (ah: number | null) => {
    Vesc.setCapacity(ah)
      .then((reason) => {
        setCapError(reason);
        if (reason == null) setCapacity(null);
        reload();
      })
      .catch(() => setCapError('Could not save.'));
  };

  const save = (p: Pack | null) => {
    Vesc.setPack(p?.cells ?? null, p?.chemistry ?? null)
      .then((reason) => {
        setError(reason);
        if (reason == null) setManual(null);
        reload();
      })
      .catch(() => setError('Could not save.'));
  };

  const top = (
    <View style={styles.top}>
      <Button label="Back" onPress={() => router.back()} />
      <Text style={styles.title}>Battery</Text>
    </View>
  );

  if (!info) {
    return (
      <View style={styles.screen}>
        {top}
        <Text style={styles.note}>{loaded ? 'Connect to the vehicle to detect its battery.' : ''}</Text>
      </View>
    );
  }

  const shown: Pack | null = info.confirmed ?? info.detected;
  const low = info.confidence == null || info.confidence === 1;
  const confColor = info.confirmed
    ? colors.state.ok
    : info.confidence === 3
      ? colors.state.ok
      : info.confidence === 2
        ? colors.state.info
        : colors.state.warn;

  return (
    <View style={styles.screen}>
      {top}
      <ScrollView contentContainerStyle={styles.body}>
        <View style={styles.col}>
          <View style={styles.card}>
            <View style={styles.heroRow}>
              <Text style={styles.hero}>{shown ? `${shown.cells}S` : '––'}</Text>
              <View style={styles.heroText}>
                <Text style={styles.heroName}>
                  {shown ? (CHEMISTRIES[shown.chemistry]?.name ?? '') : 'Detecting…'}
                </Text>
                <Text style={styles.hint}>
                  {info.confirmed
                    ? 'set by you'
                    : `best match of ${info.alternatives.length + (info.detected ? 1 : 0)} candidates`}
                </Text>
              </View>
              <View style={styles.conf}>
                <View style={styles.confHead}>
                  <Text style={styles.hint}>Confidence</Text>
                  <Text style={[styles.confValue, { color: confColor }]}>
                    {info.confirmed ? 'Yours' : confidenceLabel(info.confidence)}
                  </Text>
                </View>
                <View style={styles.confTrack}>
                  <View
                    style={[
                      styles.confFill,
                      {
                        backgroundColor: confColor,
                        width: `${confFill(info.confirmed != null, info.confidence)}%`,
                      },
                    ]}
                  />
                </View>
              </View>
            </View>
            {!info.confirmed && info.confirmChemistry === 1 ? (
              <Text style={[styles.hint, { color: colors.state.warn }]}>
                Voltages fit two chemistries: please confirm
              </Text>
            ) : null}
            {shown
              ? evidenceLines(info, shown).map((l) => (
                  <View key={l.text} style={styles.row}>
                    <Text style={[styles.mark, { color: MARK_COLOR[l.kind] }]}>{MARK[l.kind]}</Text>
                    <Text style={styles.line}>{l.text}</Text>
                  </View>
                ))
              : null}
            {info.confirmed && info.detected ? (
              <Text style={styles.hint}>The app&apos;s own guess is {packShort(info.detected)}.</Text>
            ) : null}
          </View>

          <View style={styles.card}>
            <Text style={styles.section}>Capacity</Text>
            <Text style={styles.line}>{capacityLine(info)}</Text>
            {capacity != null ? (
              <>
                <View style={styles.row}>
                  <Button label="−5" onPress={() => setCapacity(stepCapacity(capacity, -5))} />
                  <Button label="−0.5" onPress={() => setCapacity(stepCapacity(capacity, -0.5))} />
                  <Text style={styles.value}>{capacity.toFixed(1)} Ah</Text>
                  <Button label="+0.5" onPress={() => setCapacity(stepCapacity(capacity, 0.5))} />
                  <Button label="+5" onPress={() => setCapacity(stepCapacity(capacity, 5))} />
                </View>
                <View style={styles.buttons}>
                  <Button
                    kind="primary"
                    label={`Use ${capacity.toFixed(1)} Ah`}
                    onPress={() => saveCapacity(capacity)}
                  />
                  <Button label="Cancel" onPress={() => setCapacity(null)} />
                </View>
              </>
            ) : (
              <View style={styles.buttons}>
                <Button
                  label="Enter capacity"
                  onPress={() => setCapacity(info.capacityAh ?? info.learnedAh ?? 10)}
                />
                {info.capacityAh != null ? (
                  <Button label="Learn from rides" onPress={() => saveCapacity(null)} />
                ) : null}
              </View>
            )}
            {capError ? <Text style={styles.error}>{capError}</Text> : null}
            <Text style={styles.hint}>
              The rated capacity of the pack (Ah) gives a range number from the first ride; otherwise the app
              learns it from two long discharges.
            </Text>
          </View>
          {emptyLine(info) ? (
            <View style={styles.card}>
              <Text style={styles.section}>Empty point</Text>
              <Text style={styles.line}>{emptyLine(info)}</Text>
              {empty != null ? (
                <>
                  <View style={styles.row}>
                    <Button label="−0.1" onPress={() => setEmpty(stepEmptyCell(empty, -0.1))} />
                    <Button label="−0.01" onPress={() => setEmpty(stepEmptyCell(empty, -0.01))} />
                    <Text style={styles.value}>{empty.toFixed(2)} V</Text>
                    <Button label="+0.01" onPress={() => setEmpty(stepEmptyCell(empty, 0.01))} />
                    <Button label="+0.1" onPress={() => setEmpty(stepEmptyCell(empty, 0.1))} />
                  </View>
                  <View style={styles.buttons}>
                    <Button
                      kind="primary"
                      label={`Use ${empty.toFixed(2)} V`}
                      onPress={() => saveEmpty(empty)}
                    />
                    <Button label="Cancel" onPress={() => setEmpty(null)} />
                  </View>
                </>
              ) : (
                <View style={styles.buttons}>
                  <Button
                    label="Change"
                    onPress={() => setEmpty(info.emptyCellV ?? info.defaultEmptyCellV ?? 3)}
                  />
                  {info.emptyCellV != null ? (
                    <Button label="Default" onPress={() => saveEmpty(null)} />
                  ) : null}
                </View>
              )}
              {emptyError ? <Text style={styles.error}>{emptyError}</Text> : null}
              <Text style={styles.hint}>
                The cell voltage the app shows as 0 %. Raise it to keep a reserve; range counts down to it.
              </Text>
            </View>
          ) : null}
          <Text style={styles.note}>
            The pack sets battery %, cell voltage and the low-battery alerts. It is remembered for this
            vehicle on this phone; nothing is written to the controller.
          </Text>
        </View>
        <View style={styles.col}>
          {!info.confirmed && info.alternatives.length > 0 ? (
            <View style={styles.card}>
              <Text style={styles.section}>OTHER POSSIBILITIES · TAP TO USE</Text>
              {info.alternatives.map((a) => (
                <Pressable
                  key={`${a.cells}-${a.chemistry}`}
                  accessibilityRole="button"
                  onPress={() => save(a)}
                  style={({ pressed }) => [styles.alt, pressed && styles.pressed]}
                >
                  <Text style={styles.altName}>{packName(a)}</Text>
                  <Text style={styles.line}>{alternativeLine(info, a)}</Text>
                </Pressable>
              ))}
            </View>
          ) : null}

          {manual ? (
            <View style={styles.card}>
              <Text style={styles.section}>Enter by hand</Text>
              <View style={styles.row}>
                <Text style={styles.label}>Cells in series</Text>
                <Button
                  label="−"
                  disabled={manual.cells <= CELLS_MIN}
                  onPress={() => setManual({ ...manual, cells: manual.cells - 1 })}
                />
                <Text style={styles.value}>{manual.cells}S</Text>
                <Button
                  label="+"
                  disabled={manual.cells >= CELLS_MAX}
                  onPress={() => setManual({ ...manual, cells: manual.cells + 1 })}
                />
              </View>
              <View style={styles.row}>
                <Text style={styles.label}>Chemistry</Text>
                {Object.entries(CHEMISTRIES).map(([code, c]) => (
                  <Button
                    key={code}
                    label={c.name}
                    kind={manual.chemistry === Number(code) ? 'primary' : 'quiet'}
                    onPress={() => setManual({ ...manual, chemistry: Number(code) })}
                  />
                ))}
              </View>
              <View style={styles.buttons}>
                <Button kind="primary" label={`Use ${packShort(manual)}`} onPress={() => save(manual)} />
                <Button label="Cancel" onPress={() => setManual(null)} />
              </View>
            </View>
          ) : (
            <View style={styles.buttons}>
              {info.confirmed ? (
                <Button label="Back to auto-detect" onPress={() => save(null)} />
              ) : info.detected ? (
                <Button
                  kind={low ? 'quiet' : 'primary'}
                  label={`Use ${packShort(info.detected)}`}
                  onPress={() => save(info.detected)}
                />
              ) : null}
              <Button
                kind={low && !info.confirmed ? 'primary' : 'quiet'}
                label="Enter by hand"
                onPress={() =>
                  setManual(
                    shown ? { cells: shown.cells, chemistry: shown.chemistry } : { cells: 13, chemistry: 1 },
                  )
                }
              />
            </View>
          )}
          {error ? <Text style={styles.error}>{error}</Text> : null}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: space[4], gap: space[3] },
  top: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  body: { flexDirection: 'row', gap: space[4], paddingBottom: space[6] },
  col: { flex: 1, gap: space[4] },
  heroRow: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  heroText: { flex: 1, gap: 2 },
  heroName: { color: colors.textPrimary, fontSize: type.heading.size, fontFamily: fontFamily.uiSemiBold },
  conf: { width: 140, gap: space[1] },
  confHead: { flexDirection: 'row', justifyContent: 'space-between' },
  confValue: { fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  confTrack: { height: 8, borderRadius: 4, backgroundColor: colors.divider, overflow: 'hidden' },
  confFill: { height: 8, borderRadius: 4 },
  card: {
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[4],
    gap: space[2],
  },
  hero: { color: colors.textPrimary, fontSize: type.numL.size, fontFamily: fontFamily.display },
  row: { flexDirection: 'row', alignItems: 'center', gap: space[3], flexWrap: 'wrap' },
  chip: {
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingHorizontal: space[2],
    paddingVertical: 2,
  },
  mark: { width: 16, textAlign: 'center', fontSize: type.body.size, fontFamily: fontFamily.uiSemiBold },
  line: { flex: 1, color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  hint: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  section: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  alt: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingVertical: space[2],
    borderBottomColor: colors.divider,
    borderBottomWidth: 1,
  },
  pressed: { opacity: 0.6 },
  altName: {
    width: 180,
    color: colors.textPrimary,
    fontSize: type.body.size,
    fontFamily: fontFamily.uiSemiBold,
  },
  label: { width: 140, color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  value: {
    minWidth: 56,
    textAlign: 'center',
    color: colors.textPrimary,
    fontSize: type.numS.size,
    fontFamily: fontFamily.display,
  },
  buttons: { flexDirection: 'row', gap: space[3], flexWrap: 'wrap' },
  error: { color: colors.state.warn, fontSize: type.body.size, fontFamily: fontFamily.ui },
  note: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
