// One recorded ride (history mockup, ride detail): summary tiles, a chart per metric
// (mean line over a min/max band) with round gridlines, a time or distance axis, a scrub
// tooltip with every metric and alert chips. Data comes downsampled natively.
import Vesc, { type RideRow, type RideSeries } from '@modules/vesc';
import { Canvas, Line, Path, vec } from '@shopify/react-native-skia';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import {
  Alert,
  type GestureResponderEvent,
  type LayoutChangeEvent,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { Button } from '@/components/button';
import { Segmented } from '@/components/segmented';
import { alertText } from '@/alerts/texts';
import { distOf, distUnit, speedOf, speedUnit, tempOf, tempUnit } from '@/settings/units';
import { useUnits } from '@/stores/units-store';
import {
  alertMarks,
  bandPaths,
  bucketAtX,
  bucketTime,
  chartScale,
  chartsFor,
  distances,
  type ChartKey,
  hasSpeed,
  niceTicks,
  rangeOf,
  readout,
  rowStats,
  xFractions,
} from '@/history/series';
import { formatDuration } from '@/lib/format';
import { rideTitle } from '@/screens/history-screen';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

const BUCKETS = 400;

const BAND_COLORS: Record<ChartKey, string[]> = {
  speed: [colors.metric.speed],
  power: [colors.metric.drive],
  voltage: [colors.metric.voltage],
  temps: [colors.metric.temp, colors.metric.duty],
  duty: [colors.metric.duty],
};

function Tile({ label, value, unit, color }: { label: string; value: string; unit: string; color?: string }) {
  return (
    <View style={styles.tile}>
      <Text style={styles.tileLabel}>{label}</Text>
      <Text style={[styles.tileValue, color != null && { color }]}>
        {value}
        <Text style={styles.tileUnit}> {unit}</Text>
      </Text>
    </View>
  );
}

export function RideDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const rideId = Number(id);
  const [ride, setRide] = useState<RideRow | null>(null);
  const [series, setSeries] = useState<RideSeries | null | undefined>(undefined);
  const [picked, setChart] = useState<ChartKey | null>(null);
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [scrub, setScrub] = useState<number | null>(null);
  const [byDistance, setByDistance] = useState(false);

  useEffect(() => {
    Vesc.listRides(500)
      .then((rows) => setRide(rows.find((r) => r.id === rideId) ?? null))
      .catch(() => undefined);
    Vesc.rideSeries(rideId, BUCKETS)
      .then(setSeries)
      .catch(() => setSeries(null));
  }, [rideId]);

  const confirmDelete = (r: RideRow) =>
    Alert.alert('Delete ride?', `${rideTitle(r)}\nIts samples are removed from the phone.`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () => void Vesc.deleteRide(r.id).then(() => router.back()),
      },
    ]);
  const withSpeed = series != null && hasSpeed(series);
  const units = useUnits();
  const charts = chartsFor(units).filter((c) => c.key !== 'speed' || withSpeed);
  const chart: ChartKey = picked ?? (withSpeed ? 'speed' : 'power');
  const spec = charts.find((c) => c.key === chart) ?? charts[0]!;
  const range = series && size.w > 0 ? rangeOf(spec.bands(series), spec.key === 'power') : null;
  const dist = series ? distances(series) : null;
  const xs = series ? xFractions(series, byDistance ? dist : null) : [];
  const paths = series && range ? spec.bands(series).map((b) => bandPaths(b, range, size.w, size.h, xs)) : [];

  const onLayout = (e: LayoutChangeEvent) =>
    setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const onTouch = (e: GestureResponderEvent) => {
    if (series) setScrub(bucketAtX(e.nativeEvent.locationX, size.w, xs));
  };
  const marks = series ? alertMarks(series) : [];
  const markX = (i: number) => (xs[i] ?? 0) * size.w;
  const scrubX = scrub != null ? markX(scrub) : null;
  const stats = series ? rowStats(series) : null;
  const controllers = ride ? ride.streams.split(',').filter((x) => x !== '').length : 0;

  // Round gridlines in display units, placed back on the stored scale.
  const scale = chartScale(spec.key, units);
  const yOf = (raw: number) => (range ? size.h - ((raw - range.lo) / (range.hi - range.lo)) * size.h : 0);
  const yTicks = range
    ? niceTicks(range.lo * scale.a + scale.b, range.hi * scale.a + scale.b, 3).map((d) => ({
        label: Number.isInteger(d) ? String(d) : d.toFixed(1),
        y: yOf((d - scale.b) / scale.a),
      }))
    : [];
  // Axis labels: minutes, or distance along the ride.
  const axisTotal =
    series == null
      ? 0
      : byDistance && dist
        ? distOf(dist[dist.length - 1] ?? 0, units)
        : ((series.buckets - 1) * series.bucketMs) / 60000;
  const xTicks = niceTicks(0, axisTotal, 6).map((v) => ({
    label: String(v),
    x: axisTotal > 0 ? (v / axisTotal) * size.w : 0,
  }));
  const axisUnit = byDistance && dist ? distUnit(units) : 'min';

  return (
    <View style={styles.screen}>
      <View style={styles.top}>
        <Button label="‹" onPress={() => router.back()} />
        <View>
          <Text style={styles.title}>{ride ? rideTitle(ride) : 'Ride'}</Text>
          {ride ? (
            <Text style={styles.sub}>
              {controllers === 1 ? '1 controller' : `${controllers} controllers`}
              {ride.state === 'recovered' ? ' · ended unexpectedly' : ''}
            </Text>
          ) : null}
        </View>
        <View style={styles.spacer} />
        {charts.length > 0 ? (
          <Segmented
            label="Chart"
            value={chart}
            options={charts.map((c) => ({ value: c.key, label: c.label }))}
            onChange={(k) => setChart(k)}
          />
        ) : null}
        {ride ? <Button label="Delete" onPress={() => confirmDelete(ride)} /> : null}
      </View>
      {ride ? (
        <View style={styles.tiles}>
          {stats?.distanceM != null ? (
            <Tile label="DISTANCE" value={distOf(stats.distanceM, units).toFixed(1)} unit={distUnit(units)} />
          ) : null}
          <Tile label="TIME" value={formatDuration(ride.durationMs)} unit="" />
          {stats?.distanceM != null && stats.maxSpeedMps != null && ride.durationMs > 0 ? (
            <Tile
              label="AVG · MAX"
              value={`${speedOf(stats.distanceM / (ride.durationMs / 1000), units).toFixed(0)} · ${speedOf(
                stats.maxSpeedMps,
                units,
              ).toFixed(0)}`}
              unit={speedUnit(units)}
            />
          ) : null}
          <Tile label="USED" value={ride.whUsed.toFixed(0)} unit="Wh" />
          <Tile label="REGEN" value={ride.whRegen.toFixed(0)} unit="Wh" color={colors.metric.regen} />
          {ride.minVoltageV != null ? (
            <Tile
              label="MIN VOLT"
              value={ride.minVoltageV.toFixed(1)}
              unit="V"
              color={colors.metric.voltage}
            />
          ) : null}
          <Tile
            label="PEAK"
            value={(ride.peakPowerW / 1000).toFixed(1)}
            unit="kW"
            color={colors.metric.drive}
          />
          {ride.maxTempMotorC != null ? (
            <Tile
              label="MOTOR"
              value={tempOf(ride.maxTempMotorC, units).toFixed(0)}
              unit={tempUnit(units)}
              color={colors.metric.temp}
            />
          ) : null}
          {ride.maxTempFetC != null ? (
            <Tile label="FET" value={tempOf(ride.maxTempFetC, units).toFixed(0)} unit={tempUnit(units)} />
          ) : null}
        </View>
      ) : null}
      <View
        style={styles.chart}
        onLayout={onLayout}
        onStartShouldSetResponder={() => true}
        onMoveShouldSetResponder={() => true}
        onResponderGrant={onTouch}
        onResponderMove={onTouch}
      >
        {series === undefined ? null : series === null ? (
          <Text style={styles.empty}>No samples stored for this ride.</Text>
        ) : (
          <Canvas style={StyleSheet.absoluteFill}>
            {yTicks.map((t) => (
              <Line
                key={`g${t.y}`}
                p1={vec(0, t.y)}
                p2={vec(size.w, t.y)}
                color={colors.divider}
                strokeWidth={1}
              />
            ))}
            {paths.map((p, i) => {
              const color = BAND_COLORS[spec.key][i] ?? colors.accent;
              return [
                <Path key={`b${i}`} path={p.band} color={color} opacity={0.22} />,
                <Path key={`l${i}`} path={p.line} color={color} style="stroke" strokeWidth={2} />,
              ];
            })}
            {marks.map((m) => (
              <Line
                key={`m${m.bucket}`}
                p1={vec(markX(m.bucket), 0)}
                p2={vec(markX(m.bucket), size.h)}
                color={m.severity === 'critical' ? colors.state.crit : colors.state.warn}
                strokeWidth={1}
                opacity={0.6}
              />
            ))}
            {scrubX != null ? (
              <Line
                p1={vec(scrubX, 0)}
                p2={vec(scrubX, size.h)}
                color={colors.textSecondary}
                strokeWidth={1}
              />
            ) : null}
          </Canvas>
        )}
        {yTicks.map((t) => (
          <Text key={`y${t.y}`} style={[styles.axis, { left: 6, top: t.y - 16 }]}>
            {t.label}
          </Text>
        ))}
        {scrub != null && series && scrubX != null ? (
          <View
            pointerEvents="none"
            style={[
              styles.tooltip,
              scrubX > size.w / 2 ? { right: size.w - scrubX + 8 } : { left: scrubX + 8 },
            ]}
          >
            <Text style={styles.tooltipText}>{readout(series, scrub, units, dist)}</Text>
          </View>
        ) : null}
      </View>
      <View style={styles.xAxis}>
        {xTicks.map((t, i) => (
          <Text key={`x${i}`} style={[styles.axis, { left: Math.max(0, t.x - 8) }]}>
            {t.label}
          </Text>
        ))}
        <Text style={[styles.axis, { right: 0 }]}>{axisUnit}</Text>
      </View>
      {marks.length > 0 ? (
        <ScrollView horizontal contentContainerStyle={styles.marks}>
          {marks.map((m) => {
            const color = m.severity === 'critical' ? colors.state.crit : colors.state.warn;
            const title = alertText({
              key: m.key,
              controllerId: null,
              severity: m.severity,
              value: 0,
              sinceMs: 0,
              notify: 0,
              cleared: 0,
            }).title;
            return (
              <Text
                key={m.bucket}
                onPress={() => setScrub(m.bucket)}
                style={[styles.mark, { color, borderColor: color }]}
              >
                {series ? bucketTime(series, m.bucket) : ''} {title}
              </Text>
            );
          })}
        </ScrollView>
      ) : null}
      <View style={styles.footer}>
        {dist ? (
          <Segmented
            label="Axis"
            value={byDistance ? 'distance' : 'time'}
            options={[
              { value: 'time', label: 'By time' },
              { value: 'distance', label: 'By distance' },
            ]}
            onChange={(v) => setByDistance(v === 'distance')}
          />
        ) : null}
        {spec.key === 'temps' ? (
          <Text style={styles.legend}>
            <Text style={{ color: colors.metric.temp }}>Motor</Text> ·{' '}
            <Text style={{ color: colors.metric.duty }}>Controller (FET)</Text> · line = average, band = min
            to max
          </Text>
        ) : (
          <Text style={styles.legend}>
            {spec.label} in {spec.unit} · line = average, band = min to max · drag across the chart
          </Text>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: space[4], gap: space[3] },
  top: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  tiles: { flexDirection: 'row', gap: space[3], flexWrap: 'wrap' },
  tile: {
    minWidth: 96,
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.sm,
    paddingHorizontal: space[3],
    paddingVertical: space[2],
  },
  tileLabel: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  tileValue: { color: colors.textPrimary, fontSize: type.numS.size, fontFamily: fontFamily.display },
  tileUnit: { color: colors.textSecondary, fontSize: type.unit.size, fontFamily: fontFamily.ui },
  sub: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  axis: {
    position: 'absolute',
    color: colors.textTertiary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
  },
  xAxis: { height: 16, marginTop: -space[2] },
  tooltip: {
    position: 'absolute',
    top: space[2],
    maxWidth: 360,
    backgroundColor: colors.bgRaised,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.sm,
    paddingHorizontal: space[3],
    paddingVertical: space[2],
  },
  tooltipText: { color: colors.textPrimary, fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  footer: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  spacer: { flex: 1 },
  marks: { gap: space[2] },
  mark: {
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingHorizontal: space[2],
    paddingVertical: 2,
  },
  chart: {
    flex: 1,
    minHeight: 120,
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
  legend: { flex: 1, color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
