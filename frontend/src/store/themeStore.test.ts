import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useThemeStore } from './themeStore';

describe('themeStore', () => {
  beforeEach(() => {
    // Clear the store before each test
    const { setTheme } = useThemeStore.getState();
    setTheme('system');
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('should have "system" as default theme', () => {
    expect(useThemeStore.getState().theme).toBe('system');
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
