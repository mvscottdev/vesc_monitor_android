// Settings (mockup settings-main): sections on the left, the chosen section on the right.
// Display & units and Ride logging are edited here; the others open their own screen.
import Vesc, { type BackgroundStatus } from '@modules/vesc';
import { router } from 'expo-router';
import { type ReactNode, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Segmented } from '@/components/segmented';
import { backgroundNeedsAttention } from '@/settings/vendors';
import { useSessionStore } from '@/stores/session-store';
import { useUnitsStore } from '@/stores/units-store';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

type Section = 'display' | 'logging';

type Entry =
  | { key: Section; label: string }
  | { key: string; label: string; route: '/storage' | '/background' | '/source' | '/bench' };

const SECTIONS: Entry[] = [
  { key: 'display', label: 'Display & units' },
  { key: 'logging', label: 'Ride logging' },
  { key: 'storage', label: 'Storage', route: '/storage' },
  { key: 'background', label: 'Run in background', route: '/background' },
  { key: 'source', label: 'Data source', route: '/source' },
  { key: 'bench', label: 'Developer', route: '/bench' },
];

function Row({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  return (
    <View style={styles.row}>
      <View style={styles.rowText}>
        <Text style={styles.rowTitle}>{title}</Text>
        {sub ? <Text style={styles.rowSub}>{sub}</Text> : null}
      </View>
      {children}
    </View>
  );
}

function DisplayPanel() {
  const units = useUnitsStore((s) => s.units);
  const setUnits = useUnitsStore((s) => s.setUnits);
  return (
    <View style={styles.panel}>
      <Row title="Speed and distance">
        <Segmented
          label="Speed and distance"
          value={units.speed}
          options={[
            { value: 'kmh', label: 'km/h' },
            { value: 'mph', label: 'mph' },
          ]}
          onChange={(speed) => setUnits({ ...units, speed })}
        />
      </Row>
      <Row title="Temperature">
        <Segmented
          label="Temperature"
          value={units.temp}
          options={[
            { value: 'c', label: '°C' },
            { value: 'f', label: '°F' },
          ]}
          onChange={(temp) => setUnits({ ...units, temp })}
        />
      </Row>
      <Text style={styles.note}>
        Units apply to the dashboard, history, ride charts, the speed test, alert banners and notifications.
        In mph the speed test times 0-20, 0-30 and 0-40 mph from the next run. Alert thresholds stay in °C.
      </Text>
    </View>
  );
}

function LoggingPanel() {
  const logging = useSessionStore((s) => s.session?.logging ?? true);
  const low = useSessionStore((s) => s.session?.storageLow ?? false);
  return (
    <View style={styles.panel}>
      <Row
        title="Record rides"
        sub="A ride starts when the vehicle moves and ends after 10 minutes still; the REC pill toggles it too"
      >
        <Switch
          accessibilityLabel="Record rides"
          value={logging}
          onValueChange={(on) => void Vesc.setLogging(on)}
          trackColor={{ true: colors.accent, false: colors.divider }}
        />
      </Row>
      {low ? (
        <Text style={[styles.note, { color: colors.state.warn }]}>
          Phone storage is almost full: rides are paused until space is freed (Storage › delete old rides).
        </Text>
      ) : null}
      <Text style={styles.note}>Speed-test runs are saved whether or not rides are recorded.</Text>
    </View>
  );
}

export function SettingsScreen() {
  const [section, setSection] = useState<Section>('display');
  const [bg, setBg] = useState<BackgroundStatus | null>(null);
  useEffect(() => {
    Vesc.backgroundStatus()
      .then(setBg)
      .catch(() => undefined);
  }, []);

  return (
    <View style={styles.screen}>
      <View style={styles.top}>
        <Button label="Back" onPress={() => router.back()} />
        <Text style={styles.title}>Settings</Text>
      </View>
      <View style={styles.body}>
        <ScrollView style={styles.list} contentContainerStyle={styles.listInner}>
          {SECTIONS.map((e) => {
            const on = !('route' in e) && e.key === section;
            const warn = e.key === 'background' && backgroundNeedsAttention(bg);
            return (
              <Pressable
                key={e.key}
                accessibilityRole="button"
                accessibilityState={{ selected: on }}
                onPress={() => ('route' in e ? router.push(e.route) : setSection(e.key))}
                style={({ pressed }) => [styles.item, on && styles.itemOn, pressed && styles.pressed]}
              >
                <Text style={[styles.itemText, on && styles.itemTextOn]}>{e.label}</Text>
                {warn ? <View style={styles.dot} accessibilityLabel="Needs attention" /> : null}
                {'route' in e ? <Text style={styles.chevron}>›</Text> : null}
              </Pressable>
            );
          })}
        </ScrollView>
        <ScrollView style={styles.detail}>
          {section === 'display' ? <DisplayPanel /> : <LoggingPanel />}
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: space[4], gap: space[3] },
  top: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  body: { flex: 1, flexDirection: 'row', gap: space[4] },
  list: {
    flexGrow: 0,
    width: 200,
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
  },
  listInner: { paddingVertical: space[1] },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 44,
    paddingHorizontal: space[4],
    gap: space[2],
  },
  itemOn: { backgroundColor: colors.accentMuted },
  pressed: { opacity: 0.6 },
  itemText: { flex: 1, color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  itemTextOn: { color: colors.accent, fontFamily: fontFamily.uiSemiBold },
  chevron: { color: colors.textTertiary, fontSize: type.heading.size },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.state.warn },
  detail: { flex: 1 },
  panel: {
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    paddingHorizontal: space[4],
    paddingVertical: space[2],
    gap: space[1],
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    minHeight: 56,
    borderBottomColor: colors.divider,
    borderBottomWidth: 1,
  },
  rowText: { flex: 1, gap: 2 },
  rowTitle: { color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  rowSub: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  note: {
    color: colors.textTertiary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.ui,
    paddingVertical: space[2],
  },
});
