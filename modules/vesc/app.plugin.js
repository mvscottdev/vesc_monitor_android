// Config plugin for the VESC module: Bluetooth and foreground-service permissions.
// The service itself is declared in the module's own AndroidManifest.xml.
const { withAndroidManifest } = require('expo/config-plugins');

const PERMISSIONS = [
  // Scan results are never used to derive location.
  ['android.permission.BLUETOOTH_SCAN', { 'android:usesPermissionFlags': 'neverForLocation' }],
  ['android.permission.BLUETOOTH_CONNECT', {}],
  ['android.permission.POST_NOTIFICATIONS', {}],
  ['android.permission.FOREGROUND_SERVICE', {}],
  ['android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE', {}],
];

function withVescPermissions(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults.manifest;
    const existing = manifest['uses-permission'] ?? [];
    const names = new Set(PERMISSIONS.map(([name]) => name));
    const kept = existing.filter((p) => !names.has(p.$['android:name']));
    manifest['uses-permission'] = [
      ...kept,
      ...PERMISSIONS.map(([name, extra]) => ({ $: { 'android:name': name, ...extra } })),
    ];
    return cfg;
  });
}

module.exports = withVescPermissions;
