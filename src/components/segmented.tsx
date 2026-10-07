// Segmented control: one choice of a few, the chosen one raised (design kit "segc").
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

type Props<T extends string> = {
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
  label: string;
};

export function Segmented<T extends string>({ value, options, onChange, label }: Props<T>) {
  return (
    <View style={styles.wrap} accessibilityRole="radiogroup" accessibilityLabel={label}>
      {options.map((o) => {
        const on = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="radio"
            accessibilityState={{ checked: on }}
            onPress={() => onChange(o.value)}
            style={[styles.seg, on && styles.on]}
          >
            <Text style={[styles.text, on && styles.textOn]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flexDirection: 'row',
    backgroundColor: colors.glassFill,
    borderRadius: radius.md,
    padding: 3,
    gap: 2,
  },
  seg: { paddingHorizontal: space[4], paddingVertical: space[2], borderRadius: radius.sm, minWidth: 56 },
  on: { backgroundColor: colors.glassFillStrong, borderColor: colors.glassEdge, borderWidth: 1 },
  text: {
    color: colors.textSecondary,
    fontSize: type.body.size,
    fontFamily: fontFamily.uiSemiBold,
    textAlign: 'center',
  },
  textOn: { color: colors.textPrimary },
});
