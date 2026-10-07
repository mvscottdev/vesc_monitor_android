// Run in background: live checks of what Android may use to stop the connection while
// the screen is off, re-run when the rider returns from system settings.
import Vesc, { type BackgroundStatus } from '@modules/vesc';
import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { AppState, Linking, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { vendorSteps } from '@/settings/vendors';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

function Check({
  title,
  ok,
  okText,
  badText,
  action,
}: {
  title: string;
  ok: boolean | null;
  okText: string;
  badText: string;
  action?: { label: string; onPress: () => void };
}) {
  const color = ok == null ? colors.textTertiary : ok ? colors.state.ok : colors.state.warn;
  return (
    <View style={styles.row}>
      <Text style={[styles.mark, { color }]}>{ok == null ? '·' : ok ? '✓' : '!'}</Text>
      <View style={styles.text}>
        <Text style={styles.name}>{title}</Text>
        <Text style={[styles.detail, ok === false && { color: colors.state.warn }]}>
          {ok == null ? 'Checking…' : ok ? okText : badText}
        </Text>
      </View>
      {ok === false && action ? <Button label={action.label} onPress={action.onPress} /> : null}
    </View>
  );
}

export function BackgroundScreen() {
  const [status, setStatus] = useState<BackgroundStatus | null>(null);
  const check = useCallback(() => {
    Vesc.backgroundStatus()
      .then(setStatus)
      .catch(() => undefined);
  }, []);
  useEffect(() => {
    const first = setTimeout(check, 0);
    const sub = AppState.addEventListener('change', (s) => s === 'active' && check());
    return () => {
      clearTimeout(first);
      sub.remove();
    };
  }, [check]);
  const openApp = () => void Linking.openSettings();
  const vendor = vendorSteps(status?.manufacturer);

  return (
    <View style={styles.screen}>
      <View style={styles.top}>
        <Button label="Back" onPress={() => router.back()} />
        <Text style={styles.title}>Run in background</Text>
      </View>
      <ScrollView contentContainerStyle={styles.body}>
        <Text style={styles.note}>
          Android may stop the connection when the screen is off. These settings keep the ride recording and
          the alerts sounding.
        </Text>
        <View style={styles.card}>
          <Check
            title="Nearby devices"
            ok={status ? status.nearby === 1 : null}
            okText="Allowed"
            badText="Not allowed: the app can't reach the vehicle"
            action={{ label: 'Open settings', onPress: openApp }}
          />
          <Check
            title="Notifications"
            ok={status ? status.notifications === 1 : null}
            okText="Allowed · needed for the ride notification and alerts"
            badText="Off: no ride notification and no alerts in the background"
            action={{ label: 'Open settings', onPress: openApp }}
          />
          <Check
            title="Battery optimisation"
            ok={status ? status.batteryOptimized === 0 : null}
            okText="Off for this app"
            badText="On · Android may stop the app"
            action={{
              label: 'Turn off',
              onPress: () => void Vesc.openBatterySettings().then((opened) => opened || openApp()),
            }}
          />
        </View>
        {vendor.steps.length > 0 ? (
          <View style={styles.card}>
            <Text style={styles.name}>{vendor.name}</Text>
            <Text style={styles.detail}>
              Some phones have an extra power saver. Also allow the app there:
            </Text>
            {vendor.steps.map((s) => (
              <Text key={s} style={styles.step}>
                {s}
              </Text>
            ))}
            <Text style={styles.note}>Menu names vary between system versions.</Text>
          </View>
        ) : null}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: space[4], gap: space[3] },
  top: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  body: { gap: space[4], paddingBottom: space[6] },
  card: {
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[4],
    gap: space[3],
  },
  row: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  mark: { width: 20, textAlign: 'center', fontSize: type.heading.size, fontFamily: fontFamily.uiSemiBold },
  text: { flex: 1, gap: 2 },
  name: { color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.uiSemiBold },
  detail: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  step: { color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  note: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
