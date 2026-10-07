// FailureCard: what went wrong with the connection and the one button that helps (connect-failed mockup).
import { Linking, StyleSheet, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Icon } from '@/components/icon';
import type { FailureCard as Card } from '@/connect/failure';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

type Props = {
  card: Card;
  /** Raw reason code, shown small for bug reports. */
  code: string;
  onRetry: () => void;
  onChooseAnother: () => void;
};

export function FailureCard({ card, code, onRetry, onChooseAnother }: Props) {
  const primary =
    card.action === 'bluetooth'
      ? {
          label: 'Bluetooth settings',
          onPress: () =>
            void Linking.sendIntent('android.settings.BLUETOOTH_SETTINGS').catch(() =>
              Linking.openSettings(),
            ),
        }
      : card.action === 'settings'
        ? { label: 'App settings', onPress: () => void Linking.openSettings() }
        : { label: 'Try again', onPress: onRetry };
  return (
    <View style={styles.card} accessibilityRole="alert">
      <View style={styles.tile}>
        <Icon name="unplug" size={26} color={colors.state.warn} />
      </View>
      <View style={styles.texts}>
        <Text style={styles.title}>{card.title}</Text>
        <Text style={styles.body}>{card.body}</Text>
        <Text style={styles.code}>{code.toUpperCase()}</Text>
        <View style={styles.actions}>
          <Button kind="primary" label={primary.label} onPress={primary.onPress} />
          {card.action !== 'retry' && card.action !== 'pairing' ? (
            <Button label="Try again" onPress={onRetry} />
          ) : null}
          <Button label="Choose another device" onPress={onChooseAnother} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    gap: space[4],
    padding: space[5],
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.glassEdge,
    backgroundColor: colors.bgRaised,
    maxWidth: 560,
  },
  tile: {
    width: 52,
    height: 52,
    borderRadius: radius.lg,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.glassFillStrong,
  },
  texts: { flex: 1, gap: space[2] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  body: { color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  code: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  actions: { flexDirection: 'row', gap: space[2], flexWrap: 'wrap', marginTop: space[2] },
});
