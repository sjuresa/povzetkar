import { describe, it, expect, vi, afterEach } from 'vitest';

// Serve empty translations instead of fetching /locales/*.json.
vi.mock('i18next-http-backend', () => ({
  default: {
    type: 'backend',
    init: () => undefined,
    read: (_lng: string, _ns: string, callback: (err: unknown, data: object) => void) => callback(null, {}),
  },
}));

async function loadI18n(navigatorLanguage: string, stored?: string) {
  vi.resetModules();
  localStorage.clear();
  if (stored) localStorage.setItem('povzetkar-language', stored);
  vi.spyOn(globalThis.navigator, 'language', 'get').mockReturnValue(navigatorLanguage);
  // i18next itself is a node_modules singleton that resetModules does not recreate, so a repeated
  // init finishes asynchronously — callers wait for the language they expect.
  return (await import('./config')).default;
}

describe('i18n config', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('starts in Slovenian for a Slovenian browser and mirrors it into <html lang>', async () => {
    const i18n = await loadI18n('sl-SI');

    await vi.waitFor(() => expect(i18n.language).toBe('sl'));
    expect(document.documentElement.lang).toBe('sl');
  });

  it('starts in the stored language over the browser language', async () => {
    const i18n = await loadI18n('sl-SI', 'en');

    await vi.waitFor(() => expect(i18n.language).toBe('en'));
    expect(document.documentElement.lang).toBe('en');
  });

  it('does not store the detected language as a choice', async () => {
    const i18n = await loadI18n('sl-SI');

    await vi.waitFor(() => expect(i18n.language).toBe('sl'));
    expect(localStorage.getItem('povzetkar-language')).toBeNull();
  });
});
