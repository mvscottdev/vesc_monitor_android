'use no memo';
// Radial speed gauge: 250° sweep, track, value arc, ticks, centre number, a white tick
// at the session max. The value arc's `end` is its only live path geometry (a trim,
// not a new path per frame). The scale max follows the session max (model.value2).
import { Group, Line, Path, Skia, Text, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { Box } from '../geometry';
import type { WidgetModel } from '../metrics';
import { speedUnit } from '@/settings/units';
import { useUnits } from '@/stores/units-store';
import { colors, stroke, type } from '@/theme/tokens';
import type { DashFonts } from '@/theme/fonts';

export const SWEEP_DEG = 250;
const START_DEG = 90 + (360 - SWEEP_DEG) / 2;
const TICKS = 10;

function geometry(box: Box) {
  const cx = box.x + box.width / 2;
  const r = Math.min(box.width / 2, box.height * 0.56) - 18;
  const cy = box.y + Math.max(r + 18, box.height / 2 + 8);
  return { cx, cy, r };
}

/** Tick marks as one SVG path string, built once per size. */
function ticksSvg(cx: number, cy: number, r: number): string {
  const parts: string[] = [];
  for (let i = 0; i <= TICKS; i++) {
    const a = ((START_DEG + (SWEEP_DEG * i) / TICKS) * Math.PI) / 180;
    const r1 = r - 16;
    const r2 = r - (i % 2 === 0 ? 26 : 21);
    parts.push(
      `M${(cx + r1 * Math.cos(a)).toFixed(1)} ${(cy + r1 * Math.sin(a)).toFixed(1)}` +
        `L${(cx + r2 * Math.cos(a)).toFixed(1)} ${(cy + r2 * Math.sin(a)).toFixed(1)}`,
    );
  }
  return parts.join('');
}

export function SpeedGauge({
  box,
  model,
  fonts,
  chip,
}: {
  box: Box;
  model: SharedValue<WidgetModel>;
  fonts: DashFonts;
  /** Combine rule with 2+ controllers; shown after the source label. */
  chip: string | null;
}) {
  const { cx, cy, r } = geometry(box);
  const maxAngle = useDerivedValue(() => ((START_DEG + SWEEP_DEG * model.value.peak) * Math.PI) / 180);
  const maxP1 = useDerivedValue(() =>
    vec(cx + (r - 22) * Math.cos(maxAngle.value), cy + (r - 22) * Math.sin(maxAngle.value)),
  );
  const maxP2 = useDerivedValue(() =>
    vec(cx + (r + 10) * Math.cos(maxAngle.value), cy + (r + 10) * Math.sin(maxAngle.value)),
  );
  const maxOpacity = useDerivedValue(() => (model.value.peak > 0 ? 1 : 0));
  const arcColor = useDerivedValue(() => (model.value.zone === 'warn' ? colors.state.warn : colors.accent));
  const paths = useMemo(() => {
    const arc = Skia.Path.Make();
    arc.addArc(Skia.XYWHRect(cx - r, cy - r, 2 * r, 2 * r), START_DEG, SWEEP_DEG);
    return { arc, ticks: Skia.Path.MakeFromSVGString(ticksSvg(cx, cy, r)) };
  }, [cx, cy, r]);
  const end = useDerivedValue(() => Math.max(0, Math.min(1, model.value.fraction)));
  const value = useDerivedValue(() => model.value.value);
  const size = Math.min(type.hero.size, r * 0.8);
  const numFont = fonts.font('display', size);
  const unitFont = fonts.font('label', type.unit.size);
  const chipFont = fonts.font('label', type.label.size - 1);
  const valueX = useDerivedValue(() => cx - numFont.measureText(model.value.value).width / 2);
  const unitText = speedUnit(useUnits());
  const unitW = unitFont.measureText(unitText).width;
  const chipText = useDerivedValue(() =>
    (chip && model.value.label === 'VESC'
      ? `${model.value.label} · ${chip}`
      : model.value.label
    ).toUpperCase(),
  );
  const chipX = useDerivedValue(() => cx - chipFont.measureText(chipText.value).width / 2);
  const sub = useDerivedValue(() => model.value.sub);
  const subFont = fonts.font('label', type.caption.size);
  const subX = useDerivedValue(() => cx - subFont.measureText(model.value.sub).width / 2);
  const scaleText = useDerivedValue(() => model.value.value2);
  return (
    <Group>
      <Path
        path={paths.arc}
        color={colors.divider}
        style="stroke"
        strokeWidth={stroke.gaugeTrack}
        strokeCap="round"
      />
      <Path
        path={paths.arc}
        color={arcColor}
        style="stroke"
        strokeWidth={stroke.gaugeValue}
        strokeCap="round"
        start={0}
        end={end}
      />
      {paths.ticks && (
        <Path
          path={paths.ticks}
          color={colors.textTertiary}
          style="stroke"
          strokeWidth={stroke.hairline * 2}
        />
      )}
      <Group opacity={maxOpacity}>
        <Line p1={maxP1} p2={maxP2} color={colors.textPrimary} strokeWidth={3} strokeCap="round" />
      </Group>
      <Text x={chipX} y={cy - size * 0.62} text={chipText} font={chipFont} color={colors.textTertiary} />
      <Text x={valueX} y={cy + size * 0.35} text={value} font={numFont} color={colors.metric.speed} />
      <Text
        x={cx - unitW / 2}
        y={cy + size * 0.35 + 22}
        text={unitText}
        font={unitFont}
        color={colors.textSecondary}
      />
      <Text x={subX} y={cy + r * 0.78} text={sub} font={subFont} color={colors.textSecondary} />
      <Text
        x={cx + (r - 30) * Math.cos(((START_DEG + SWEEP_DEG) * Math.PI) / 180) - 8}
        y={cy + (r - 30) * Math.sin(((START_DEG + SWEEP_DEG) * Math.PI) / 180) + 16}
        text={scaleText}
        font={subFont}
        color={colors.textTertiary}
      />
    </Group>
  );
}
