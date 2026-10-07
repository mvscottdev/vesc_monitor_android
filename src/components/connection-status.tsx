import type { SessionEvent } from '@modules/vesc';
import { StyleSheet, Text, View } from 'react-native';

import { controllerLabel } from '@/lib/format';
import { colors, space, type } from '@/theme/tokens';

const STATE_LABEL: Record<SessionEvent['state'], string> = {
  idle: 'Not connected',
  scanning: 'Searching…',
  connecting: 'Connecting…',
  connected: 'Connected',
  reconnecting: 'Reconnecting…',
  lost: 'Connection lost',
};

/** Connection state with its plain-language reason, and the topology found. */
export function ConnectionStatus({ session }: { session: SessionEvent | null }) {
  if (!session) return null;
  const warn = session.state === 'lost';
  return (
    <View style={styles.box}>
      <Text style={[styles.state, warn && styles.warn]}>{STATE_LABEL[session.state]}</Text>
      {session.reason && <Text style={styles.reason}>{session.reason}</Text>}
      {session.firmware && (
        <Text style={styles.meta}>
          {session.hardware} · FW {session.firmware} · {session.transport}
        </Text>
      )}
      {session.controllers.map((c) => (
        <Text key={c.controllerId} style={styles.meta}>
          {controllerLabel(c.controllerId, c.local)} · FW {c.firmware} ·{' '}
          {c.fresh ? `${c.hz.toFixed(0)} Hz` : 'stale'}
          {c.untestedFirmware ? ' · untested firmware' : ''}
        </Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: space[1] },
  state: { color: colors.textPrimary, fontSize: type.title.size, fontWeight: '600' },
  warn: { color: colors.state.warn },
  reason: { color: colors.textSecondary, fontSize: type.body.size },
  meta: { color: colors.textTertiary, fontSize: type.caption.size },
});
