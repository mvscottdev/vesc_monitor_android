// Connect setup steps (connect-detect mockup): what is done, what runs now (pure, tested).
import type { SessionEvent } from '@modules/vesc';

export type StepState = 'done' | 'now' | 'todo';
export type SetupStepRow = { label: string; sub: string; state: StepState };

/** The four setup rows for a connecting or connected session; empty otherwise. */
export function setupSteps(s: SessionEvent | null | undefined): SetupStepRow[] {
  if (!s || (s.state !== 'connecting' && s.state !== 'connected')) return [];
  const live = s.state === 'connected';
  const step = live ? 3 : s.setupStep === 'controllers' ? 2 : s.setupStep === 'firmware' ? 1 : 0;
  const at = (i: number): StepState => (i < step ? 'done' : i === step ? 'now' : 'todo');
  const found = live ? s.controllers.length : (s.setupFound ?? 0);
  return [
    {
      label: step > 0 ? 'Connected' : 'Connecting to the bridge',
      sub: step > 0 && s.mtu > 0 ? `${Math.max(20, s.mtu - 3)} B packets` : 'Bluetooth LE',
      state: at(0),
    },
    {
      label: step > 1 && s.firmware ? `Firmware ${s.firmware}` : 'Reading firmware',
      sub: step > 1 && s.hardware ? `hw ${s.hardware}` : 'VESC on the bridge',
      state: at(1),
    },
    {
      label: step > 2 ? `${found} motor controller${found === 1 ? '' : 's'}` : 'Looking for more controllers',
      sub: `CAN bus · ${found} found`,
      state: at(2),
    },
    { label: live ? 'Live data' : 'Start live data', sub: 'Read-only', state: live ? 'done' : 'todo' },
  ];
}
