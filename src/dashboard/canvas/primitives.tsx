'use no memo';
// Canvas building blocks for ride-mode widgets. Everything here draws inside the one
// page canvas; live props are derived values, static props are plain numbers.
import { Group, Line, RoundedRect, Text, vec } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { Box } from '../geometry';
import type { WidgetModel, Zone } from '../metrics';
import { colors, radius, stroke, type } from '@/theme/tokens';
import type { DashFonts } from '@/theme/fonts';

export const PAD = 14;

export function zoneColor(zone: Zone, fallback: string): string {
  'worklet';
  if (zone === 'ok') return colors.state.ok;
  if (zone === 'warn') return colors.state.warn;
  if (zone === 'crit') return colors.state.crit;
  return fallback;
}

/** Static glass: flat fill, 1 dp edge (zone-coloured when warn/crit), top highlight. No blur. */
export function Panel({ box, zone }: { box: Box; zone?: SharedValue<Zone> }) {
  const edge = useDerivedValue(() => {
    const z = zone?.value ?? 'none';
    return z === 'warn' || z === 'crit' ? zoneColor(z, colors.glassEdge) : colors.glassEdge;
  });
  const r = radius.md;
  return (
    <Group>
      <RoundedRect x={box.x} y={box.y} width={box.width} height={box.height} r={r} color={colors.glassFill} />
      <RoundedRect
        x={box.x + 0.5}
        y={box.y + 0.5}
        width={box.width - 1}
        height={box.height - 1}
        r={r}
        color={edge}
        style="stroke"
        strokeWidth={stroke.edge}
      />
      <Line
        p1={vec(box.x + r, box.y + 1.5)}
        p2={vec(box.x + box.width - r, box.y + 1.5)}
        color={colors.glassHighlight}
        strokeWidth={stroke.hairline}
      />
    </Group>
  );
}

/** Uppercase widget label, top left. */
export function HeaderLabel({ box, text, fonts }: { box: Box; text: string; fonts: DashFonts }) {
  const font = fonts.font('label', type.label.size);
  return (
    <Text
      x={box.x + PAD}
      y={box.y + PAD + type.label.size}
      text={text.toUpperCase()}
      font={font}
      color={colors.textSecondary}
    />
  );
}

/** Small outline chip, top right: combine rule (Combined, Sum, Max) or link (Local, CAN 17). */
export function Chip({ box, text, fonts }: { box: Box; text: string; fonts: DashFonts }) {
  const font = fonts.font('label', type.label.size - 1);
  const label = text.toUpperCase();
  const w = font.measureText(label).width + 16;
  const h = 18;
  const x = box.x + box.width - PAD - w;
  const y = box.y + PAD - 3;
  return (
    <Group>
      <RoundedRect
        x={x}
        y={y}
        width={w}
        height={h}
        r={h / 2}
        color={colors.glassEdge}
        style="stroke"
        strokeWidth={stroke.hairline}
      />
      <Text x={x + 8} y={y + 13} text={label} font={font} color={colors.textTertiary} />
    </Group>
  );
}

/** Age tag for stale values ("4 s"), bottom right. */
export function StaleTag({
  box,
  model,
  fonts,
}: {
  box: Box;
  model: SharedValue<WidgetModel>;
  fonts: DashFonts;
}) {
  const font = fonts.font('label', type.caption.size);
  const text = useDerivedValue(() => model.value.age);
  const x = useDerivedValue(() => box.x + box.width - PAD - font.measureText(model.value.age).width);
  return <Text x={x} y={box.y + box.height - PAD + 2} text={text} font={font} color={colors.state.stale} />;
}

/** Opacity for stale content: 35 % as in the design states. */
export function useStaleOpacity<T extends { stale: boolean }>(model: SharedValue<T>) {
  return useDerivedValue(() => (model.value.stale ? 0.35 : 1));
}

/** Number + unit on one baseline; the unit follows the number's measured width. */
export function ValueText({
  x,
  y,
  size,
  value,
  unit,
  color,
  fonts,
}: {
  x: number;
  y: number;
  size: number;
  value: SharedValue<string>;
  unit: SharedValue<string> | string;
  color: SharedValue<string> | string;
  fonts: DashFonts;
}) {
  const numFont = fonts.font('display', size);
  const unitFont = fonts.font('label', type.unit.size);
  const unitX = useDerivedValue(() => x + numFont.measureText(value.value).width + 6);
  return (
    <Group>
      <Text x={x} y={y} text={value} font={numFont} color={color} />
      <Text x={unitX} y={y} text={unit} font={unitFont} color={colors.textSecondary} />
    </Group>
  );
}

/** Horizontal bar with a zone-coloured fill (fraction 0..1). */
export function Bar({
  x,
  y,
  width,
  fraction,
  color,
}: {
  x: number;
  y: number;
  width: number;
  fraction: SharedValue<number>;
  color: SharedValue<string>;
}) {
  const h = 8;
  const fillW = useDerivedValue(() => Math.max(0, Math.min(1, fraction.value)) * width);
  return (
    <Group>
      <RoundedRect x={x} y={y} width={width} height={h} r={h / 2} color={colors.divider} />
      <RoundedRect x={x} y={y} width={fillW} height={h} r={h / 2} color={color} />
    </Group>
  );
}

/** Bidirectional power bar: regen left of zero (teal), drive right (amber), peak ticks. */
export function PowerBar({ box, model, y }: { box: Box; model: SharedValue<WidgetModel>; y: number }) {
  const h = 10;
  const x = box.x + PAD;
  const width = box.width - 2 * PAD;
  // Zero sits at a third of the bar: drive needs more room than regen.
  const zeroX = x + width / 3;
  const driveW = width - width / 3;
  const regenW = width / 3;
  const fillX = useDerivedValue(() =>
    model.value.fraction < 0 ? zeroX + model.value.fraction * regenW : zeroX,
  );
  const fillW = useDerivedValue(() =>
    model.value.fraction < 0 ? -model.value.fraction * regenW : model.value.fraction * driveW,
  );
  const fillColor = useDerivedValue(() =>
    model.value.fraction < 0 ? colors.metric.regen : colors.metric.drive,
  );
  const peakDrive = useDerivedValue(() => zeroX + model.value.peak * driveW);
  const peakRegen = useDerivedValue(() => zeroX + model.value.peak2 * regenW);
  const p1 = useDerivedValue(() => vec(peakDrive.value, y - 3));
  const p2 = useDerivedValue(() => vec(peakDrive.value, y + h + 3));
  const r1 = useDerivedValue(() => vec(peakRegen.value, y - 3));
  const r2 = useDerivedValue(() => vec(peakRegen.value, y + h + 3));
  return (
    <Group>
      <RoundedRect x={x} y={y} width={width} height={h} r={h / 2} color={colors.divider} />
      <RoundedRect x={fillX} y={y} width={fillW} height={h} r={3} color={fillColor} />
      <Line p1={vec(zeroX, y - 2)} p2={vec(zeroX, y + h + 2)} color={colors.textTertiary} strokeWidth={2} />
      <Line p1={p1} p2={p2} color={colors.textPrimary} strokeWidth={stroke.hairline} />
      <Line p1={r1} p2={r2} color={colors.textPrimary} strokeWidth={stroke.hairline} />
    </Group>
  );
}
