// Phone-maker power savers that can stop a background connection on top of Android's
// own battery optimisation. Menu paths differ between OS versions; these are the usual
// ones and say so on screen.

export type VendorSteps = { name: string; steps: string[] };

const OPPO_FAMILY: VendorSteps = {
  name: 'OnePlus / OPPO / realme',
  steps: [
    'Settings › Apps › this app › Battery usage › allow background activity',
    'Recent apps: pull the app card down to lock it',
  ],
};

const VENDORS: Record<string, VendorSteps> = {
  oneplus: OPPO_FAMILY,
  oppo: OPPO_FAMILY,
  realme: OPPO_FAMILY,
  samsung: {
    name: 'Samsung',
    steps: ['Settings › Battery › Background usage limits › Never sleeping apps › add this app'],
  },
  xiaomi: {
    name: 'Xiaomi / Redmi / POCO',
    steps: [
      'Settings › Apps › Manage apps › this app › Battery saver › No restrictions',
      'Same page: turn on Autostart',
    ],
  },
  huawei: {
    name: 'Huawei / Honor',
    steps: ['Settings › Battery › App launch › this app › Manage manually, with all three switches on'],
  },
  vivo: {
    name: 'vivo',
    steps: ['Settings › Battery › Background power consumption management › this app › Allow'],
  },
  google: { name: 'Google Pixel', steps: [] },
};
VENDORS.redmi = VENDORS.xiaomi!;
VENDORS.poco = VENDORS.xiaomi!;
VENDORS.honor = VENDORS.huawei!;

const GENERIC: VendorSteps = {
  name: 'Your phone maker',
  steps: ['Look for a battery or background setting for this app and allow it to run in the background'],
};

export function vendorSteps(manufacturer: string | null | undefined): VendorSteps {
  return VENDORS[(manufacturer ?? '').toLowerCase().trim()] ?? GENERIC;
}

type Flags = { nearby: number; notifications: number; batteryOptimized: number };

/** True when something can stop or silence the app in the background (warn dot in Settings). */
export function backgroundNeedsAttention(s: Flags | null): boolean {
  if (!s) return false;
  return s.nearby !== 1 || s.notifications !== 1 || s.batteryOptimized === 1;
}
