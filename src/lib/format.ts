/** Short label for a controller: its CAN ID, marked when wired to the bridge. */
export function controllerLabel(controllerId: number, local: boolean): string {
  return local ? `ID ${controllerId} (local)` : `ID ${controllerId}`;
}

/** Ride time as m:ss, or h:mm:ss from one hour. */
export function formatDuration(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const ss = String(s % 60).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

/** Signal bars 0..4 from RSSI (dBm). */
export function signalBars(rssi: number): number {
  if (rssi >= -60) return 4;
  if (rssi >= -70) return 3;
  if (rssi >= -80) return 2;
  if (rssi >= -90) return 1;
  return 0;
}

/** Second line of the reconnect banner, e.g. "Attempt 3. The ride keeps recording." */
export function reconnectDetail(attempt: number, recording: boolean): string {
  const a = attempt > 0 ? `Attempt ${attempt}.` : 'Retrying.';
  return recording ? `${a} The ride keeps recording.` : a;
}
