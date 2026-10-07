import Vesc, { type ControllerInfo } from '@modules/vesc';
import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { controllerLabel } from '@/lib/format';
import { stopAndShareRecording } from '@/lib/session-actions';
import { useSessionStore } from '@/stores/session-store';
import { colors, space, type } from '@/theme/tokens';

/** Hidden BLE bench: poll toggles, per-controller link numbers, capture recording. */
export function BenchScreen() {
  const session = useSessionStore((s) => s.session);
  const [selective, setSelective] = useState(session?.selective ?? true);
  const [depth2, setDepth2] = useState((session?.depth ?? 1) === 2);
  const [forwardLocal, setForwardLocal] = useState(session?.forwardLocal ?? false);

  useEffect(() => {
    void Vesc.setPollConfig(selective, depth2 ? 2 : 1, forwardLocal);
  }, [selective, depth2, forwardLocal]);

  const recording = session?.recording != null;
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text style={styles.title}>BLE bench</Text>
      <Text style={styles.note}>Toggles apply at the next connect. Note screen on/off yourself.</Text>
      <Toggle label="Selective mask (off = full GET_VALUES)" value={selective} onChange={setSelective} />
      <Toggle label="Two requests in flight (experimental)" value={depth2} onChange={setDepth2} />
      <Toggle
        label="Poll the local controller through CAN forward"
        value={forwardLocal}
        onChange={setForwardLocal}
      />
      <Text style={styles.row}>
        MTU {session?.mtu ?? 0} · CRC errors {session?.crcErrors ?? 0} · unmatched {session?.unmatched ?? 0} ·
        notifications {session?.notifications ?? 0}
      </Text>
      {session?.controllers.map((c) => (
        <ControllerRow key={c.controllerId} c={c} />
      ))}
      <View style={styles.actions}>
        {recording ? (
          <Button kind="primary" label="Stop and export" onPress={() => void stopAndShareRecording()} />
        ) : (
          <Button
            label="Record session"
            onPress={() => void Vesc.startRecording()}
            disabled={session?.state !== 'connected'}
          />
        )}
      </View>
    </ScrollView>
  );
}

function Toggle({
  label,
  value,
  onChange,
}: {
  label: string;
  value: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <View style={styles.toggle}>
      <Text style={styles.label}>{label}</Text>
      <Switch value={value} onValueChange={onChange} />
    </View>
  );
}

function ControllerRow({ c }: { c: ControllerInfo }) {
  const f = (n: number) => n.toFixed(1);
  return (
    <Text style={styles.row}>
      {controllerLabel(c.controllerId, c.local)}: {f(c.hz)} Hz · write {f(c.writeMs)} ms · RTT {f(c.rttMs)} ms
      · {f(c.notificationsPerReply)} notif/reply over {f(c.notificationSpanMs)} ms · timeouts {c.timeouts}/
      {c.requests}
    </Text>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: space[4], gap: space[2] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontWeight: '700' },
  note: { color: colors.textSecondary, fontSize: type.body.size },
  toggle: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', maxWidth: 520 },
  label: { color: colors.textPrimary, fontSize: type.body.size },
  row: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: 'monospace' },
  actions: { flexDirection: 'row', gap: space[2], marginTop: space[3] },
});
