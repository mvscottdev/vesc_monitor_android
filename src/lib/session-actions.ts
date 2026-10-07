import Vesc from '@modules/vesc';
import * as Sharing from 'expo-sharing';

import { requestBlePermissions } from '@/lib/permissions';

/** Starts or stops the user scan; returns a message to show, if any. */
export async function toggleScan(on: boolean): Promise<string | null> {
  if (on) {
    const p = await requestBlePermissions();
    if (!p.ok) return p.message;
  }
  return Vesc.scan(on);
}

/** Connects to a BLE bridge (after the permissions) or starts the selected dev transport. */
export async function connect(address: string | null): Promise<string | null> {
  if (address) {
    const p = await requestBlePermissions();
    if (!p.ok) return p.message;
  }
  await Vesc.connect(address);
  return null;
}

/** Stops the capture and opens the share sheet for the file. */
export async function stopAndShareRecording(): Promise<void> {
  const path = await Vesc.stopRecording();
  if (path && (await Sharing.isAvailableAsync())) {
    await Sharing.shareAsync(`file://${path}`, {
      mimeType: 'application/x-ndjson',
      dialogTitle: 'Export capture',
    });
  }
}
