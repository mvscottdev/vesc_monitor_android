// Ride-mode top line: menu, link dot + source, measured rate, controller count, page
// dots, info-alert count, REC pill, clock. Reads the warm store (≤ 5 Hz), never the live frame.
import type { SessionEvent } from '@modules/vesc';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { RecPill } from './rec-pill';
import { fontFamily } from '@/theme/fonts';
import { colors, grid, space, touch, type } from '@/theme/tokens';

type Props = {
  session: SessionEvent | null;
  page: number;
  pages: number;
  /** Active info alerts (they never get a banner). */
  infoAlerts?: number;
  onMenu: () => void;
};

function linkColor(s: SessionEvent | null): string {
  if (s?.state === 'connected')
    return s.controllers.some((c) => !c.fresh) ? colors.state.warn : colors.state.ok;
  if (s?.state === 'connecting') return colors.state.info;
  return colors.state.crit;
}

/** The slowest fresh controller sets the rate the rider actually gets. */
export function rateLabel(s: SessionEvent | null): string {
  const fresh = s?.state === 'connected' ? s.controllers.filter((c) => c.fresh && c.hz > 0) : [];
  if (fresh.length === 0) return '—';
  return `${Math.round(Math.min(...fresh.map((c) => c.hz)))} Hz`;
}

function sourceLabel(s: SessionEvent | null): string {
  if (!s || s.state === 'idle' || s.state === 'lost') return 'Not connected';
  if (s.state === 'connecting') return 'Connecting…';
  if (s.state === 'reconnecting') return 'Reconnecting';
  return s.transport === 'ble' ? 'VESC BLE' : s.transport === 'replay' ? 'Replay' : 'Synthetic';
}

function useClock(): string {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 15_000);
    return () => clearInterval(id);
  }, []);
  return `${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}`;
}

export function StatusStrip({ session, page, pages, infoAlerts = 0, onMenu }: Props) {
  const clock = useClock();
  const n = session?.state === 'connected' ? session.controllers.length : 0;
  return (
    <View style={styles.strip}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Menu"
        onPress={onMenu}
        style={styles.menu}
        hitSlop={16}
      >
        <View style={styles.menuBar} />
        <View style={styles.menuBar} />
        <View style={styles.menuBar} />
      </Pressable>
      <View style={[styles.dot, { backgroundColor: linkColor(session) }]} />
      <Text style={styles.primary}>{sourceLabel(session)}</Text>
      <Text style={styles.secondary}>{rateLabel(session)}</Text>
      {n > 1 && <Text style={styles.secondary}>{n} controllers</Text>}
      <View style={styles.spacer}>
        {pages > 1 &&
          Array.from({ length: pages }, (_, i) => (
            <View key={i} style={[styles.page, i === page && styles.pageOn]} />
          ))}
      </View>
      {infoAlerts > 0 && (
        <Text style={styles.info}>
          {infoAlerts} {infoAlerts === 1 ? 'notice' : 'notices'}
        </Text>
      )}
      <RecPill session={session} />
      <Text style={styles.primary}>{clock}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  strip: {
    height: grid.statusBarHeight,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: grid.padding + space[1],
    gap: space[3],
  },
  // Inset from the screen edge so a rounded display corner doesn't cut the target.
  menu: { width: 26, height: touch.minTarget / 2, justifyContent: 'center', gap: 4, marginLeft: space[3] },
  menuBar: { height: 2.5, borderRadius: 1.25, backgroundColor: colors.textSecondary },
  dot: { width: 8, height: 8, borderRadius: 4 },
  primary: { color: colors.textPrimary, fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  secondary: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  info: { color: colors.state.info, fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  spacer: { flex: 1, flexDirection: 'row', justifyContent: 'center', gap: 5 },
  page: { width: 6, height: 6, borderRadius: 3, backgroundColor: colors.textDisabled },
  pageOn: { width: 16, backgroundColor: colors.textSecondary },
});
