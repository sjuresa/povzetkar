import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

export type Theme = 'light' | 'dark';

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

/**
 * Picks the theme for the first render: an explicit earlier choice wins, otherwise the system preference.
 * Keep in sync with the pre-paint script in index.html, which applies the same rule before React loads.
 */
export function resolveInitialTheme(stored: string | null, prefersDark: boolean): Theme {
  if (stored === 'light' || stored === 'dark') return stored;
  return prefersDark ? 'dark' : 'light';
}

function prefersDarkScheme(): boolean {
  // Without matchMedia keep the historical default (dark).
  return globalThis.matchMedia?.('(prefers-color-scheme: dark)').matches ?? true;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      // A stored choice is hydrated over this default by `persist`.
      theme: resolveInitialTheme(null, prefersDarkScheme()),
      setTheme: (theme) => set({ theme }),
    }),
    {
      // The pre-paint script in index.html reads this key ({"state":{"theme":…}}); rename both together.
      name: 'theme-storage',
      storage: createJSONStorage(() => localStorage),
      // Validate the stored value with the same rule as the pre-paint script, so an unknown value
      // (e.g. an old "system") cannot flip the theme after load.
      merge: (persisted, current) => ({
        ...current,
        theme: resolveInitialTheme((persisted as Partial<ThemeState> | undefined)?.theme ?? null, prefersDarkScheme()),
      }),
    }
  )
);
