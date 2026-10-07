'use no memo';
// One ride-mode page: a single Skia canvas with the page background and every widget.
import { Canvas, Rect, RadialGradient, vec } from '@shopify/react-native-skia';
import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';

import { WidgetView } from './canvas/widgets';
import { cellBox } from './geometry';
import type { Page } from './layout';
import { useDashFonts } from '@/theme/fonts';
import { colors } from '@/theme/tokens';

type Props = { page: Page; multi: boolean; localId: number | null };

export function DashboardPage({ page, multi, localId }: Props) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const fonts = useDashFonts();
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };
  const ready = fonts != null && size.width > 0;
  return (
    <View style={styles.fill} onLayout={onLayout}>
      {ready && (
        <Canvas style={styles.fill}>
          <Rect x={0} y={0} width={size.width} height={size.height} color={colors.bg}>
            <RadialGradient
              c={vec(size.width / 2, size.height * 0.2)}
              r={Math.max(size.width, size.height) * 0.7}
              colors={[colors.bgGlow, colors.bg]}
            />
          </Rect>
          {page.widgets.map((w) => (
            <WidgetView
              key={w.id}
              widget={w}
              box={cellBox(w, size.width, size.height, page.cols, page.rows)}
              fonts={fonts}
              multi={multi}
              localId={localId}
            />
          ))}
        </Canvas>
      )}
    </View>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
