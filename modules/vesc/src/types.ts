// Types of the native module's events and state. The telemetry frame follows
// contract/telemetry-frame.v1.json (numbers only, flags are 0/1).

export type Flag = 0 | 1;

export type TelemetryFrameV1 = {
  v: 1;
  seq: number;
  tMs: number;
  generation: number;
  combined: {
    voltageV: number | null;
    fresh: number;
    total: number;
    partial: Flag;
    divergent: Flag;
    // Optional fields (added within v1): absent on older builds, null = unknown.
    /** Sum of signed battery currents (A). */
    currentInA?: number | null;
    /** Sum of signed motor currents (A). */
    currentMotorA?: number | null;
    /** Sum of each controller's own V x I (W); negative = regen. */
    powerW?: number | null;
    /** Largest |duty| over controllers, sign kept (0..1). */
    duty?: number | null;
    /** Hottest present sensor (degC). */
    tempMosC?: number | null;
    tempMotorC?: number | null;
    /** Session peaks: drive >= 0, regen <= 0 (W). */
    peakPowerW?: number;
    peakRegenW?: number;
    /** First non-zero fault of the session (0 = none) and its controller. */
    faultCode?: number;
    faultControllerId?: number | null;
    /** Combined speed (m/s); null when no fresh controller has a trusted speed. */
    speedMps?: number | null;
    /** 1 = from the VESC's own configuration, 0 = unknown. */
    speedSource?: number;
    /** A controller has no wheel configured (or the default one): set it in VESC Tool. */
    speedWheelMissing?: Flag;
    /** Controllers disagree beyond the tolerance (held 0.5 s). */
    slip?: Flag;
    /** Session maximum speed (m/s). */
    maxSpeedMps?: number;
    /** Session distance (m) and net energy (Wh); Wh per km once 200 m are ridden. */
    tripM?: number;
    whUsed?: number;
    whPerKm?: number | null;
    /** Display SoC (%), 0 % at the chemistry's empty point. */
    socPct?: number | null;
    cellV?: number | null;
    /** Detected series cell count and chemistry (1 NMC/NCA, 2 LFP). */
    cells?: number | null;
    chemistry?: number | null;
    /** 1 low, 2 medium, 3 high. */
    socConfidence?: number | null;
    /** Only the prior separates chemistries: the rider should confirm. */
    batteryConfirm?: Flag;
    /** Sag below the last rest reference (V), null without a reference. */
    sagV?: number | null;
    minVoltageV?: number | null;
    /** Learned pack capacity (Ah); null while learning. */
    capacityAh?: number | null;
    /** Range left (km) on the learned capacity and blended consumption; null while learning. */
    rangeKm?: number | null;
  };
  vescs: VescFrameEntry[];
};

export type VescFrameEntry = {
  controllerId: number;
  local: Flag;
  voltageV: number | null;
  fresh: Flag;
  /** Milliseconds since this controller's last sample. */
  ageMs?: number | null;
  tempMosC?: number | null;
  tempMotorC?: number | null;
  currentMotorA?: number | null;
  currentInA?: number | null;
  powerW?: number | null;
  duty?: number | null;
  erpm?: number | null;
  faultCode?: number;
  /** This controller's speed (m/s); null when unknown or stale. */
  speedMps?: number | null;
};

export type ConnectionState = 'idle' | 'scanning' | 'connecting' | 'connected' | 'reconnecting' | 'lost';

export type ControllerInfo = {
  controllerId: number;
  local: boolean;
  firmware: string;
  hardware: string;
  fresh: boolean;
  untestedFirmware: boolean;
  hz: number;
  requests: number;
  timeouts: number;
  writeMs: number;
  rttMs: number;
  notificationsPerReply: number;
  notificationSpanMs: number;
  batteryCutStartV: number | null;
  batteryCutEndV: number | null;
};

export type SessionEvent = {
  generation: number;
  state: ConnectionState;
  reason: string | null;
  /** The reason as a stable code (Reason enum name, lower case); absent on older builds. */
  reasonCode?: string | null;
  /** The BLE bridge of this session; null for the dev sources. */
  address?: string | null;
  /** Setup step while connecting; absent on older builds. */
  setupStep?: 'firmware' | 'controllers' | null;
  /** Motor controllers found so far. */
  setupFound?: number;
  /** CAN nodes that are not motor controllers (listed, never polled); absent on older builds. */
  otherNodes?: { canId: number; kind: 'bms' | 'module' | 'other'; hardware: string }[];
  /** Reconnect attempts since the link was lost (0 while connected); absent on older builds. */
  attempt?: number;
  mtu: number;
  firmware: string | null;
  hardware: string | null;
  selective: boolean;
  depth: number;
  forwardLocal: boolean;
  crcErrors: number;
  unmatched: number;
  notifications: number;
  controllers: ControllerInfo[];
  transport: string;
  recording: string | null;
  /** Ride logging switched on (persisted). */
  logging?: boolean;
  /** Time since the open ride started; null when no ride is recording. */
  rideElapsedMs?: number | null;
  /** Chunks the recorder had to drop (queue full) and store calls that failed. */
  droppedChunks?: number;
  storeErrors?: number;
  /** Phone storage nearly full: rides are not recorded. */
  storageLow?: boolean;
  /** Speed-test run state; null while no session exists. */
  run?: RunState | null;
};

export type RunPhase = 'idle' | 'armed' | 'staged' | 'running' | 'done' | 'aborted';

export type RunAbort =
  'false_start' | 'rolling_start' | 'lift_off' | 'link' | 'fault' | 'timeout' | 'arm_timeout' | 'cancelled';

export type RunBracket = {
  label: string;
  /** Elapsed from the 1 ft rollout start (headline), ms; null = not reached. */
  rolloutMs: number | null;
  /** Elapsed from first motion, ms. */
  firstMotionMs: number | null;
};

export type RunState = {
  state: RunPhase;
  abort: RunAbort | null;
  /** Running time at the event (ms); extrapolate locally between events. */
  elapsedMs: number | null;
  brackets: RunBracket[];
  peakSpeedMps: number;
  peakPowerW: number;
  minVoltageV: number | null;
  maxTempMosC: number | null;
  maxTempMotorC: number | null;
  /** Wheel slip before 20 km/h. */
  slip: Flag;
  /** Rollout could not be integrated; times start from first motion. */
  startEstimated: Flag;
  sampleRateHz: number;
  jitterP95Ms: number;
  /** Learned speed ratio (m/s per ERPM), mean over controllers. */
  k: number | null;
};

/** One recorded ride with its exact summary. */
export type RideRow = {
  id: number;
  /** closed, or recovered after the app was killed mid-ride. */
  state: 'closed' | 'recovered' | 'recording';
  startWallMs: number;
  /** "index:canId:L|C" per controller, comma-separated. */
  streams: string;
  bytes: number;
  durationMs: number;
  whUsed: number;
  whRegen: number;
  peakPowerW: number;
  peakRegenW: number;
  peakCurrentInA: number;
  minVoltageV: number | null;
  maxTempMotorC: number | null;
  maxTempFetC: number | null;
  /** Distinct non-zero fault codes, comma-separated. */
  faults: string;
};

export type ScanDevice = {
  address: string;
  name: string | null;
  rssi: number;
  looksLikeVesc: boolean;
};

export type ScanEvent = {
  scanning: boolean;
  devices: ScanDevice[];
};

/** A saved speed-test run as stored: `json` holds a RunState (without elapsedMs) plus `curve`. */
export type RunRecord = { id: number; startWallMs: number; json: string };

export type AlertSeverity = 'info' | 'warning' | 'critical';

/** One active alert from the native engine; texts live in the UI, keyed by `key`. */
export type ActiveAlert = {
  key: string;
  /** Null for vehicle-wide alerts (battery, sag, divergence). */
  controllerId: number | null;
  severity: AlertSeverity;
  /** The value that triggered it (degC, %, V, V/cell, duty 0..1, ms, fault code, count). */
  value: number;
  sinceMs: number;
  /** Fired or escalated on this evaluation (outside its cooldown). */
  notify: Flag;
  /** A latched fault whose code has returned to 0. */
  cleared: Flag;
};

export type AlertsEvent = { alerts: ActiveAlert[] };

export type LiveState = {
  frame: TelemetryFrameV1 | null;
  alerts?: AlertsEvent;
  session: SessionEvent;
  scan: ScanEvent;
};

export type TransportKind = 'ble' | 'synthetic' | 'replay';

export type VescEvents = {
  telemetry: (frame: TelemetryFrameV1) => void;
  session: (event: SessionEvent) => void;
  scan: (event: ScanEvent) => void;
  alerts: (event: AlertsEvent) => void;
};

/** A battery pack: series cell count and chemistry code (1 NMC/NCA, 2 LFP). */
export type PackCandidate = { cells: number; chemistry: number; p: number };

/** Battery detection state for the confirm screen. Voltages in V. */
export type BatteryInfo = {
  /** The pack the rider confirmed or entered; null follows auto-detection. */
  confirmed: PackCandidate | null;
  /** The detector's best guess; null until a quiet reading. */
  detected: PackCandidate | null;
  /** 1 low, 2 medium, 3 high. */
  confidence: number | null;
  /** 1 when only the chemistry prior separates the top candidates. */
  confirmChemistry: number;
  alternatives: PackCandidate[];
  vRest: number | null;
  vMaxSeen: number | null;
  cutStartV: number | null;
  cutEndV: number | null;
  /** Capacity the rider entered (Ah); null when learned. */
  capacityAh: number | null;
  /** Cell voltage the rider set as 0 %; null uses the default. */
  emptyCellV?: number | null;
  /** The chemistry's default empty cell voltage; null without a pack. */
  defaultEmptyCellV?: number | null;
  /** Learned capacity (Ah); null until enough discharge has been seen. */
  learnedAh: number | null;
  learnIntervals: number;
  learnNeeded: number;
};

/** Per bucket statistics; null where the bucket had no value. */
export type SeriesBand = { mean: (number | null)[]; min: (number | null)[]; max: (number | null)[] };

/** A stored ride downsampled for charts (fixed time buckets from startMs, session clock). */
export type RideSeries = {
  startMs: number;
  bucketMs: number;
  buckets: number;
  powerW: SeriesBand;
  voltageV: SeriesBand;
  tempFetC: SeriesBand;
  tempMotorC: SeriesBand;
  /** 0..1, highest controller. */
  duty: SeriesBand;
  erpm: SeriesBand;
  /** m/s, mean over controllers; absent on rides recorded before speed was stored. */
  speedMps?: SeriesBand;
  /** Stored alert code per bucket in `max` (keyIndex × 4 + severity; 0 none); absent on older rides. */
  alert?: SeriesBand;
};

/** What Android may use to stop the connection in the background. Flags: 1 = yes. */
export type BackgroundStatus = {
  /** Nearby devices (Bluetooth) permission granted. */
  nearby: Flag;
  notifications: Flag;
  /** The app is subject to battery optimisation (Android may stop it). */
  batteryOptimized: Flag;
  /** Lower-case phone maker, for vendor-specific steps. */
  manufacturer: string;
  sdk: number;
};

/** Alert thresholds in native units, one per level (lowest severity first). */
export type AlertThresholds = {
  defaults: Record<string, number[]>;
  overrides: Record<string, number[]>;
};
