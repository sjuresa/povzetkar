import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { resolveInitialTheme, useThemeStore } from './themeStore';

describe('resolveInitialTheme', () => {
  it('prefers an explicit stored choice over the system preference', () => {
    expect(resolveInitialTheme('light', true)).toBe('light');
    expect(resolveInitialTheme('dark', false)).toBe('dark');
  });

  it('ignores an unknown stored value', () => {
    expect(resolveInitialTheme('system', true)).toBe('dark');
    expect(resolveInitialTheme('system', false)).toBe('light');
  });

  it('follows the system preference when nothing is stored', () => {
    expect(resolveInitialTheme(null, true)).toBe('dark');
    expect(resolveInitialTheme(null, false)).toBe('light');
  });
});

describe('themeStore initial state', () => {
  const originalMatchMedia = globalThis.matchMedia;

  function prefersDark(dark: boolean) {
    globalThis.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === '(prefers-color-scheme: dark)' ? dark : false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })) as unknown as typeof globalThis.matchMedia;
  }

  async function freshStore() {
    vi.resetModules();
    return (await import('./themeStore')).useThemeStore;
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => {
    globalThis.matchMedia = originalMatchMedia;
    localStorage.clear();
  });

  it('defaults to the system preference when nothing is stored', async () => {
    prefersDark(false);
    expect((await freshStore()).getState().theme).toBe('light');

    prefersDark(true);
    expect((await freshStore()).getState().theme).toBe('dark');
  });

  it('hydrates a stored choice over the system preference', async () => {
    prefersDark(false);
    localStorage.setItem('theme-storage', JSON.stringify({ state: { theme: 'dark' }, version: 0 }));

    expect((await freshStore()).getState().theme).toBe('dark');
  });

  it('ignores an unknown stored value and falls back to the system preference', async () => {
    prefersDark(false);
    localStorage.setItem('theme-storage', JSON.stringify({ state: { theme: 'system' }, version: 0 }));

    expect((await freshStore()).getState().theme).toBe('light');
  });
});

describe('themeStore', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('should update theme correctly', () => {
    const { setTheme } = useThemeStore.getState();
    setTheme('dark');
    expect(useThemeStore.getState().theme).toBe('dark');

    setTheme('light');
    expect(useThemeStore.getState().theme).toBe('light');
  });

  it('should persist theme to localStorage', () => {
    const { setTheme } = useThemeStore.getState();
    setTheme('dark');

    const persistedState = JSON.parse(localStorage.getItem('theme-storage') || '{}');
    expect(persistedState.state.theme).toBe('dark');
  });
});
