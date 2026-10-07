// Logging toggle with the open ride's time. Solid red dot while a ride records,
// hollow and dimmed while logging is off. 56 dp hit area for ride mode.
import Vesc, { type SessionEvent } from '@modules/vesc';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { formatDuration } from '@/lib/format';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, touch, type } from '@/theme/tokens';

export function RecPill({ session }: { session: SessionEvent | null }) {
  const on = session?.logging ?? true;
  const elapsed = session?.rideElapsedMs ?? null;
  const label = elapsed != null ? `REC ${formatDuration(elapsed)}` : 'REC';
  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{ checked: on }}
      accessibilityLabel="Ride logging"
      onPress={() => void Vesc.setLogging(!on)}
      hitSlop={(touch.rideMinTarget - 22) / 2}
      style={[styles.pill, !on && styles.off]}
    >
      <View style={[styles.dot, on ? styles.dotOn : styles.dotOff]} />
      <Text style={[styles.text, !on && styles.textOff]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pill: {
    height: 22,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    borderRadius: radius.pill,
    // 18 % of the crit colour, as in the mockup.
    backgroundColor: `${colors.state.crit}2E`,
  },
  off: { backgroundColor: colors.glassFill },
  dot: { width: 8, height: 8, borderRadius: 4 },
  dotOn: { backgroundColor: colors.state.crit },
  dotOff: { borderWidth: 1.5, borderColor: colors.textTertiary },
  text: { color: colors.textPrimary, fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  textOff: { color: colors.textTertiary },
});
