// Speed test: arm, stop fully, launch. Times come from the native run timer (motor
// speed at sample time); this screen only renders the run state and sends intents.
import Vesc, { type RunState } from '@modules/vesc';
import { useKeepAwake } from 'expo-keep-awake';
import { router } from 'expo-router';
import { Canvas, Line, Path, vec } from '@shopify/react-native-skia';
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  FlatList,
  type GestureResponderEvent,
  type LayoutChangeEvent,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { Button } from '@/components/button';
import {
  bestBracket,
  curvePaths,
  formatRunTime,
  isBest,
  headline,
  liveElapsedMs,
  parseRun,
  speedAt,
  phaseHint,
  phaseText,
  runSplits,
  type SavedRun,
  toggleCompare,
} from '@/speed-test/run';
import { useSessionStore } from '@/stores/session-store';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, touch, type } from '@/theme/tokens';
import { speedOf, speedUnit, tempOf, tempUnit, type Units } from '@/settings/units';
import { useUnits } from '@/stores/units-store';

type LiveBits = { kmh: number | null; socPct: number | null; voltageV: number | null; motorC: number | null };

const LIVE_EMPTY: LiveBits = { kmh: null, socPct: null, voltageV: null, motorC: null };

/** Live speed, battery and motor temperature at about 5 Hz (enough for this screen). */
function useLiveBits(u: Units): LiveBits {
  const [bits, setBits] = useState<LiveBits>(LIVE_EMPTY);
  useEffect(() => {
    let last = 0;
    const sub = Vesc.addListener('telemetry', (f) => {
      const now = f.tMs ?? 0;
      if (now - last < 200 && now >= last) return;
      last = now;
      const c = f.combined;
      const v = c.fresh > 0 ? c.speedMps : null;
      setBits({
        kmh: v == null ? null : Math.round(speedOf(Math.abs(v), u)),
        socPct: c.socPct ?? null,
        voltageV: c.voltageV ?? null,
        motorC: c.tempMotorC ?? null,
      });
    });
    return () => sub.remove();
  }, [u]);
  return bits;
}

/** Redraws at display rate while the run is live, extrapolating from the last event. */
function useRunClock(run: RunState | null): number | null {
  const [shown, setShown] = useState<number | null>(null);
  useEffect(() => {
    if (run?.state !== 'running') return undefined;
    const at = Date.now();
    let id = requestAnimationFrame(function loop() {
      setShown(liveElapsedMs(run, at, Date.now()));
      id = requestAnimationFrame(loop);
    });
    return () => cancelAnimationFrame(id);
  }, [run]);
  return run?.state === 'running' ? (shown ?? run.elapsedMs) : null;
}

function useSavedRuns(state: string | undefined): { runs: SavedRun[]; reload: () => void } {
  const [runs, setRuns] = useState<SavedRun[]>([]);
  const reload = useCallback(() => {
    Vesc.listRuns(50)
      .then((rows) => setRuns(rows.map(parseRun).filter((r): r is SavedRun => r != null)))
      .catch(() => undefined);
  }, []);
  // Reload on open, and a few times after a run finishes (the native side saves it in the background).
  useEffect(() => {
    const delays = state === 'done' ? [800, 2500, 6000] : [0];
    const ids = delays.map((d) => setTimeout(reload, d));
    return () => ids.forEach(clearTimeout);
  }, [state, reload]);
  return { runs, reload };
}

function runTitle(r: SavedRun): string {
  const d = new Date(r.startWallMs);
  return `${d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })} ${d.toLocaleTimeString(
    undefined,
    {
      hour: '2-digit',
      minute: '2-digit',
    },
  )}`;
}

/** Overlay colours in fixed order: accent, drive, voltage, regen. */
const COMPARE_COLORS = [colors.accent, colors.metric.drive, colors.metric.voltage, colors.metric.regen];

function CompareChart({ runs }: { runs: SavedRun[] }) {
  const units = useUnits();
  const [size, setSize] = useState({ w: 0, h: 0 });
  const onLayout = (e: LayoutChangeEvent) =>
    setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const { paths, maxMs, maxKmh } = curvePaths(runs, size.w, size.h);
  const [scrubMs, setScrubMs] = useState<number | null>(null);
  const onTouch = (e: GestureResponderEvent) => {
    if (size.w > 0 && maxMs > 0)
      setScrubMs(Math.max(0, Math.min(maxMs, (e.nativeEvent.locationX / size.w) * maxMs)));
  };
  const scrubX = scrubMs != null && maxMs > 0 ? (scrubMs / maxMs) * size.w : null;
  return (
    <View style={styles.compare}>
      <View
        style={styles.chart}
        onLayout={onLayout}
        onStartShouldSetResponder={() => true}
        onMoveShouldSetResponder={() => true}
        onResponderGrant={onTouch}
        onResponderMove={onTouch}
      >
        {size.w > 0 && (
          <Canvas style={StyleSheet.absoluteFill}>
            {scrubX != null ? (
              <Line
                p1={vec(scrubX, 0)}
                p2={vec(scrubX, size.h)}
                color={colors.textSecondary}
                strokeWidth={1}
              />
            ) : null}
            {paths.map((p, i) => (
              <Path
                key={runs[i]!.id}
                path={p}
                color={COMPARE_COLORS[i] ?? colors.textPrimary}
                style="stroke"
                strokeWidth={2.5}
              />
            ))}
          </Canvas>
        )}
      </View>
      <Text style={styles.facts}>
        {scrubMs != null
          ? `At ${(scrubMs / 1000).toFixed(2)} s`
          : `Speed 0 to ${speedOf(maxKmh / 3.6, units).toFixed(0)} ${speedUnit(units)} over ${(maxMs / 1000).toFixed(1)} s · drag across the chart`}
      </Text>
      {runs.map((r, i) => {
        const at = scrubMs != null ? speedAt(r.curve, scrubMs) : null;
        return (
          <View key={r.id} style={styles.row}>
            <Text style={[styles.label, { color: COMPARE_COLORS[i] }]}>{runTitle(r)}</Text>
            <Text style={styles.numDim}>
              {scrubMs != null
                ? at != null
                  ? `${speedOf(at / 3.6, units).toFixed(1)} ${speedUnit(units)}`
                  : '—'
                : (bestBracket(r) ?? '—')}
            </Text>
          </View>
        );
      })}
    </View>
  );
}

function Stat({ label, value, unit, color }: { label: string; value: string; unit: string; color: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.statLabel}>{label}</Text>
      <Text style={[styles.statValue, { color }]}>
        {value}
        <Text style={styles.unit}> {unit}</Text>
      </Text>
    </View>
  );
}

/** The finished run: headline time, every bracket, its speed curve and the run's extremes. */
function ResultView({ run, saved, units }: { run: RunState; saved: SavedRun[]; units: Units }) {
  const main = headline(run);
  const latest = saved[0];
  const best = latest != null && main != null && isBest(latest, saved, main.label);
  return (
    <View style={styles.body}>
      <View style={styles.leftCol}>
        <View style={styles.card}>
          <Text style={styles.statLabel}>{main ? main.label.toUpperCase() : 'NO BRACKET REACHED'}</Text>
          <View style={styles.headRow}>
            <Text style={styles.bigTime}>{main ? ((main.rolloutMs ?? 0) / 1000).toFixed(2) : '—'}</Text>
            <Text style={styles.unit}>s</Text>
            <View style={styles.spacer} />
            {best ? <Text style={styles.bestChip}>Best</Text> : null}
          </View>
        </View>
        <View style={styles.card}>
          {run.brackets.map((b) => (
            <View key={b.label} style={styles.bracketRow}>
              <Text style={[styles.label, b.rolloutMs == null && styles.dim]}>{b.label}</Text>
              {b.rolloutMs != null ? (
                <Text style={styles.num}>
                  {(b.rolloutMs / 1000).toFixed(2)}
                  <Text style={styles.unit}> s</Text>
                </Text>
              ) : (
                <Text style={styles.dimText}>Not reached</Text>
              )}
            </View>
          ))}
          <Text style={styles.facts}>
            {[
              'times from the 1 ft rollout',
              `${run.sampleRateHz.toFixed(0)} Hz`,
              run.slip === 1 ? 'wheel slip' : null,
              run.startEstimated === 1 ? 'start estimated' : null,
            ]
              .filter(Boolean)
              .join(' · ')}
          </Text>
        </View>
      </View>
      <View style={styles.rightCol}>
        {latest ? <CompareChart runs={[latest]} /> : <View style={styles.compare} />}
        <View style={styles.stats}>
          <Stat
            label="PEAK SPEED"
            value={speedOf(run.peakSpeedMps, units).toFixed(0)}
            unit={speedUnit(units)}
            color={colors.textPrimary}
          />
          <Stat
            label="PEAK POWER"
            value={(run.peakPowerW / 1000).toFixed(2)}
            unit="kW"
            color={colors.metric.drive}
          />
          {run.minVoltageV != null ? (
            <Stat
              label="MIN VOLTAGE"
              value={run.minVoltageV.toFixed(1)}
              unit="V"
              color={colors.metric.voltage}
            />
          ) : null}
          {run.maxTempMotorC != null ? (
            <Stat
              label="MOTOR"
              value={tempOf(run.maxTempMotorC, units).toFixed(0)}
              unit={tempUnit(units)}
              color={colors.metric.temp}
            />
          ) : null}
        </View>
      </View>
    </View>
  );
}

/** Before and during a run: the phase, what to do, the brackets, live speed and the big button. */
function LiveView({
  run,
  live,
  units,
  timer,
  reason,
  onArm,
}: {
  run: RunState | null;
  live: LiveBits;
  units: Units;
  timer: number | null;
  reason: string | null;
  onArm: () => void;
}) {
  const state = run?.state;
  const armed = state === 'armed' || state === 'staged' || state === 'running';
  const big =
    state === 'running'
      ? formatRunTime(timer)
      : state === 'staged'
        ? 'READY'
        : state === 'armed'
          ? 'ARMED'
          : state === 'aborted'
            ? 'ABORTED'
            : 'SPEED TEST';
  const bigColor =
    state === 'staged'
      ? colors.state.ok
      : state === 'aborted'
        ? colors.state.warn
        : state === 'running'
          ? colors.textPrimary
          : colors.accent;
  const main = headline(run);
  return (
    <View style={styles.body}>
      <View style={styles.liveLeft}>
        <Text style={[styles.phaseBig, { color: bigColor }]} numberOfLines={1} adjustsFontSizeToFit>
          {big}
        </Text>
        <Text style={styles.lead}>{state === 'aborted' ? phaseText(run) : phaseHint(run)}</Text>
        {reason ? <Text style={styles.reason}>{reason}</Text> : null}
        <View style={styles.chips}>
          {(run?.brackets ?? []).map((b) => (
            <Text key={b.label} style={[styles.chip, b.label === main?.label && styles.chipOn]}>
              {b.rolloutMs != null ? `${b.label} ${formatRunTime(b.rolloutMs)}` : b.label}
            </Text>
          ))}
        </View>
      </View>
      <View style={styles.liveRight}>
        <View style={[styles.card, styles.nowCard]}>
          <Text style={styles.statLabel}>NOW</Text>
          <Text style={styles.nowValue} numberOfLines={1} adjustsFontSizeToFit>
            {live.kmh == null ? '––' : live.kmh}
            <Text style={styles.unit}> {speedUnit(units)}</Text>
          </Text>
        </View>
        <View style={styles.card}>
          <View style={styles.bracketRow}>
            <Text style={styles.label}>Battery</Text>
            <Text style={styles.value}>
              {live.socPct != null ? `${live.socPct.toFixed(0)} % · ` : ''}
              {live.voltageV != null ? `${live.voltageV.toFixed(1)} V` : '—'}
            </Text>
          </View>
          <View style={styles.bracketRow}>
            <Text style={styles.label}>Motor</Text>
            <Text style={styles.value}>
              {live.motorC != null ? `${tempOf(live.motorC, units).toFixed(0)} ${tempUnit(units)}` : '—'}
            </Text>
          </View>
        </View>
        <Pressable
          accessibilityRole="button"
          onPress={armed ? () => void Vesc.cancelRun() : onArm}
          style={({ pressed }) => [
            styles.bigButton,
            !armed && styles.bigButtonArm,
            pressed && styles.pressed,
          ]}
        >
          <Text style={[styles.bigButtonText, !armed && styles.bigButtonTextArm]}>
            {armed ? 'Disarm' : state === 'aborted' ? 'Arm again' : 'Arm'}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

export function SpeedTestScreen() {
  useKeepAwake();
  const session = useSessionStore((s) => s.session);
  const run = session?.run ?? null;
  const units = useUnits();
  const live = useLiveBits(units);
  const [reason, setReason] = useState<string | null>(null);
  const [showRuns, setShowRuns] = useState(false);
  const saved = useSavedRuns(run?.state);
  const [picked, setSelected] = useState<number[]>([]);
  // Deleted runs drop out of the selection.
  const selected = picked.filter((id) => saved.runs.some((r) => r.id === id));
  const [comparing, setComparing] = useState(false);
  const confirmDelete = (r: SavedRun) =>
    Alert.alert('Delete run?', `${runTitle(r)} · ${runSplits(r)}`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () => void Vesc.deleteRun(r.id).then(saved.reload),
      },
    ]);
  const running = run?.state === 'running';
  const timer = useRunClock(run);
  const armed = run?.state === 'armed' || run?.state === 'staged' || running;
  const done = run?.state === 'done';

  const arm = () => {
    setReason(null);
    setShowRuns(false);
    Vesc.armRun()
      .then(setReason)
      .catch((e: unknown) => setReason(e instanceof Error ? e.message : String(e)));
  };

  return (
    <View
      style={[
        styles.screen,
        armed && { borderColor: running ? colors.metric.drive : colors.accent, borderWidth: 3 },
      ]}
    >
      <View style={styles.top}>
        <Button label="‹" onPress={() => router.back()} />
        <View style={styles.titleBox}>
          <Text style={styles.title}>{showRuns ? 'Past runs' : done ? 'Run result' : 'Speed test'}</Text>
          {done && !showRuns && saved.runs[0] ? (
            <Text style={styles.sub}>{runTitle(saved.runs[0])}</Text>
          ) : null}
        </View>
        <View style={styles.spacer} />
        {!armed && showRuns && selected.length > 0 ? (
          <Button
            label={comparing ? 'List' : `Compare (${selected.length})`}
            onPress={() => setComparing((v) => !v)}
          />
        ) : null}
        {!armed ? (
          <Button
            label={showRuns ? 'Back to test' : `Past runs (${saved.runs.length})`}
            onPress={() => {
              setShowRuns((v) => !v);
              setComparing(false);
            }}
          />
        ) : null}
        {done && !showRuns ? <Button kind="primary" label="Arm again" onPress={arm} /> : null}
      </View>
      {showRuns && !armed ? (
        comparing ? (
          <CompareChart runs={saved.runs.filter((r) => selected.includes(r.id))} />
        ) : (
          <View style={styles.splits}>
            <FlatList
              data={saved.runs}
              keyExtractor={(r) => String(r.id)}
              ListHeaderComponent={
                saved.runs.length > 0 ? (
                  <Text style={styles.facts}>Tap runs to compare (up to 4) · long-press to delete</Text>
                ) : null
              }
              ListEmptyComponent={
                <Text style={styles.facts}>No saved runs yet. Finished runs are kept here.</Text>
              }
              renderItem={({ item }) => {
                const h = headline(item);
                return (
                  <Pressable
                    onPress={() => setSelected(toggleCompare(selected, item.id))}
                    onLongPress={() => confirmDelete(item)}
                    style={[styles.runRow, selected.includes(item.id) && styles.runRowOn]}
                  >
                    <View style={styles.row}>
                      <Text style={styles.label}>{runTitle(item)}</Text>
                      {h && isBest(item, saved.runs, h.label) ? (
                        <Text style={styles.best}>Best {h.label}</Text>
                      ) : null}
                    </View>
                    <Text style={styles.facts}>{runSplits(item)}</Text>
                  </Pressable>
                );
              }}
            />
          </View>
        )
      ) : done && run ? (
        <ResultView run={run} saved={saved.runs} units={units} />
      ) : (
        <LiveView run={run} live={live} units={units} timer={timer} reason={reason} onArm={arm} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: space[4], gap: space[3] },
  titleBox: { gap: 2 },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  sub: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  spacer: { flex: 1 },
  leftCol: { width: 300, gap: space[3] },
  rightCol: { flex: 1, gap: space[3] },
  card: {
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[4],
    gap: space[2],
  },
  headRow: { flexDirection: 'row', alignItems: 'baseline', gap: space[1] },
  bigTime: { color: colors.textPrimary, fontSize: type.hero.size, fontFamily: fontFamily.display },
  bestChip: {
    color: colors.state.ok,
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    backgroundColor: colors.accentMuted,
    borderRadius: radius.pill,
    paddingHorizontal: space[3],
    paddingVertical: space[1],
  },
  bracketRow: { flexDirection: 'row', alignItems: 'baseline', minHeight: 32 },
  dim: { color: colors.textTertiary },
  dimText: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  value: { color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.uiSemiBold },
  stats: { flexDirection: 'row', gap: space[3] },
  stat: {
    flex: 1,
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[3],
  },
  statLabel: {
    color: colors.textTertiary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    letterSpacing: 1,
  },
  statValue: { fontSize: type.numS.size, fontFamily: fontFamily.display },
  liveLeft: { flex: 1, justifyContent: 'center', gap: space[3] },
  // The NOW card takes what is left, so the Arm button always stays on screen.
  liveRight: { width: 360, gap: space[3] },
  nowCard: { flex: 1, minHeight: 0, justifyContent: 'center' },
  phaseBig: { fontSize: type.hero.size, fontFamily: fontFamily.display },
  lead: { color: colors.textPrimary, fontSize: type.heading.size, fontFamily: fontFamily.ui, maxWidth: 460 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  chip: {
    color: colors.textSecondary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingHorizontal: space[3],
    paddingVertical: space[1],
  },
  chipOn: { color: colors.accent, borderColor: colors.accent },
  nowValue: { color: colors.metric.speed, fontSize: type.hero.size, fontFamily: fontFamily.display },
  bigButton: {
    minHeight: touch.rideMinTarget,
    borderRadius: radius.lg,
    backgroundColor: colors.glassFillStrong,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bigButtonArm: { backgroundColor: colors.accent, borderColor: colors.accent },
  bigButtonText: {
    color: colors.textPrimary,
    fontSize: type.heading.size,
    fontFamily: fontFamily.uiSemiBold,
  },
  bigButtonTextArm: { color: colors.textOnAccent },
  pressed: { opacity: 0.7 },
  top: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  body: { flex: 1, flexDirection: 'row', gap: space[6] },
  unit: { color: colors.textSecondary, fontSize: type.unit.size, fontFamily: fontFamily.ui },
  reason: { color: colors.state.warn, fontSize: type.body.size, fontFamily: fontFamily.ui, maxWidth: 420 },
  splits: {
    flex: 1,
    alignSelf: 'center',
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[4],
    gap: space[2],
  },
  row: { flexDirection: 'row', alignItems: 'baseline' },
  head: { flex: 1, color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  headNum: {
    flex: 1,
    color: colors.textTertiary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.ui,
    textAlign: 'right',
  },
  label: {
    flex: 1,
    color: colors.textSecondary,
    fontSize: type.body.size,
    fontFamily: fontFamily.uiSemiBold,
  },
  num: {
    flex: 1,
    color: colors.textPrimary,
    fontSize: type.numS.size,
    fontFamily: fontFamily.display,
    textAlign: 'right',
  },
  numDim: {
    flex: 1,
    color: colors.textSecondary,
    fontSize: type.body.size,
    fontFamily: fontFamily.display,
    textAlign: 'right',
  },
  runRowOn: { backgroundColor: colors.accentMuted },
  best: { color: colors.state.ok, fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  compare: {
    flex: 1,
    alignSelf: 'stretch',
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[4],
    gap: space[2],
  },
  chart: { flex: 1, minHeight: 100 },
  runRow: { paddingVertical: space[2], borderBottomColor: colors.divider, borderBottomWidth: 1 },
  facts: {
    color: colors.textSecondary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.ui,
    marginTop: space[2],
  },
});
