import Vesc, { type TransportKind } from '@modules/vesc';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, space, type } from '@/theme/tokens';

type Choice = { label: string; kind: TransportKind; controllers: number | null; path: string | null };

const FIXED: Choice[] = [
  { label: 'Bluetooth (real VESC)', kind: 'ble', controllers: null, path: null },
  { label: 'Synthetic, 1 controller', kind: 'synthetic', controllers: 1, path: null },
  { label: 'Synthetic, 2 controllers', kind: 'synthetic', controllers: 2, path: null },
];

/** Dev setting: where the session reads its data from. */
export function SourceScreen() {
  const [recordings, setRecordings] = useState<string[]>([]);
  useEffect(() => {
    void Vesc.listRecordings().then(setRecordings);
  }, []);
  const choices: Choice[] = [
    ...FIXED,
    ...recordings.map((path) => ({
      label: `Replay ${path.split('/').pop() ?? path}`,
      kind: 'replay' as const,
      controllers: null,
      path,
    })),
  ];

  const pick = async (c: Choice) => {
    await Vesc.disconnect();
    await Vesc.setTransport(c.kind, c.controllers, c.path);
    router.back();
  };

  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Data source</Text>
      <Text style={styles.note}>Applies to the next connection. Replay plays your own recordings.</Text>
      <FlatList
        data={choices}
        keyExtractor={(c) => `${c.kind}-${c.controllers ?? ''}-${c.path ?? ''}`}
        renderItem={({ item }) => (
          <Pressable
            onPress={() => void pick(item)}
            style={({ pressed }) => [styles.row, pressed && styles.pressed]}
          >
            <Text style={styles.label}>{item.label}</Text>
          </Pressable>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, padding: space[4], gap: space[2], backgroundColor: colors.bg },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontWeight: '700' },
  note: { color: colors.textSecondary, fontSize: type.body.size },
  row: {
    paddingVertical: space[3],
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.divider,
  },
  pressed: { backgroundColor: colors.accentMuted },
  label: { color: colors.textPrimary, fontSize: type.body.size },
});
