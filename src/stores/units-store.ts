import Vesc from '@modules/vesc';
import { create } from 'zustand';

import { METRIC, parseUnits, type Units, UNITS_KEY } from '@/settings/units';

type UnitsStore = { units: Units; setUnits: (u: Units) => void };

/** Display units, loaded once from the phone and saved on change. */
export const useUnitsStore = create<UnitsStore>()((set) => ({
  units: METRIC,
  setUnits: (units) => {
    set({ units });
    Vesc.setUiSetting(UNITS_KEY, JSON.stringify(units)).catch(() => undefined);
  },
}));

export function loadUnits(): void {
  Vesc.uiSetting(UNITS_KEY)
    .then((json) => useUnitsStore.setState({ units: parseUnits(json) }))
    .catch(() => undefined);
}

export const useUnits = () => useUnitsStore((s) => s.units);
