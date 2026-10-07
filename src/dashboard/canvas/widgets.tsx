'use no memo';
// Ride-mode widgets drawn in the page canvas. One model mapper per widget reads the
// frame shared value; everything else derives from that model.
import { Group, Text } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { Box } from '../geometry';
import type { Widget, WidgetType } from '../layout';
import { type WidgetModel, widgetModel, type Zone } from '../metrics';
import { ControllerCard } from './controller-card';
import {
  Bar,
  Chip,
  HeaderLabel,
  PAD,
  Panel,
  PowerBar,
  StaleTag,
  useStaleOpacity,
  ValueText,
  zoneColor,
} from './primitives';
import { SpeedGauge } from './speed-gauge';
import { useUnits } from '@/stores/units-store';
import { frameValue } from '@/telemetry/frame';
import { colors, type } from '@/theme/tokens';
import type { DashFonts } from '@/theme/fonts';

const LABEL: Record<WidgetType, string> = {
  speed: 'Speed',
  battery: 'Battery',
  range: 'Range',
  sag: 'Sag',
  voltage: 'Voltage',
  current: 'Battery current',
  power: 'Power',
  temperature: 'Temperature',
  duty: 'Duty',
  controller: 'Controller',
};

/** How a combined widget merges controllers (shown only with 2+ controllers). */
const RULE: Record<WidgetType, string> = {
  speed: 'Combined',
  battery: 'Combined',
  range: 'Combined',
  sag: 'Combined',
  voltage: 'Combined',
  current: 'Sum',
  power: 'Sum',
  temperature: 'Hottest',
  duty: 'Max',
  controller: '',
};

type Props = { widget: Widget; box: Box; fonts: DashFonts; multi: boolean; localId: number | null };

export function WidgetView({ widget, box, fonts, multi, localId }: Props) {
  if (widget.type === 'controller') {
    const id = widget.source === 'combined' ? -1 : widget.source.controllerId;
    return <ControllerCard box={box} controllerId={id} local={id === localId} fonts={fonts} />;
  }
  return <MetricWidget widget={widget} box={box} fonts={fonts} multi={multi} />;
}

function MetricWidget({ widget, box, fonts, multi }: Omit<Props, 'localId'>) {
  const { type: kind, source } = widget;
  const units = useUnits();
  const model = useDerivedValue(() => widgetModel(frameValue.value, kind, source, units));
  const zone = useDerivedValue<Zone>(() =>
    model.value.zone === 'crit' || model.value.zone2 === 'crit'
      ? 'crit'
      : model.value.zone === 'warn' || model.value.zone2 === 'warn'
        ? 'warn'
        : 'none',
  );
  const opacity = useStaleOpacity(model);
  const chip = multi && source === 'combined' ? RULE[kind] : null;
  return (
    <Group>
      <Panel box={box} zone={zone} />
      {kind !== 'speed' && <HeaderLabel box={box} text={LABEL[kind]} fonts={fonts} />}
      {chip && kind !== 'speed' && <Chip box={box} text={chip} fonts={fonts} />}
      <Group opacity={opacity}>
        <Body kind={kind} box={box} model={model} fonts={fonts} chip={chip} />
      </Group>
      <StaleTag box={box} model={model} fonts={fonts} />
    </Group>
  );
}

function Body({
  kind,
  box,
  model,
  fonts,
  chip,
}: {
  kind: WidgetType;
  box: Box;
  model: SharedValue<WidgetModel>;
  fonts: DashFonts;
  chip: string | null;
}) {
  switch (kind) {
    case 'speed':
      return <SpeedGauge box={box} model={model} fonts={fonts} chip={chip} />;
    case 'power':
      return <PowerBody box={box} model={model} fonts={fonts} />;
    case 'temperature':
      return <TemperatureBody box={box} model={model} fonts={fonts} />;
    case 'battery':
      return <BatteryBody box={box} model={model} fonts={fonts} />;
    case 'range':
    case 'sag':
      return <FootBody box={box} model={model} fonts={fonts} />;
    default:
      return <NumberBody box={box} model={model} fonts={fonts} bar={kind === 'duty'} />;
  }
}

/** Value size that fits the widget height, capped by the type scale. */
function valueSize(box: Box, max: number): number {
  return Math.max(type.numM.size, Math.min(max, (box.height - 2 * PAD - 24) * 0.8));
}

function NumberBody({
  box,
  model,
  fonts,
  bar,
}: {
  box: Box;
  model: SharedValue<WidgetModel>;
  fonts: DashFonts;
  bar: boolean;
}) {
  const size = valueSize(box, type.numXL.size);
  const value = useDerivedValue(() => model.value.value);
  const unit = useDerivedValue(() => model.value.unit);
  const sub = useDerivedValue(() => model.value.sub);
  const color = useDerivedValue(() => zoneColor(model.value.zone, colors.textPrimary));
  const fraction = useDerivedValue(() => model.value.fraction);
  const barColor = useDerivedValue(() => zoneColor(model.value.zone, colors.metric.duty));
  const top = box.y + PAD + type.label.size + 8;
  const baseline = Math.min(top + size * 0.9 + (box.height - size - 60) * 0.3, box.y + box.height - PAD - 18);
  const bottom = box.y + box.height - PAD;
  return (
    <Group>
      <ValueText
        x={box.x + PAD}
        y={baseline}
        size={size}
        value={value}
        unit={unit}
        color={color}
        fonts={fonts}
      />
      {bar ? (
        <Bar
          x={box.x + PAD}
          y={bottom - 8}
          width={box.width - 2 * PAD}
          fraction={fraction}
          color={barColor}
        />
      ) : (
        <Text
          x={box.x + PAD}
          y={bottom}
          text={sub}
          font={fonts.font('label', type.caption.size)}
          color={colors.textSecondary}
        />
      )}
    </Group>
  );
}

function PowerBody({ box, model, fonts }: { box: Box; model: SharedValue<WidgetModel>; fonts: DashFonts }) {
  const size = valueSize(box, type.numL.size);
  const value = useDerivedValue(() => model.value.value);
  const unit = useDerivedValue(() => model.value.unit);
  const color = useDerivedValue(() => (model.value.fraction < 0 ? colors.metric.regen : colors.textPrimary));
  const sub = useDerivedValue(() => model.value.sub);
  const subFont = fonts.font('display', type.numS.size - 4);
  const subX = useDerivedValue(() => box.x + box.width - PAD - subFont.measureText(model.value.sub).width);
  const barY = box.y + box.height - PAD - 12;
  const baseline = Math.min(box.y + PAD + type.label.size + 8 + size * 0.9, barY - 10);
  return (
    <Group>
      {/* Right of the value: the top right corner belongs to the rule chip. */}
      <Text x={subX} y={baseline} text={sub} font={subFont} color={colors.textSecondary} />
      <ValueText
        x={box.x + PAD}
        y={baseline}
        size={size}
        value={value}
        unit={unit}
        color={color}
        fonts={fonts}
      />
      <PowerBar box={box} model={model} y={barY} />
    </Group>
  );
}

function TemperatureBody({
  box,
  model,
  fonts,
}: {
  box: Box;
  model: SharedValue<WidgetModel>;
  fonts: DashFonts;
}) {
  const size = valueSize(box, type.numL.size);
  const half = (box.width - 2 * PAD - 16) / 2;
  const motor = useDerivedValue(() => model.value.value);
  const fet = useDerivedValue(() => model.value.value2);
  const motorColor = useDerivedValue(() => zoneColor(model.value.zone, colors.textPrimary));
  const fetColor = useDerivedValue(() => zoneColor(model.value.zone2, colors.textPrimary));
  const motorBar = useDerivedValue(() => zoneColor(model.value.zone, colors.state.ok));
  const fetBar = useDerivedValue(() => zoneColor(model.value.zone2, colors.state.ok));
  const motorF = useDerivedValue(() => model.value.fraction);
  const fetF = useDerivedValue(() => model.value.fraction2);
  const barY = box.y + box.height - PAD - 8;
  const baseline = Math.min(box.y + PAD + type.label.size + 8 + size * 0.9, barY - 10);
  const x2 = box.x + PAD + half + 16;
  return (
    <Group>
      <ValueText
        x={box.x + PAD}
        y={baseline}
        size={size}
        value={motor}
        unit="°C motor"
        color={motorColor}
        fonts={fonts}
      />
      <ValueText x={x2} y={baseline} size={size} value={fet} unit="°C FET" color={fetColor} fonts={fonts} />
      <Bar x={box.x + PAD} y={barY} width={half} fraction={motorF} color={motorBar} />
      <Bar x={x2} y={barY} width={half} fraction={fetF} color={fetBar} />
    </Group>
  );
}

/** SoC number, zone-coloured bar, then pack voltage and V/cell, then the detected pack. */
function BatteryBody({ box, model, fonts }: { box: Box; model: SharedValue<WidgetModel>; fonts: DashFonts }) {
  const size = valueSize(box, type.numXL.size);
  const value = useDerivedValue(() => model.value.value);
  const unit = useDerivedValue(() => model.value.unit);
  const fraction = useDerivedValue(() => model.value.fraction);
  const barColor = useDerivedValue(() => zoneColor(model.value.zone, colors.metric.battery));
  const line1 = useDerivedValue(() => model.value.value2);
  const line2 = useDerivedValue(() => model.value.sub);
  const caption = fonts.font('label', type.caption.size);
  const bottom = box.y + box.height - PAD;
  const barY = bottom - 2 * (type.caption.size + 6) - 10;
  const baseline = Math.min(box.y + PAD + type.label.size + 8 + size * 0.9, barY - 10);
  return (
    <Group>
      <ValueText
        x={box.x + PAD}
        y={baseline}
        size={size}
        value={value}
        unit={unit}
        color={colors.textPrimary}
        fonts={fonts}
      />
      <Bar x={box.x + PAD} y={barY} width={box.width - 2 * PAD} fraction={fraction} color={barColor} />
      <Text
        x={box.x + PAD}
        y={bottom - type.caption.size - 6}
        text={line1}
        font={caption}
        color={colors.textSecondary}
      />
      <Text x={box.x + PAD} y={bottom} text={line2} font={caption} color={colors.textTertiary} />
    </Group>
  );
}

/** Number with a foot line (left: sub) and a right-hand note in the header row (value2). */
function FootBody({ box, model, fonts }: { box: Box; model: SharedValue<WidgetModel>; fonts: DashFonts }) {
  const size = valueSize(box, type.numL.size);
  const value = useDerivedValue(() => model.value.value);
  const unit = useDerivedValue(() => model.value.unit);
  const sub = useDerivedValue(() => model.value.sub);
  const note = useDerivedValue(() => model.value.value2);
  const caption = fonts.font('label', type.caption.size);
  const noteX = useDerivedValue(
    () => box.x + box.width - PAD - caption.measureText(model.value.value2).width,
  );
  const bottom = box.y + box.height - PAD;
  const baseline = Math.min(box.y + PAD + type.label.size + 8 + size * 0.9, bottom - type.caption.size - 10);
  return (
    <Group>
      <ValueText
        x={box.x + PAD}
        y={baseline}
        size={size}
        value={value}
        unit={unit}
        color={colors.textPrimary}
        fonts={fonts}
      />
      <Text x={box.x + PAD} y={bottom} text={sub} font={caption} color={colors.textSecondary} />
      <Text x={noteX} y={bottom} text={note} font={caption} color={colors.textSecondary} />
    </Group>
  );
}
