import { Pressable, StyleSheet, Text } from 'react-native';

import { colors, radius, space, type } from '@/theme/tokens';

type Props = {
  label: string;
  onPress: () => void;
  onLongPress?: () => void;
  kind?: 'primary' | 'quiet';
  disabled?: boolean;
};

export function Button({ label, onPress, onLongPress, kind = 'quiet', disabled = false }: Props) {
  const primary = kind === 'primary';
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      onLongPress={onLongPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.base,
        primary ? styles.primary : styles.quiet,
        (pressed || disabled) && styles.dim,
      ]}
    >
      <Text style={[styles.label, primary && styles.labelPrimary]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    paddingHorizontal: space[4],
    paddingVertical: space[2],
    borderRadius: radius.xs,
    alignItems: 'center',
  },
  primary: { backgroundColor: colors.accent },
  quiet: { backgroundColor: colors.accentMuted },
  dim: { opacity: 0.5 },
  label: { color: colors.textPrimary, fontSize: type.body.size, fontWeight: '600' },
  labelPrimary: { color: colors.textOnAccent },
});
