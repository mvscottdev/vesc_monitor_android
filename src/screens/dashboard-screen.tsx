// Ride mode: status strip over swipeable pages, one Skia canvas per page. The screen
// stays awake and the system bars are hidden while it is shown.
import { useKeepAwake } from 'expo-keep-awake';
import * as NavigationBar from 'expo-navigation-bar';
import { router, useFocusEffect } from 'expo-router';
import Vesc from '@modules/vesc';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import PagerView from 'react-native-pager-view';

import { AlertBanner } from '@/components/alert-banner';
import { ReconnectBanner } from '@/components/reconnect-banner';
import { Button } from '@/components/button';
import { StatusStrip } from '@/components/status-strip';
import { DashboardEditor } from '@/dashboard/dashboard-editor';
import { DashboardPage } from '@/dashboard/dashboard-page';
import { layoutKey, parseLayout } from '@/dashboard/edit';
import type { Layout } from '@/dashboard/layout';
import { defaultLayout } from '@/dashboard/presets';
import { useSessionStore } from '@/stores/session-store';
import { fontFamily } from '@/theme/fonts';
import { colors, radius, space, type } from '@/theme/tokens';

function useHiddenSystemBars() {
  useFocusEffect(
    useCallback(() => {
      if (Platform.OS !== 'android') return undefined;
      NavigationBar.setVisibilityAsync('hidden').catch(() => undefined);
      return () => {
        NavigationBar.setVisibilityAsync('visible').catch(() => undefined);
      };
    }, []),
  );
}

export function DashboardScreen() {
  useKeepAwake();
  useHiddenSystemBars();
  const session = useSessionStore((s) => s.session);
  const alerts = useSessionStore((s) => s.alerts);
  const alertSeen = useSessionStore((s) => s.alertSeen);
  const [page, setPage] = useState(0);
  const connected = session?.state === 'connected';
  const ids = connected ? session.controllers.map((c) => c.controllerId).join(',') : '';
  const localId = connected ? (session.controllers.find((c) => c.local)?.controllerId ?? null) : null;
  // Rebuild the layout only when the set of controllers changes, not on every session event.
  const controllerIds = useMemo(() => (ids ? ids.split(',').map(Number) : []), [ids]);
  const fallback = useMemo(
    () => defaultLayout(controllerIds.map((controllerId) => ({ controllerId }))),
    [controllerIds],
  );
  const [saved, setSaved] = useState<{ key: string; layout: Layout | null } | null>(null);
  const [editing, setEditing] = useState(false);
  const key = layoutKey(controllerIds);
  useEffect(() => {
    let live = true;
    Vesc.uiSetting(key)
      .then((json) => live && setSaved({ key, layout: parseLayout(json, controllerIds) }))
      .catch(() => undefined);
    return () => {
      live = false;
    };
  }, [key, controllerIds]);
  const layout = (saved?.key === key ? saved.layout : null) ?? fallback;
  const store = (next: Layout | null) => {
    setSaved({ key, layout: next });
    Vesc.setUiSetting(key, next ? JSON.stringify(next) : null).catch(() => undefined);
  };
  const multi = ids.split(',').filter(Boolean).length > 1;
  const reconnecting = session?.state === 'reconnecting';
  const active = connected || reconnecting || session?.state === 'connecting';
  const openConnect = () => router.push('/connect');
  const pageIndex = Math.min(page, layout.pages.length - 1);
  if (editing) {
    return (
      <DashboardEditor
        layout={layout}
        pageIndex={pageIndex}
        multi={multi}
        localId={localId}
        controllerIds={controllerIds}
        onChange={store}
        onPageIndex={setPage}
        onReset={() => {
          setPage(0);
          store(null);
        }}
        onDone={() => setEditing(false)}
      />
    );
  }
  return (
    <View style={styles.screen}>
      <StatusStrip
        session={session}
        page={page}
        pages={layout.pages.length}
        infoAlerts={alerts.filter((a) => a.severity === 'info').length}
        onMenu={openConnect}
      />
      <PagerView
        key={ids}
        style={styles.pager}
        initialPage={pageIndex}
        onPageSelected={(e) => setPage(e.nativeEvent.position)}
      >
        {layout.pages.map((p) => (
          <Pressable
            key={p.id}
            style={styles.pager}
            collapsable={false}
            onLongPress={() => setEditing(true)}
            delayLongPress={700}
            accessibilityHint="Long-press to edit this page"
          >
            <DashboardPage page={p} multi={multi} localId={localId} />
          </Pressable>
        ))}
      </PagerView>
      {active && !reconnecting && <AlertBanner alerts={alerts} seen={alertSeen} />}
      {reconnecting && (
        <ReconnectBanner attempt={session?.attempt ?? 0} recording={session?.rideElapsedMs != null} />
      )}
      {!active && (
        <View style={styles.overlay} pointerEvents="box-none">
          <View style={styles.card}>
            <Text style={styles.title}>
              {session?.state === 'lost' ? 'Connection lost' : 'Not connected'}
            </Text>
            {session?.reason && <Text style={styles.reason}>{session.reason}</Text>}
            <Button kind="primary" label="Connect" onPress={openConnect} />
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  pager: { flex: 1 },
  overlay: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center' },
  card: {
    backgroundColor: colors.bgRaised,
    borderColor: colors.glassEdge,
    borderWidth: 1,
    borderRadius: radius.lg,
    padding: space[6],
    gap: space[3],
    minWidth: 280,
    alignItems: 'flex-start',
  },
  title: { color: colors.textPrimary, fontSize: type.title.size, fontFamily: fontFamily.uiSemiBold },
  reason: { color: colors.textSecondary, fontSize: type.body.size, fontFamily: fontFamily.ui, maxWidth: 360 },
});
