export type SupportedLanguage = 'sl' | 'en';

// Keep in sync with the pre-paint script in index.html, which reads the same key to set <html lang>.
export const LANGUAGE_STORAGE_KEY = 'povzetkar-language';

/**
 * Picks the language for the first render: an explicit earlier choice wins, otherwise the browser
 * language decides (Slovenian browsers get Slovenian), otherwise English.
 */
export function resolveInitialLanguage(stored: string | null, navigatorLang: string | undefined): SupportedLanguage {
  if (stored === 'sl' || stored === 'en') return stored;
  return navigatorLang?.toLowerCase().startsWith('sl') ? 'sl' : 'en';
}

export function readStoredLanguage(): string | null {
  try {
    return localStorage.getItem(LANGUAGE_STORAGE_KEY);
  } catch {
    return null; // storage unavailable (private mode, blocked site data)
  }
}

/** Stores an explicit user choice; called only from the language toggle, never on init. */
export function storeLanguage(lng: string): void {
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, lng);
  } catch {
    // storage unavailable — the choice lasts for this page only
  }
}
