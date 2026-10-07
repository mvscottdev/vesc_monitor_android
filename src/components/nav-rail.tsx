// Left navigation rail of the non-ride screens (design kit "rail"): ride, speed test,
// history, vehicle, alerts, and settings at the bottom. The current screen is raised.
import { router } from 'expo-router';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Icon } from './icon';
import type { IconName } from '@/theme/icons';
import { colors, radius, space, touch } from '@/theme/tokens';

export type RailKey = 'ride' | 'run' | 'history' | 'vehicle' | 'alerts' | 'settings';

const ITEMS: { key: RailKey; icon: IconName; label: string; route: string }[] = [
  { key: 'ride', icon: 'gauge', label: 'Dashboard', route: '/' },
  { key: 'run', icon: 'timer', label: 'Speed test', route: '/speed-test' },
  { key: 'history', icon: 'history', label: 'History', route: '/history' },
  { key: 'vehicle', icon: 'bike', label: 'Vehicle and battery', route: '/battery' },
  { key: 'alerts', icon: 'bell', label: 'Alerts', route: '/alerts' },
];

function Item({
  icon,
  label,
  on,
  onPress,
}: {
  icon: IconName;
  label: string;
  on: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ selected: on }}
      onPress={onPress}
      style={({ pressed }) => [styles.item, on && styles.itemOn, pressed && styles.pressed]}
    >
      <Icon name={icon} size={22} color={on ? colors.textOnAccent : colors.textSecondary} />
    </Pressable>
  );
}

export function NavRail({ current }: { current: RailKey | null }) {
  const go = (route: string) => {
    if (route === '/') router.dismissAll();
    // Swap instead of push, so the stack stays dashboard + one screen.
    else router.replace(route as never);
  };
  return (
    <View style={styles.rail}>
      {ITEMS.map((i) => (
        <Item key={i.key} icon={i.icon} label={i.label} on={i.key === current} onPress={() => go(i.route)} />
      ))}
      <View style={styles.spacer} />
      <Item icon="settings" label="Settings" on={current === 'settings'} onPress={() => go('/settings')} />
    </View>
  );
}

const styles = StyleSheet.create({
  rail: {
    width: touch.rideMinTarget + space[4],
    paddingVertical: space[3],
    alignItems: 'center',
    gap: space[2],
    borderRightColor: colors.divider,
    borderRightWidth: 1,
    backgroundColor: colors.bg,
  },
  item: {
    width: touch.rideMinTarget - 4,
    height: touch.rideMinTarget - 4,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  itemOn: { backgroundColor: colors.accent },
  pressed: { opacity: 0.6 },
  spacer: { flex: 1 },
});

/** A screen with the rail on its left. */
export function WithRail({ current, children }: { current: RailKey | null; children: ReactNode }) {
  return (
    <View style={shell.row}>
      <NavRail current={current} />
      <View style={shell.main}>{children}</View>
    </View>
  );
}

const shell = StyleSheet.create({
  row: { flex: 1, flexDirection: 'row', backgroundColor: colors.bg },
  main: { flex: 1 },
});
