// ReconnectBanner: non-blocking notice at the top of ride mode while the link is retried.
import { StyleSheet, Text, View } from 'react-native';

import { Icon } from '@/components/icon';
import { reconnectDetail } from '@/lib/format';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

export function ReconnectBanner({ attempt, recording }: { attempt: number; recording: boolean }) {
  return (
    <View style={styles.wrap} pointerEvents="none">
      <View style={styles.banner} accessibilityRole="alert">
        <Icon name="bluetooth-searching" size={28} color={colors.state.warn} />
        <View style={styles.texts}>
          <Text style={styles.title}>Connection lost · retrying</Text>
          <Text style={styles.detail} numberOfLines={1}>
            {reconnectDetail(attempt, recording)}
          </Text>
        </View>
      </View>
    </View>
  );
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
    borderColor: colors.state.warn,
    backgroundColor: colors.bgRaised,
  },
  texts: { flex: 1 },
  title: { color: colors.textPrimary, fontSize: type.body.size + 1, fontFamily: fontFamily.uiSemiBold },
  detail: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
