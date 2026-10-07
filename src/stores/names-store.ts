// Rider-given controller names (e.g. Front / Rear), saved per set of controllers on the phone.
import Vesc from '@modules/vesc';
import { useEffect } from 'react';
import { create } from 'zustand';

import { controllerName, namesKey, parseNames, type Names } from '@/lib/names';
import { useSessionStore } from '@/stores/session-store';

type NamesStore = { key: string | null; names: Names };

export const useNamesStore = create<NamesStore>()(() => ({ key: null, names: {} }));

/** Loads the names whenever the connected set of controllers changes. */
export function useNamesFeed(): void {
  const ids = useSessionStore((s) => (s.session?.controllers ?? []).map((c) => c.controllerId).join(','));
  useEffect(() => {
    if (!ids) return;
    const key = namesKey(ids.split(',').map(Number));
    if (useNamesStore.getState().key === key) return;
    useNamesStore.setState({ key, names: {} });
    Vesc.uiSetting(key)
      .then((json) => {
        if (useNamesStore.getState().key === key) useNamesStore.setState({ names: parseNames(json) });
      })
      .catch(() => undefined);
  }, [ids]);
}

/** Sets or clears (empty text) one controller's name and saves it. */
export function setControllerName(id: number, name: string): void {
  const { key, names } = useNamesStore.getState();
  if (!key) return;
  const next = { ...names };
  const trimmed = name.trim();
  if (trimmed) next[id] = trimmed;
  else delete next[id];
  useNamesStore.setState({ names: next });
  Vesc.setUiSetting(key, JSON.stringify(next)).catch(() => undefined);
}

/** The display name of a controller: the rider's name, else its ID label. */
export function useControllerName(id: number, local: boolean): string {
  return useNamesStore((s) => controllerName(id, local, s.names));
}
