import Vesc, { type ActiveAlert, type AlertsEvent, type ScanEvent, type SessionEvent } from '@modules/vesc';
import { useEffect } from 'react';
import { AppState } from 'react-native';
import { create } from 'zustand';

import { alertId } from '@/alerts/texts';
import { frameValue } from '@/telemetry/frame';

type SessionStore = {
  session: SessionEvent | null;
  scan: ScanEvent;
  alerts: ActiveAlert[];
  /** When the UI first saw each alert firing (by alertId), for the banner's auto-hide. */
  alertSeen: ReadonlyMap<string, number>;
  setSession: (s: SessionEvent) => void;
  setScan: (s: ScanEvent) => void;
  setAlerts: (e: AlertsEvent) => void;
};

/** Warm state (≤ 5 updates/s): connection, topology, bench numbers, scan results. */
export const useSessionStore = create<SessionStore>()((set) => ({
  session: null,
  scan: { scanning: false, devices: [] },
  setSession: (session) => set({ session }),
  alerts: [],
  setScan: (scan) => set({ scan }),
  alertSeen: new Map(),
  setAlerts: (e) =>
    set((st) => {
      const now = Date.now();
      const seen = new Map<string, number>();
      for (const a of e.alerts) seen.set(alertId(a), st.alertSeen.get(alertId(a)) ?? now);
      return { alerts: e.alerts, alertSeen: seen };
    }),
}));

async function pullLiveState(): Promise<void> {
  const live = await Vesc.getLiveState();
  const { setSession, setScan, setAlerts } = useSessionStore.getState();
  setSession(live.session);
  setScan(live.scan);
  if (live.alerts) setAlerts(live.alerts);
  frameValue.value = live.frame;
}

/** Attaches to the running session: pulls the live state, then follows events. */
export function useSessionFeed(): void {
  useEffect(() => {
    const { setSession, setScan, setAlerts } = useSessionStore.getState();
    const subs = [
      Vesc.addListener('session', setSession),
      Vesc.addListener('scan', setScan),
      Vesc.addListener('alerts', setAlerts),
    ];
    void pullLiveState();
    const appState = AppState.addEventListener('change', (s) => {
      if (s === 'active') void pullLiveState();
    });
    return () => {
      subs.forEach((s) => s.remove());
      appState.remove();
    };
  }, []);
}
