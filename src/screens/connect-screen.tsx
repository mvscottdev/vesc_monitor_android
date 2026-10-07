import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { Button } from '@/components/button';
import { Icon } from '@/components/icon';
import { fontFamily } from '@/theme/fonts';
import { ConnectionStatus } from '@/components/connection-status';
import { FailureCard } from '@/components/failure-card';
import { failureCard } from '@/connect/failure';
import { ScanList } from '@/components/scan-list';
import { SetupSteps } from '@/components/setup-steps';
import { setupSteps } from '@/connect/steps';
import { controllerLabel } from '@/lib/format';
import { NAME_MAX } from '@/lib/names';
import { setControllerName, useNamesStore } from '@/stores/names-store';
import { connect, toggleScan } from '@/lib/session-actions';
import { useSessionStore } from '@/stores/session-store';
import { colors, radius, space, type } from '@/theme/tokens';
import Vesc from '@modules/vesc';

const NODE_KIND = { bms: 'BMS', module: 'Module', other: 'Other device' } as const;

/** Scan, connect, connection state and the data source (connect mockup); long-press the icon for the BLE bench. */
export function ConnectScreen() {
  const session = useSessionStore((s) => s.session);
  const scan = useSessionStore((s) => s.scan);
  const names = useNamesStore((s) => s.names);
  const [message, setMessage] = useState<string | null>(null);
  const active =
    session?.state === 'connected' || session?.state === 'connecting' || session?.state === 'reconnecting';
  const steps = setupSteps(session);
  const ble = (session?.transport ?? 'ble') === 'ble';
  // The failure card shows once per session generation until the rider acts on it.
  const [dismissed, setDismissed] = useState<number | null>(null);
  const failure =
    session?.state === 'lost' && session.generation !== dismissed ? failureCard(session.reasonCode) : null;

  const run = (action: () => Promise<string | null | void>) => {
    setMessage(null);
    action()
      .then((m) => setMessage(m ?? null))
      .catch((e: unknown) => setMessage(e instanceof Error ? e.message : String(e)));
  };

  return (
    <View style={styles.screen}>
      <View style={styles.side}>
        {/* Long-press the icon to open the hidden BLE bench. */}
        <Pressable onLongPress={() => router.push('/bench')} delayLongPress={600} style={styles.hero}>
          <Icon name={active ? 'bluetooth-connected' : 'bluetooth'} size={30} color={colors.accent} />
        </Pressable>
        <Text style={styles.title}>{active ? 'Your vehicle' : 'Connect your vehicle'}</Text>
        {!active ? (
          <Text style={styles.lead}>
            Turn the scooter or bike on. The app lists BLE bridges nearby and never changes anything on the
            controller.
          </Text>
        ) : null}
        {steps.length > 0 ? <SetupSteps rows={steps} /> : <ConnectionStatus session={session} />}
        {message && <Text style={styles.message}>{message}</Text>}
        <View style={styles.actions}>
          {active ? (
            <Button label="Disconnect" onPress={() => run(() => Vesc.disconnect())} />
          ) : ble ? (
            <Button
              kind="primary"
              label={scan.scanning ? 'Stop search' : 'Search'}
              onPress={() => run(() => toggleScan(!scan.scanning))}
            />
          ) : (
            <Button kind="primary" label="Start" onPress={() => run(() => connect(null))} />
          )}
          <Button label={`Source: ${session?.transport ?? 'ble'}`} onPress={() => router.push('/source')} />
          {active ? <Button kind="primary" label="Dashboard" onPress={() => router.back()} /> : null}
        </View>
      </View>
      <View style={styles.main}>
        {failure && session ? (
          <FailureCard
            card={failure}
            code={session.reasonCode ?? ''}
            onRetry={() => {
              setDismissed(session.generation);
              run(() => connect(session.address ?? null));
            }}
            onChooseAnother={() => {
              setDismissed(session.generation);
              if (ble) run(() => toggleScan(true));
            }}
          />
        ) : ble && !active ? (
          <>
            <View style={styles.listHead}>
              <Text style={styles.section}>NEARBY</Text>
              <View style={styles.spacer} />
              {scan.scanning ? <Text style={styles.scanning}>Scanning…</Text> : null}
            </View>
            <ScanList devices={scan.devices} onPick={(d) => run(() => connect(d.address))} />
            <Text style={styles.note}>A bridge talks to one app at a time. Close VESC Tool first.</Text>
          </>
        ) : (
          <>
            {active ? (
              <Text style={styles.section}>FOUND ON THIS VEHICLE · TAP A NAME TO CHANGE IT</Text>
            ) : null}
            {active
              ? session?.controllers.map((c) => (
                  <View key={c.controllerId} style={styles.found}>
                    <Icon name="cpu" size={20} color={colors.textSecondary} />
                    <View style={styles.spacer}>
                      <TextInput
                        key={names[c.controllerId] ?? ''}
                        style={styles.foundName}
                        defaultValue={names[c.controllerId] ?? ''}
                        placeholder={controllerLabel(c.controllerId, c.local)}
                        placeholderTextColor={colors.textSecondary}
                        maxLength={NAME_MAX}
                        accessibilityLabel={`Name for ${controllerLabel(c.controllerId, c.local)}`}
                        onEndEditing={(e) => setControllerName(c.controllerId, e.nativeEvent.text)}
                      />
                      <Text style={styles.note}>
                        {c.local ? 'Local' : `CAN ${c.controllerId}`} · fw {c.firmware} ·{' '}
                        {c.fresh ? `${c.hz.toFixed(0)} Hz` : 'stale'}
                        {c.untestedFirmware ? ' · untested firmware' : ''}
                      </Text>
                    </View>
                  </View>
                ))
              : null}
            {active
              ? session?.otherNodes?.map((o) => (
                  <View key={`n${o.canId}`} style={[styles.found, styles.skipped]}>
                    <Icon
                      name={o.kind === 'bms' ? 'battery-charging' : 'cpu'}
                      size={20}
                      color={colors.textTertiary}
                    />
                    <View style={styles.spacer}>
                      <Text style={styles.foundName}>{NODE_KIND[o.kind]}</Text>
                      <Text style={styles.note}>
                        CAN {o.canId} · {o.hardware || 'unknown'} · not a motor controller, skipped
                      </Text>
                    </View>
                  </View>
                ))
              : null}
            <Text style={styles.note}>
              {active
                ? 'Read-only: the app never changes anything on the controllers.'
                : 'This source needs no search: press Start.'}
            </Text>
          </>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, flexDirection: 'row', backgroundColor: colors.bg, padding: space[5], gap: space[6] },
  side: { width: 340, gap: space[3] },
  hero: {
    width: 64,
    height: 64,
    borderRadius: radius.lg,
    backgroundColor: colors.glassFillStrong,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  main: { flex: 1, gap: space[2] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  lead: { color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  message: { color: colors.state.warn, fontSize: type.body.size },
  actions: { flexDirection: 'row', gap: space[2], flexWrap: 'wrap' },
  listHead: { flexDirection: 'row', alignItems: 'center' },
  section: {
    color: colors.textTertiary,
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    letterSpacing: 1,
  },
  spacer: { flex: 1 },
  scanning: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  found: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    padding: space[3],
    borderRadius: radius.md,
    backgroundColor: colors.glassFillStrong,
    borderColor: colors.glassEdge,
    borderWidth: 1,
  },
  skipped: { opacity: 0.6 },
  foundName: {
    color: colors.textPrimary,
    fontSize: type.body.size,
    fontFamily: fontFamily.uiMedium,
    paddingVertical: 2,
    paddingHorizontal: 0,
  },
  note: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
