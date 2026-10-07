// Recorded rides, newest first, as the history mockup: speed sparkline, distance, time,
// max speed, Wh/km and alert count per ride. Long-press deletes after a confirm.
import Vesc, { type RideRow } from '@modules/vesc';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Alert, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { Canvas, Path } from '@shopify/react-native-skia';

import { Button } from '@/components/button';
import { Segmented } from '@/components/segmented';
import { rideAge, rowStats, type RowStats, sparkPath } from '@/history/series';
import { formatDuration } from '@/lib/format';
import { distOf, distUnit, perDistOf, speedOf, speedUnit, type Units } from '@/settings/units';
import { formatBytes, usage, wallNow } from '@/storage/usage';
import { useUnits } from '@/stores/units-store';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

const LIMIT = 200;

export function rideTitle(r: RideRow): string {
  const d = new Date(r.startWallMs);
  const date = d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
  const time = d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  return `${date} ${time} · ${formatDuration(r.durationMs)}`;
}

function rowTitle(r: RideRow, nowMs: number): string {
  const d = new Date(r.startWallMs);
  const time = d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  if (rideAge(r.startWallMs, nowMs) === 'Today') return `Today ${time}`;
  const date = d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
  return `${date} ${time}`;
}

/** Speed sparkline, distance, max speed and alert count of one ride, loaded when its row shows. */
function useRowStats(id: number): RowStats | null {
  const [stats, setStats] = useState<RowStats | null>(null);
  useEffect(() => {
    let live = true;
    Vesc.rideSeries(id, SPARK_BUCKETS)
      .then((s) => live && s && setStats(rowStats(s)))
      .catch(() => undefined);
    return () => {
      live = false;
    };
  }, [id]);
  return stats;
}

function Num({ value, unit, width }: { value: string; unit?: string; width: number }) {
  return (
    <Text style={[styles.num, { width }]} numberOfLines={1}>
      {value}
      {unit ? <Text style={styles.numUnit}> {unit}</Text> : null}
    </Text>
  );
}

function RideListRow({
  item,
  units,
  nowMs,
  onDelete,
}: {
  item: RideRow;
  units: Units;
  nowMs: number;
  onDelete: (r: RideRow) => void;
}) {
  const stats = useRowStats(item.id);
  const km = stats?.distanceM != null ? stats.distanceM / 1000 : null;
  const net = item.whUsed - item.whRegen;
  const sub =
    item.state === 'recovered'
      ? 'Ended unexpectedly'
      : item.state === 'recording'
        ? 'Recording'
        : rideAge(item.startWallMs, nowMs);
  const spark = stats ? sparkPath(stats.spark, SPARK_W, SPARK_H) : '';
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityHint="Long-press to delete"
      onPress={() => router.push({ pathname: '/ride', params: { id: String(item.id) } })}
      onLongPress={() => onDelete(item)}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}
    >
      <View style={styles.colRide}>
        <Text style={styles.rowTitle}>{rowTitle(item, nowMs)}</Text>
        <Text style={[styles.rowSub, item.state === 'recovered' && styles.warn]}>{sub}</Text>
      </View>
      <View style={styles.colSpark}>
        {spark !== '' ? (
          <Canvas style={styles.spark}>
            <Path
              path={spark}
              color={stats?.sparkIsSpeed ? colors.accent : colors.metric.drive}
              style="stroke"
              strokeWidth={1.5}
            />
          </Canvas>
        ) : null}
      </View>
      <Num
        value={km == null ? '—' : distOf(km * 1000, units).toFixed(1)}
        unit={km == null ? undefined : distUnit(units)}
        width={COL.distance}
      />
      <Num value={formatDuration(item.durationMs)} width={COL.time} />
      <Num
        value={stats?.maxSpeedMps != null ? speedOf(stats.maxSpeedMps, units).toFixed(0) : '—'}
        unit={stats?.maxSpeedMps != null ? speedUnit(units) : undefined}
        width={COL.max}
      />
      <Num value={km != null && km >= 0.5 ? perDistOf(net / km, units).toFixed(1) : '—'} width={COL.whkm} />
      <View style={styles.badges}>
        {stats && stats.alerts > 0 ? (
          <Text style={[styles.badge, styles.badgeWarn]}>⚠ {stats.alerts}</Text>
        ) : null}
        {item.faults !== '' ? <Text style={[styles.badge, styles.badgeCrit]}>Fault</Text> : null}
      </View>
    </Pressable>
  );
}

export function HistoryScreen() {
  const units = useUnits();
  const [rides, setRides] = useState<RideRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [nowMs, setNowMs] = useState(0);

  const load = useCallback(() => {
    Vesc.listRides(LIMIT)
      .then((rows) => {
        setNowMs(wallNow());
        setRides(rows);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  }, []);
  // Reload on every visit: a ride may have been deleted in its detail screen or finished meanwhile.
  useFocusEffect(load);

  const confirmDelete = (r: RideRow) =>
    Alert.alert('Delete ride?', `${rideTitle(r)}\nIts samples are removed from the phone.`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => void Vesc.deleteRide(r.id).then(load) },
    ]);
  const used = rides ? usage(rides).bytes : null;

  return (
    <View style={styles.screen}>
      <View style={styles.head}>
        <Button label="Back" onPress={() => router.back()} />
        <Text style={styles.title}>History</Text>
        <Segmented
          label="Show"
          value="rides"
          options={[
            { value: 'rides', label: 'Rides' },
            { value: 'runs', label: 'Speed runs' },
          ]}
          onChange={(v) => v === 'runs' && router.push('/speed-test')}
        />
        <View style={styles.spacer} />
        <Pressable accessibilityRole="button" onPress={() => router.push('/storage')} style={styles.storage}>
          <Text style={styles.storageText}>{used == null ? 'Storage' : `${formatBytes(used)} used`}</Text>
        </Pressable>
      </View>
      {error && <Text style={styles.warn}>{error}</Text>}
      <View style={styles.headerRow}>
        <Text style={[styles.colHead, styles.colRide]}>RIDE</Text>
        <Text style={[styles.colHead, styles.colSpark]}>SPEED</Text>
        <Text style={[styles.colHead, { width: COL.distance }]}>DISTANCE</Text>
        <Text style={[styles.colHead, { width: COL.time }]}>TIME</Text>
        <Text style={[styles.colHead, { width: COL.max }]}>MAX</Text>
        <Text style={[styles.colHead, { width: COL.whkm }]}>WH/{distUnit(units).toUpperCase()}</Text>
        <View style={styles.badges} />
      </View>
      <FlatList
        data={rides ?? []}
        keyExtractor={(r) => String(r.id)}
        contentContainerStyle={styles.table}
        ListEmptyComponent={
          <Text style={styles.empty}>
            {rides == null ? 'Loading…' : 'No rides yet. Rides record while REC is on.'}
          </Text>
        }
        renderItem={({ item }) => (
          <RideListRow item={item} units={units} nowMs={nowMs} onDelete={confirmDelete} />
        )}
      />
      <Text style={styles.hint}>Tap a ride for its charts; long-press to delete it.</Text>
    </View>
  );
}

const SPARK_BUCKETS = 40;
const SPARK_W = 110;
const SPARK_H = 26;
const COL = { distance: 96, time: 92, max: 84, whkm: 72 } as const;

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: space[4], gap: space[3] },
  head: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  spacer: { flex: 1 },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  storage: {
    borderRadius: radius.pill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    paddingHorizontal: space[3],
    paddingVertical: space[1],
  },
  storageText: {
    color: colors.textSecondary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
  },
  headerRow: { flexDirection: 'row', alignItems: 'center', gap: space[3], paddingHorizontal: space[3] },
  colHead: {
    color: colors.textTertiary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    letterSpacing: 1,
  },
  table: {
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    overflow: 'hidden',
  },
  empty: {
    color: colors.textTertiary,
    fontSize: type.body.size,
    fontFamily: fontFamily.ui,
    padding: space[4],
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingHorizontal: space[3],
    minHeight: 54,
    borderBottomColor: colors.divider,
    borderBottomWidth: 1,
  },
  pressed: { opacity: 0.7 },
  colRide: { width: 150, gap: 2 },
  colSpark: { width: SPARK_W },
  spark: { width: SPARK_W, height: SPARK_H },
  rowTitle: { color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.uiSemiBold },
  rowSub: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  num: { color: colors.textPrimary, fontSize: type.heading.size, fontFamily: fontFamily.display },
  numUnit: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  badges: { flex: 1, flexDirection: 'row', justifyContent: 'flex-end', gap: space[2] },
  badge: {
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingHorizontal: space[2],
    paddingVertical: 1,
  },
  badgeWarn: { color: colors.state.warn, borderColor: colors.state.warn },
  badgeCrit: { color: colors.state.crit, borderColor: colors.state.crit },
  hint: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  warn: { color: colors.state.warn, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
