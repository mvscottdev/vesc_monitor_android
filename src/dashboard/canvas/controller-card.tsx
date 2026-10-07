'use no memo';
// One controller's detail card (2+ controllers only): currents, power, duty, temps, fault.
import { Group, Text } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { Box } from '../geometry';
import { type CardModel, cardModel, type Zone } from '../metrics';
import { Chip, PAD, Panel, useStaleOpacity } from './primitives';
import { useControllerName } from '@/stores/names-store';
import { speedUnit, tempUnit, type Units } from '@/settings/units';
import { useUnits } from '@/stores/units-store';
import { frameValue } from '@/telemetry/frame';
import { colors, type } from '@/theme/tokens';
import type { DashFonts } from '@/theme/fonts';

type Row = { label: string; key: 'speed' | 'motorA' | 'batteryA' | 'power' | 'duty' | 'temps'; unit: string };

const rows = (u: Units): Row[] => [
  { label: 'Speed', key: 'speed', unit: speedUnit(u) },
  { label: 'Motor current', key: 'motorA', unit: 'A' },
  { label: 'Battery current', key: 'batteryA', unit: 'A' },
  { label: 'Power', key: 'power', unit: '' },
  { label: 'Duty', key: 'duty', unit: '%' },
  { label: 'Motor · FET', key: 'temps', unit: tempUnit(u) },
];

export function ControllerCard({
  box,
  controllerId,
  local,
  fonts,
}: {
  box: Box;
  controllerId: number;
  local: boolean;
  fonts: DashFonts;
}) {
  const units = useUnits();
  const ROWS = rows(units);
  const name = useControllerName(controllerId, local);
  const model = useDerivedValue(() => cardModel(frameValue.value, controllerId, units));
  const zone = useDerivedValue<Zone>(() => (model.value.faulted ? 'crit' : 'none'));
  const opacity = useStaleOpacity(model);
  const titleFont = fonts.font('label', type.heading.size);
  const rowH = Math.min(34, (box.height - 2 * PAD - 60) / ROWS.length);
  const rowsTop = box.y + PAD + 40;
  return (
    <Group>
      <Panel box={box} zone={zone} />
      <Text
        x={box.x + PAD}
        y={box.y + PAD + type.heading.size}
        text={name}
        font={titleFont}
        color={colors.textPrimary}
      />
      <Chip box={box} text={local ? 'Local' : `CAN ${controllerId}`} fonts={fonts} />
      <Group opacity={opacity}>
        {ROWS.map((row, i) => (
          <CardRow
            key={row.label}
            box={box}
            y={rowsTop + (i + 1) * rowH}
            row={row}
            model={model}
            fonts={fonts}
          />
        ))}
      </Group>
      <Footer box={box} model={model} fonts={fonts} />
    </Group>
  );
}

function CardRow({
  box,
  y,
  row,
  model,
  fonts,
}: {
  box: Box;
  y: number;
  row: Row;
  model: SharedValue<CardModel>;
  fonts: DashFonts;
}) {
  const labelFont = fonts.font('label', type.body.size);
  const valueFont = fonts.font('display', type.numS.size);
  const unitFont = fonts.font('label', type.caption.size);
  const key = row.key;
  const text = useDerivedValue(() => model.value[key]);
  const unitW = row.unit ? unitFont.measureText(row.unit).width + 4 : 0;
  const right = box.x + box.width - PAD;
  const x = useDerivedValue(() => right - unitW - valueFont.measureText(model.value[key]).width);
  return (
    <Group>
      <Text x={box.x + PAD} y={y} text={row.label} font={labelFont} color={colors.textSecondary} />
      <Text x={x} y={y} text={text} font={valueFont} color={colors.textPrimary} />
      {row.unit !== '' && (
        <Text x={right - unitW + 4} y={y} text={row.unit} font={unitFont} color={colors.textTertiary} />
      )}
    </Group>
  );
}

/** Fault line (latched per controller by the firmware's own fault code) and the stale age. */
function Footer({ box, model, fonts }: { box: Box; model: SharedValue<CardModel>; fonts: DashFonts }) {
  const font = fonts.font('label', type.caption.size);
  const text = useDerivedValue(() =>
    model.value.stale ? `No data · ${model.value.age}` : model.value.fault,
  );
  const color = useDerivedValue(() =>
    model.value.faulted ? colors.state.crit : model.value.stale ? colors.state.stale : colors.state.ok,
  );
  return <Text x={box.x + PAD} y={box.y + box.height - PAD} text={text} font={font} color={color} />;
}
