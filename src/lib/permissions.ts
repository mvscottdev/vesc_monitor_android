import { PermissionsAndroid, Platform } from 'react-native';

export type PermissionResult = { ok: true } | { ok: false; message: string };

/** Asks for the Bluetooth (and notification) permissions needed to scan and connect. */
export async function requestBlePermissions(): Promise<PermissionResult> {
  if (Platform.OS !== 'android') return { ok: false, message: 'Android only.' };
  const P = PermissionsAndroid.PERMISSIONS;
  const result = await PermissionsAndroid.requestMultiple([
    P.BLUETOOTH_SCAN,
    P.BLUETOOTH_CONNECT,
    P.POST_NOTIFICATIONS,
  ]);
  const granted = PermissionsAndroid.RESULTS.GRANTED;
  if (result[P.BLUETOOTH_CONNECT] !== granted) {
    return {
      ok: false,
      message: 'Bluetooth permission denied: cannot connect. Allow "Nearby devices" in Settings.',
    };
  }
  if (result[P.BLUETOOTH_SCAN] !== granted) {
    return {
      ok: false,
      message: 'Bluetooth permission denied: cannot search. Allow "Nearby devices" in Settings.',
    };
  }
  return { ok: true };
}
