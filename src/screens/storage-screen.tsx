// Storage: what recorded rides and speed-test runs take on the phone, and deleting old
// rides after a confirm. Nothing is deleted without asking.
import Vesc, { type RideRow } from '@modules/vesc';
import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { formatBytes, olderThanNow, usage } from '@/storage/usage';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

const AGES = [7, 30, 90];

export function StorageScreen() {
  const [rides, setRides] = useState<RideRow[] | null>(null);
  const [runs, setRuns] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const load = useCallback(() => {
    Vesc.listRides(500)
      .then(setRides)
      .catch(() => setRides([]));
    Vesc.listRuns(500)
      .then((r) => setRuns(r.length))
      .catch(() => setRuns(null));
  }, []);
  useEffect(load, [load]);

  const u = usage(rides ?? []);
  const deleteOlder = (days: number) => {
    const old = olderThanNow(rides ?? [], days);
    if (old.length === 0) {
      Alert.alert('Nothing to delete', `No rides older than ${days} days.`);
      return;
    }
    const size = formatBytes(old.reduce((a, r) => a + r.bytes, 0));
    Alert.alert(
      `Delete ${old.length} rides?`,
      `Rides older than ${days} days, ${size}. This can't be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => {
            setBusy(true);
            old
              .reduce((p, r) => p.then(() => Vesc.deleteRide(r.id)), Promise.resolve())
              .catch(() => undefined)
              .finally(() => {
                setBusy(false);
                load();
              });
          },
        },
      ],
    );
  };

  return (
    <View style={styles.screen}>
      <View style={styles.top}>
        <Button label="Back" onPress={() => router.back()} />
        <Text style={styles.title}>Storage</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.hero}>
          {rides == null ? '…' : formatBytes(u.bytes)}
          <Text style={styles.unit}> used by {u.rides} rides</Text>
        </Text>
        <Text style={styles.line}>
          {u.hours.toFixed(1)} h recorded
          {u.bytesPerHour != null ? ` · about ${formatBytes(u.bytesPerHour)} per hour of riding` : ''}
        </Text>
        <Text style={styles.line}>{runs == null ? '' : `${runs} saved speed-test runs (small)`}</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.section}>Delete old rides</Text>
        <View style={styles.buttons}>
          {AGES.map((d) => (
            <Button
              key={d}
              label={`Older than ${d} days`}
              disabled={busy || rides == null}
              onPress={() => deleteOlder(d)}
            />
          ))}
        </View>
        <Text style={styles.note}>
          Asks first and shows how many rides and how much space. Single rides are deleted in History.
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: space[4], gap: space[3] },
  top: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  card: {
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[4],
    gap: space[2],
  },
  hero: { color: colors.textPrimary, fontSize: type.numL.size, fontFamily: fontFamily.display },
  unit: { color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  line: { color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  section: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  buttons: { flexDirection: 'row', gap: space[3], flexWrap: 'wrap' },
  note: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
