// Controller names: storage key, parsing and the label shown (pure, tested).
import { controllerLabel } from '@/lib/format';

export type Names = Record<number, string>;

export const NAME_MAX = 16;

/** One names entry per set of controllers, like the dashboard layouts. */
export function namesKey(ids: number[]): string {
  return `ui.names.${[...ids].sort((a, b) => a - b).join('-') || 'none'}`;
}

export function parseNames(json: string | null): Names {
  if (!json) return {};
  try {
    const v = JSON.parse(json) as unknown;
    if (!v || typeof v !== 'object') return {};
    const out: Names = {};
    for (const [k, name] of Object.entries(v as Record<string, unknown>)) {
      const id = Number(k);
      if (Number.isInteger(id) && typeof name === 'string' && name.trim())
        out[id] = name.trim().slice(0, NAME_MAX);
    }
    return out;
  } catch {
    return {};
  }
}

export function controllerName(id: number, local: boolean, names: Names): string {
  return names[id] ?? controllerLabel(id, local);
}
