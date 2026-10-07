// A Lucide icon drawn with Skia from the generated path strings (stroke 2 on a 24 grid).
import { Canvas, Group, Path, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';

import { type IconName, icons } from '@/theme/icons';

export function Icon({ name, size = 24, color }: { name: IconName; size?: number; color: string }) {
  const path = useMemo(() => Skia.Path.MakeFromSVGString(icons[name]), [name]);
  if (!path) return null;
  const k = size / 24;
  return (
    <Canvas style={{ width: size, height: size }}>
      <Group transform={[{ scale: k }]}>
        <Path path={path} color={color} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" />
      </Group>
    </Canvas>
  );
}
