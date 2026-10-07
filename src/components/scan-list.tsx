import type { ScanDevice } from '@modules/vesc';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { Icon } from './icon';
import { signalBars } from '@/lib/format';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

type Props = { devices: ScanDevice[]; onPick: (d: ScanDevice) => void };

function Bars({ n }: { n: number }) {
  return (
    <View style={styles.bars}>
      {[0, 1, 2, 3].map((i) => (
        <View key={i} style={[styles.bar, { height: 4 + i * 3 }, i < n && styles.barOn]} />
      ))}
    </View>
  );
}

/** Scan results as the connect mockup: icon, name, what it looks like, signal. Tap to connect. */
export function ScanList({ devices, onPick }: Props) {
  return (
    <FlatList
      data={devices}
      keyExtractor={(d) => d.address}
      style={styles.list}
      ListEmptyComponent={<Text style={styles.empty}>Nothing found yet.</Text>}
      renderItem={({ item }) => {
        const dim = !item.looksLikeVesc && item.name == null;
        return (
          <Pressable
            accessibilityRole="button"
            onPress={() => onPick(item)}
            style={({ pressed }) => [styles.row, pressed && styles.pressed]}
          >
            <View style={styles.iconBox}>
              <Icon
                name="bluetooth"
                size={18}
                color={item.looksLikeVesc ? colors.accent : colors.textTertiary}
              />
            </View>
            <View style={styles.grow}>
              <Text style={[styles.name, dim && styles.dim]}>{item.name ?? 'Unnamed device'}</Text>
              <Text style={styles.hint}>
                {item.looksLikeVesc ? 'UART bridge · looks like a VESC' : 'No known UART service advertised'}
              </Text>
            </View>
            <Bars n={signalBars(item.rssi)} />
            <Text style={styles.rssi}>{item.rssi} dBm</Text>
          </Pressable>
        );
      }}
    />
  );
}

const styles = StyleSheet.create({
  list: {
    flexGrow: 0,
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    minHeight: 54,
    paddingHorizontal: space[3],
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  pressed: { backgroundColor: colors.accentMuted },
  iconBox: {
    width: 34,
    height: 34,
    borderRadius: radius.sm,
    backgroundColor: colors.glassFillStrong,
    alignItems: 'center',
    justifyContent: 'center',
  },
  grow: { flex: 1, gap: 2 },
  name: { color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.uiSemiBold },
  dim: { color: colors.textTertiary },
  hint: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  bars: { flexDirection: 'row', alignItems: 'flex-end', gap: 2, height: 14 },
  bar: { width: 3, borderRadius: 1, backgroundColor: colors.divider },
  barOn: { backgroundColor: colors.textSecondary },
  rssi: {
    width: 64,
    textAlign: 'right',
    color: colors.textTertiary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.ui,
  },
  empty: { color: colors.textTertiary, fontSize: type.body.size, padding: space[4] },
});
