import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import Vesc from '@modules/vesc';
import { useEffect } from 'react';
import { AppState } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { useSessionFeed } from '@/stores/session-store';
import { useNamesFeed } from '@/stores/names-store';
import { loadUnits } from '@/stores/units-store';
import { useTelemetryFeed } from '@/telemetry/frame';
import { useAppFonts } from '@/theme/fonts';
import { colors } from '@/theme/tokens';

export default function RootLayout() {
  useSessionFeed();
  useTelemetryFeed();
  useNamesFeed();
  useEffect(loadUnits, []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => {
      Vesc.setVisible(s === 'active').catch(() => undefined);
    });
    return () => sub.remove();
  }, []);
  const fontsReady = useAppFonts();
  if (!fontsReady) return null;
  return (
    <GestureHandlerRootView style={{ flex: 1, backgroundColor: colors.bg }}>
      <StatusBar hidden />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg } }} />
    </GestureHandlerRootView>
  );
}
