// SetupSteps: the connect sequence as a short checklist (connect-detect mockup).
import { StyleSheet, Text, View } from 'react-native';

import { Icon } from '@/components/icon';
import type { SetupStepRow } from '@/connect/steps';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

const NOW_ICON = ['bluetooth', 'cpu', 'scan-line', 'bike'] as const;

export function SetupSteps({ rows }: { rows: SetupStepRow[] }) {
  return (
    <View style={styles.list}>
      {rows.map((r, i) => (
        <View key={i} style={styles.row}>
          <View style={[styles.dot, r.state === 'done' && styles.done, r.state === 'now' && styles.now]}>
            <Icon
              name={r.state === 'done' ? 'check' : NOW_ICON[i]!}
              size={16}
              color={r.state === 'done' ? colors.bg : r.state === 'now' ? colors.accent : colors.textTertiary}
            />
          </View>
          <View style={styles.texts}>
            <Text style={[styles.label, r.state === 'todo' && styles.todo]}>{r.label}</Text>
            <Text style={styles.sub}>{r.sub}</Text>
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: space[3] },
  row: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  dot: {
    width: 30,
    height: 30,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.glassEdge,
    alignItems: 'center',
    justifyContent: 'center',
  },
  done: { backgroundColor: colors.accent, borderColor: colors.accent },
  now: { borderColor: colors.accent },
  texts: { flex: 1 },
  label: { color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.uiMedium },
  todo: { color: colors.textSecondary },
  sub: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
