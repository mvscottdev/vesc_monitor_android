// Alert settings: switch single alerts off, change thresholds, see which ones the
// connected vehicle can't support, and play a test notification. The engine runs natively.
import Vesc, { type AlertThresholds, type TelemetryFrameV1 } from '@modules/vesc';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import {
  CATALOG,
  type CatalogEntry,
  catalogCounts,
  clearText,
  displayValue,
  EDIT,
  escalates,
  FALLING,
  GROUPS,
  type Sev,
  stepMargin,
  stepRepeat,
  stepThreshold,
  triggerText,
  unavailableReason,
} from '@/alerts/catalog';
import { Button } from '@/components/button';
import { useSessionStore } from '@/stores/session-store';
import { frameValue } from '@/telemetry/frame';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

/** The latest frame, sampled every 2 s: availability hints don't need more. */
function useSlowFrame(): TelemetryFrameV1 | null {
  const [frame, setFrame] = useState<TelemetryFrameV1 | null>(null);
  useEffect(() => {
    const tick = () => setFrame(frameValue.value);
    const first = setTimeout(tick, 0);
    const id = setInterval(tick, 2000);
    return () => {
      clearTimeout(first);
      clearInterval(id);
    };
  }, []);
  return frame;
}

const SEV_LABEL: Record<Sev, string> = { info: 'Info', warning: 'Warning', critical: 'Critical' };
const SEV_COLOR: Record<Sev, string> = {
  info: colors.state.info,
  warning: colors.state.warn,
  critical: colors.state.crit,
};

function ThresholdEditor({
  entry,
  values,
  tuning,
  isDefault,
  onSave,
  onReset,
  onCancel,
}: {
  entry: CatalogEntry;
  values: number[];
  /** [clear margin, repeat s] in effect. */
  tuning: number[] | undefined;
  isDefault: boolean;
  onSave: (v: number[], tuning: number[] | null) => void;
  onReset: () => void;
  onCancel: () => void;
}) {
  const spec = EDIT[entry.key]!;
  const rising = !FALLING.has(entry.key);
  const [draft, setDraft] = useState(values);
  const [tune, setTune] = useState(tuning);
  const ok = escalates(draft, !FALLING.has(entry.key)) && draft.every((v) => v > 0);
  const step = (i: number, dir: 1 | -1) =>
    setDraft((d) => d.map((v, j) => (j === i ? stepThreshold(v, spec, dir) : v)));
  return (
    <View style={styles.editor}>
      {draft.map((v, i) => {
        const sv = entry.severities[i] ?? 'warning';
        return (
          <View key={sv + i} style={styles.editRow}>
            <Text style={[styles.editLabel, { color: SEV_COLOR[sv] }]}>{SEV_LABEL[sv]}</Text>
            <Button label="−" onPress={() => step(i, -1)} />
            <Text style={styles.editValue}>
              {displayValue(v, spec)} <Text style={styles.trigger}>{spec.unit}</Text>
            </Text>
            <Button label="+" onPress={() => step(i, 1)} />
          </View>
        );
      })}
      {!ok && <Text style={styles.why}>Each level must be more severe than the one before.</Text>}
      {tune && tune.length === 2 ? (
        <>
          <View style={styles.editRow}>
            <Text style={styles.editLabel}>Clears</Text>
            <Button label="−" onPress={() => setTune([stepMargin(tune[0]!, spec, -1), tune[1]!])} />
            <Text style={styles.editValue}>{clearText(draft[0]!, tune[0]!, spec, rising)}</Text>
            <Button label="+" onPress={() => setTune([stepMargin(tune[0]!, spec, 1), tune[1]!])} />
          </View>
          <View style={styles.editRow}>
            <Text style={styles.editLabel}>Repeat</Text>
            <Button label="−" onPress={() => setTune([tune[0]!, stepRepeat(tune[1]!, -1)])} />
            <Text style={styles.editValue}>after {tune[1]} s</Text>
            <Button label="+" onPress={() => setTune([tune[0]!, stepRepeat(tune[1]!, 1)])} />
          </View>
        </>
      ) : null}
      <View style={styles.editRow}>
        <Button
          kind="primary"
          label="Save"
          disabled={!ok}
          onPress={() => onSave(draft, tune && tune !== tuning ? tune : null)}
        />
        {!isDefault && <Button label="Reset to default" onPress={onReset} />}
        <Button label="Cancel" onPress={onCancel} />
      </View>
    </View>
  );
}

export function AlertsScreen() {
  const session = useSessionStore((s) => s.session);
  const frame = useSlowFrame();
  const [disabled, setDisabled] = useState<Set<string>>(new Set());
  const [silent, setSilent] = useState<Set<string>>(new Set());
  const [thresholds, setThresholds] = useState<AlertThresholds | null>(null);
  const [tuning, setTuning] = useState<AlertThresholds | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadThresholds = () => {
    Vesc.alertThresholds()
      .then(setThresholds)
      .catch(() => undefined);
    Vesc.alertTuning()
      .then(setTuning)
      .catch(() => undefined);
  };
  useEffect(() => {
    Vesc.alertSettings()
      .then((keys) => setDisabled(new Set(keys)))
      .catch(() => undefined);
    Vesc.silentAlerts()
      .then((keys) => setSilent(new Set(keys)))
      .catch(() => undefined);
    loadThresholds();
  }, []);

  const inEffect = (key: string) => thresholds?.overrides[key] ?? thresholds?.defaults[key];
  const saveThresholds = (key: string, values: number[] | null, tune: number[] | null = null) => {
    // Thresholds first: the clear margin is checked against them. Reset also restores the margin and repeat time.
    Vesc.setAlertThresholds(key, values)
      .then((why) =>
        why ? why : values == null || tune ? Vesc.setAlertTuning(key, values == null ? null : tune) : null,
      )
      .then((reason) => {
        setError(reason);
        if (reason == null) setEditing(null);
        loadThresholds();
      })
      .catch(() => setError('Could not save.'));
  };

  const counts = catalogCounts(disabled, frame, session);

  const toggle = (key: string, on: boolean) => {
    setDisabled((prev) => {
      const next = new Set(prev);
      if (on) next.delete(key);
      else next.add(key);
      return next;
    });
    Vesc.setAlertEnabled(key, on).catch(() => undefined);
  };
  const toggleSilent = (key: string) => {
    const quiet = !silent.has(key);
    setSilent((prev) => {
      const next = new Set(prev);
      if (quiet) next.add(key);
      else next.delete(key);
      return next;
    });
    Vesc.setAlertSilent(key, quiet).catch(() => undefined);
  };

  return (
    <View style={styles.screen}>
      <View style={styles.top}>
        <Button label="Back" onPress={() => router.back()} />
        <Text style={styles.title}>Alerts</Text>
        <Text style={styles.count}>
          {counts.on} on{counts.unavailable > 0 ? ` · ${counts.unavailable} unavailable` : ''}
        </Text>
        <View style={styles.spacer} />
        <Button label="Test sound" onPress={() => Vesc.testAlert().catch(() => undefined)} />
      </View>
      <ScrollView contentContainerStyle={styles.list}>
        {GROUPS.map((g) => (
          <View key={g} style={styles.group}>
            <Text style={styles.groupTitle}>
              {g} · {CATALOG.filter((e) => e.group === g).length}
            </Text>
            {CATALOG.filter((e) => e.group === g).map((e) => {
              const why = unavailableReason(e.key, frame, session);
              const on = !disabled.has(e.key);
              const values = inEffect(e.key);
              const editable = EDIT[e.key] != null && values != null;
              const custom = thresholds?.overrides[e.key] != null;
              return (
                <View key={e.key}>
                  <View style={styles.row}>
                    <Pressable
                      style={styles.text}
                      disabled={!editable}
                      accessibilityRole={editable ? 'button' : undefined}
                      accessibilityHint={editable ? 'Change thresholds' : undefined}
                      onPress={() => {
                        setError(null);
                        setEditing(editing === e.key ? null : e.key);
                      }}
                    >
                      <Text style={[styles.name, !on && styles.off]}>{e.title}</Text>
                      <Text style={styles.trigger}>
                        {triggerText(e, values)}
                        {custom ? ' · yours' : ''}
                        {editable ? '  ›' : ''}
                      </Text>
                      {why ? <Text style={styles.why}>{why}</Text> : null}
                    </Pressable>
                    {why ? (
                      <Text
                        style={[
                          styles.chip,
                          { color: colors.textTertiary, borderColor: colors.textTertiary },
                        ]}
                      >
                        Unavailable
                      </Text>
                    ) : (
                      e.severities.map((sv) => (
                        <Text
                          key={sv}
                          style={[styles.chip, { color: SEV_COLOR[sv], borderColor: SEV_COLOR[sv] }]}
                        >
                          {SEV_LABEL[sv]}
                        </Text>
                      ))
                    )}
                    {!why && on && e.severities.some((sv) => sv !== 'info') ? (
                      <Pressable
                        accessibilityRole="button"
                        accessibilityLabel={`${e.title}: ${silent.has(e.key) ? 'banner only' : 'sound and notification'}`}
                        onPress={() => toggleSilent(e.key)}
                        hitSlop={8}
                      >
                        <Text style={[styles.chip, silent.has(e.key) ? styles.silentChip : styles.loudChip]}>
                          {silent.has(e.key) ? 'Silent' : 'Sound'}
                        </Text>
                      </Pressable>
                    ) : null}
                    <Switch
                      accessibilityLabel={e.title}
                      value={on}
                      onValueChange={(v) => toggle(e.key, v)}
                      trackColor={{ true: colors.accent, false: colors.divider }}
                    />
                  </View>
                  {editing === e.key && values && tuning ? (
                    <ThresholdEditor
                      entry={e}
                      values={values}
                      tuning={tuning?.overrides[e.key] ?? tuning?.defaults[e.key]}
                      isDefault={!custom && tuning?.overrides[e.key] == null}
                      onSave={(v, t) => saveThresholds(e.key, v, t)}
                      onReset={() => saveThresholds(e.key, null)}
                      onCancel={() => setEditing(null)}
                    />
                  ) : null}
                  {editing === e.key && error ? <Text style={styles.why}>{error}</Text> : null}
                </View>
              );
            })}
          </View>
        ))}
        <Text style={styles.note}>
          Tap an alert to change its thresholds. Battery cut follows the controller&apos;s own setting.
          Switching an alert off also hides it from the dashboard banner. Silent alerts show the banner but
          never sound or notify in the background.
        </Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  silentChip: { color: colors.textTertiary, borderColor: colors.textTertiary },
  loudChip: { color: colors.accent, borderColor: colors.accent },
  screen: { flex: 1, backgroundColor: colors.bg, padding: space[4], gap: space[3] },
  top: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  spacer: { flex: 1 },
  editor: { gap: space[2], paddingVertical: space[2], paddingLeft: space[4] },
  editRow: { flexDirection: 'row', alignItems: 'center', gap: space[3], flexWrap: 'wrap' },
  editLabel: { width: 80, fontSize: type.body.size, fontFamily: fontFamily.uiSemiBold },
  editValue: {
    minWidth: 120,
    textAlign: 'center',
    color: colors.textPrimary,
    fontSize: type.numS.size,
    fontFamily: fontFamily.display,
  },
  count: { color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui },
  chip: {
    fontSize: type.caption.size,
    fontFamily: fontFamily.uiSemiBold,
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingHorizontal: space[2],
    paddingVertical: 2,
  },
  list: { gap: space[4], paddingBottom: space[6] },
  group: {
    backgroundColor: colors.glassFill,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[4],
    gap: space[2],
  },
  groupTitle: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.uiSemiBold },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingVertical: space[2],
    borderBottomColor: colors.divider,
    borderBottomWidth: 1,
  },
  text: { flex: 1, gap: 2 },
  name: { color: colors.textPrimary, fontSize: type.body.size, fontFamily: fontFamily.uiSemiBold },
  off: { color: colors.textTertiary },
  trigger: { color: colors.textSecondary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  why: { color: colors.state.warn, fontSize: type.caption.size, fontFamily: fontFamily.ui },
  note: { color: colors.textTertiary, fontSize: type.caption.size, fontFamily: fontFamily.ui },
});
