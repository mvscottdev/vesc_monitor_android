// AlertBanner: overlay from the top of ride mode with the most severe active alert.
// Critical stays until it clears or the rider dismisses it; warnings hide after 6 s.
// Info alerts never show here (the status strip counts them).
import Vesc, { type ActiveAlert } from '@modules/vesc';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { alertId, alertText, bannerAlert } from '@/alerts/texts';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, touch, type } from '@/theme/tokens';
import { useNamesStore } from '@/stores/names-store';
import { useUnits } from '@/stores/units-store';

export function AlertBanner({
  alerts,
  seen,
}: {
  alerts: ActiveAlert[];
  /** When each alert was first seen (session store). */
  seen: ReadonlyMap<string, number>;
}) {
  const units = useUnits();
  const names = useNamesStore((s) => s.names);
  const [dismissed, setDismissed] = useState<ReadonlySet<string>>(new Set());
  const [now, setNow] = useState(() => Date.now());
  const warning = alerts.some((a) => a.severity === 'warning');
  // A coarse clock while a warning is active, so it hides on time without new events.
  useEffect(() => {
    if (!warning) return undefined;
    const id = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(id);
  }, [warning]);
  const shown = bannerAlert(alerts, dismissed, seen, Math.max(now, latest(seen)));

  if (!shown) return null;
  const text = alertText(shown, units, names);
  const tint = shown.severity === 'critical' ? colors.state.crit : colors.state.warn;
  const dismiss = () => {
    setDismissed((d) => new Set(d).add(alertId(shown)));
    if (shown.key === 'fault') void Vesc.dismissFaults();
  };
  return (
    <View style={styles.wrap} pointerEvents="box-none">
      <View style={[styles.banner, { borderColor: tint }]} accessibilityRole="alert">
        <View style={[styles.tile, { backgroundColor: tint }]}>
          <Text style={styles.tileText}>!</Text>
        </View>
        <View style={styles.texts}>
          <Text style={styles.title}>{text.title}</Text>
          <Text style={styles.detail} numberOfLines={1}>
            {text.detail}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Dismiss alert"
          onPress={dismiss}
          style={styles.close}
          hitSlop={8}
        >
          <Text style={styles.closeText}>✕</Text>
        </Pressable>
      </View>
    </View>
  );
}

function latest(seen: ReadonlyMap<string, number>): number {
  let t = 0;
  for (const v of seen.values()) t = Math.max(t, v);
  return t;
}

const styles = StyleSheet.create({
  wrap: { position: 'absolute', top: space[2], left: 0, right: 0, alignItems: 'center' },
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    minWidth: 420,
    maxWidth: 640,
    paddingVertical: space[2],
    paddingHorizontal: space[3],
    borderRadius: radius.lg,
    borderWidth: 1,
    backgroundColor: colors.bgRaised,
  },
  tile: { width: 40, height: 40, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  tileText: { color: colors.bg, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  texts: { flex: 1 },
  title: { color: colors.textPrimary, fontSize: type.body.size + 1, fontFamily: fontFamily.uiSemiBold },
  detail: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  close: { width: touch.minTarget, height: touch.minTarget, alignItems: 'center', justifyContent: 'center' },
  closeText: { color: colors.textSecondary, fontSize: type.body.size },
});
