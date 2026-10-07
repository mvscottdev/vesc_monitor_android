// Bundled fonts (SIL OFL, via @expo-google-fonts): Barlow Semi Condensed for numbers,
// IBM Plex Sans for words. RN text uses them through expo-font; Skia loads the same TTFs.
import { BarlowSemiCondensed_600SemiBold } from '@expo-google-fonts/barlow-semi-condensed/600SemiBold';
import { IBMPlexSans_400Regular } from '@expo-google-fonts/ibm-plex-sans/400Regular';
import { IBMPlexSans_500Medium } from '@expo-google-fonts/ibm-plex-sans/500Medium';
import { IBMPlexSans_600SemiBold } from '@expo-google-fonts/ibm-plex-sans/600SemiBold';
import { type SkFont, type SkTypeface, Skia, useTypeface } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import { useMemo } from 'react';

/** Font family names for RN `Text` styles. */
export const fontFamily = {
  display: 'BarlowSemiCondensed_600SemiBold',
  ui: 'IBMPlexSans_400Regular',
  uiMedium: 'IBMPlexSans_500Medium',
  uiSemiBold: 'IBMPlexSans_600SemiBold',
} as const;

/** Loads the RN fonts once at the root; true when ready (or failed: system fonts are used). */
export function useAppFonts(): boolean {
  const [loaded, error] = useFonts({
    BarlowSemiCondensed_600SemiBold,
    IBMPlexSans_400Regular,
    IBMPlexSans_500Medium,
    IBMPlexSans_600SemiBold,
  });
  return loaded || error != null;
}

export type DashFonts = {
  display: SkTypeface;
  label: SkTypeface;
  /** Sized fonts are cached per (face, size). */
  font: (face: 'display' | 'label', size: number) => SkFont;
};

/** Skia typefaces for the dashboard canvas; null until both are loaded. */
export function useDashFonts(): DashFonts | null {
  const display = useTypeface(BarlowSemiCondensed_600SemiBold);
  const label = useTypeface(IBMPlexSans_600SemiBold);
  return useMemo(() => {
    if (!display || !label) return null;
    const cache = new Map<string, SkFont>();
    return {
      display,
      label,
      font: (face, size) => {
        const key = `${face}:${Math.round(size)}`;
        let f = cache.get(key);
        if (!f) {
          f = Skia.Font(face === 'display' ? display : label, Math.round(size));
          cache.set(key, f);
        }
        return f;
      },
    };
  }, [display, label]);
}
