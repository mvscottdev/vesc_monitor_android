import { type NativeModule, requireNativeModule } from 'expo';

import type {
  AlertThresholds,
  BackgroundStatus,
  BatteryInfo,
  LiveState,
  RideRow,
  RideSeries,
  RunRecord,
  TransportKind,
  VescEvents,
} from './src/types';

export type * from './src/types';

declare class VescNativeModule extends NativeModule<VescEvents> {
  getLiveState(): Promise<LiveState>;
  /** Starts or stops the user scan; resolves to a plain-language reason when it cannot start. */
  scan(on: boolean): Promise<string | null>;
  /** Connects with the current transport; `address` is needed for BLE only. */
  connect(address: string | null): Promise<void>;
  disconnect(): Promise<void>;
  setTransport(kind: TransportKind, controllers: number | null, path: string | null): Promise<void>;
  /** BLE bench toggles; applied at the next connect. */
  setPollConfig(selective: boolean, depth: number, forwardLocal: boolean): Promise<void>;
  /** Starts a JSONL capture of the live session; resolves to its file path. */
  startRecording(): Promise<string | null>;
  stopRecording(): Promise<string | null>;
  listRecordings(): Promise<string[]>;
  /** Ride logging on or off (persisted); on while connected opens a ride at once. */
  setLogging(on: boolean): Promise<void>;
  /** Recorded rides, newest first. */
  listRides(limit: number): Promise<RideRow[]>;
  /** A stored ride downsampled to about `buckets` time buckets; null when it has no samples. */
  rideSeries(id: number, buckets: number): Promise<RideSeries | null>;
  /** Deletes a ride and its samples (the open ride cannot be deleted). */
  deleteRide(id: number): Promise<void>;
  /** Clears latched faults the rider has seen. */
  dismissFaults(): Promise<void>;
  /** Alert keys the rider switched off (persisted). */
  alertSettings(): Promise<string[]>;
  setAlertEnabled(key: string, on: boolean): Promise<void>;
  /** Alert keys that show the banner only, never a sound or notification (persisted). */
  silentAlerts(): Promise<string[]>;
  /** Sets the pack capacity by hand (Ah), or null to learn it; returns a reason when refused. */
  setCapacity(ah: number | null): Promise<string | null>;
  /** Sets the cell voltage shown as 0 % (null = the chemistry's default); resolves to a reason when rejected. */
  setEmptyCell(v: number | null): Promise<string | null>;
  setAlertSilent(key: string, silent: boolean): Promise<void>;
  /** The app is on screen (reconnect retries stay fast while it is). */
  setVisible(visible: boolean): Promise<void>;
  /** Default and rider thresholds of the editable alerts. */
  alertThresholds(): Promise<AlertThresholds>;
  /** Sets one alert's thresholds (null restores the defaults); resolves to a reason when rejected. */
  setAlertThresholds(key: string, values: number[] | null): Promise<string | null>;
  /** Default and rider [clear margin, repeat s] of the editable alerts. */
  alertTuning(): Promise<AlertThresholds>;
  /** Sets one alert's [clear margin, repeat s] (null restores the defaults); resolves to a reason when rejected. */
  setAlertTuning(key: string, values: number[] | null): Promise<string | null>;
  /** Posts a sample alert notification. */
  testAlert(): Promise<void>;
  /** Live checks for running in the background; null before the module is ready. */
  backgroundStatus(): Promise<BackgroundStatus | null>;
  /** Opens the system battery optimisation list; false when the phone has none. */
  openBatterySettings(): Promise<boolean>;
  /** A UI setting stored on the phone (keys start with `ui.`); null when unset. */
  uiSetting(key: string): Promise<string | null>;
  /** Stores a UI setting; null removes it. */
  setUiSetting(key: string, value: string | null): Promise<void>;
  /** Battery detection state; null while no session exists. */
  batteryInfo(): Promise<BatteryInfo | null>;
  /** Confirms or enters the pack; nulls return to auto-detection. Resolves to a reason when rejected. */
  setPack(cells: number | null, chemistry: number | null): Promise<string | null>;
  /** Arms a speed-test run; resolves to a plain reason when it cannot. */
  armRun(): Promise<string | null>;
  cancelRun(): Promise<void>;
  /** Saved speed-test runs, newest first. */
  listRuns(limit: number): Promise<RunRecord[]>;
  deleteRun(id: number): Promise<void>;
}

export default requireNativeModule<VescNativeModule>('Vesc');
