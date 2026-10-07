// Connection-failed card texts: what happened, what to do, and which button helps (pure, tested).

export type FailureAction = 'retry' | 'bluetooth' | 'settings' | 'pairing';

export type FailureCard = { title: string; body: string; action: FailureAction };

const CARDS: Record<string, FailureCard> = {
  module_not_found: {
    title: "The bridge didn't answer",
    body: 'Is the vehicle on? Another app may be connected to it: close VESC Tool or any other app that uses the vehicle, then try again.',
    action: 'retry',
  },
  taken_by_other: {
    title: 'Another device took the connection',
    body: 'A bridge talks to one app at a time. Close the other app or phone, then try again.',
    action: 'retry',
  },
  vesc_not_answering: {
    title: 'The VESC is not answering',
    body: 'The bridge connected but the controller stayed quiet. In VESC Tool check the UART app, 115200 baud, RX/TX wiring, and that the bridge is enabled.',
    action: 'retry',
  },
  link_errors: {
    title: 'Too many link errors',
    body: 'Replies kept arriving damaged. Move the phone closer and try again.',
    action: 'retry',
  },
  link_lost: {
    title: 'Connection lost',
    body: 'The bridge went out of range or lost power. Try again when it is nearby.',
    action: 'retry',
  },
  not_a_bridge: {
    title: 'Not a VESC bridge?',
    body: 'This device has no serial (UART) service the app can use. Choose another device.',
    action: 'retry',
  },
  firmware_too_old: {
    title: 'Firmware too old',
    body: 'The app needs VESC firmware 5.03 or newer. Update it in VESC Tool.',
    action: 'retry',
  },
  bluetooth_off: {
    title: 'Bluetooth is off',
    body: 'Turn Bluetooth on, then try again.',
    action: 'bluetooth',
  },
  permission_connect: {
    title: 'Nearby devices permission needed',
    body: 'The app needs the nearby devices permission to connect. Allow it in the app settings.',
    action: 'settings',
  },
  permission_scan: {
    title: 'Nearby devices permission needed',
    body: 'The app needs the nearby devices permission to search. Allow it in the app settings.',
    action: 'settings',
  },
  pairing_needed: {
    title: 'Pairing needed',
    body: 'This bridge asks for pairing. Try again and accept the pairing request (the PIN is set in VESC Tool).',
    action: 'pairing',
  },
  no_controllers: {
    title: 'No motor controller found',
    body: 'The bridge answered but no motor controller replied. Check that the controller is powered.',
    action: 'retry',
  },
};

/** The card for a failure code; null when there is nothing to show (no failure, or the rider disconnected). */
export function failureCard(code: string | null | undefined): FailureCard | null {
  if (!code || code === 'user_disconnect' || code === 'replay_ended') return null;
  return (
    CARDS[code] ?? {
      title: 'Connection failed',
      body: 'Try again, or choose another device.',
      action: 'retry',
    }
  );
}
