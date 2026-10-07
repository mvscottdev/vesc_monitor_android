import Vesc, { type TelemetryFrameV1 } from '@modules/vesc';
import { useEffect } from 'react';
import { makeMutable } from 'react-native-reanimated';

/** The one hot value: the latest telemetry frame. Widgets read it through derived values. */
export const frameValue = makeMutable<TelemetryFrameV1 | null>(null);

/** Keeps [frameValue] fed while mounted (the native side only emits while observed). */
export function useTelemetryFeed(): void {
  useEffect(() => {
    const sub = Vesc.addListener('telemetry', (frame) => {
      const current = frameValue.value;
      // Late frames from an older session are ignored.
      if (current && frame.generation < current.generation) return;
      frameValue.value = frame;
    });
    return () => sub.remove();
  }, []);
}
