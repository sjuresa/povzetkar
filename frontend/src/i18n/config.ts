import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import HttpBackend from 'i18next-http-backend';
import { readStoredLanguage, resolveInitialLanguage } from './language';

// Fires on init too, so it only mirrors the language into <html lang>; the choice is stored by LanguageSwitcher.
i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng;
});

void i18n
  .use(HttpBackend)
  .use(initReactI18next)
  .init({
    fallbackLng: 'en',
    lng: resolveInitialLanguage(readStoredLanguage(), globalThis.navigator?.language),
    debug: false,
    interpolation: {
      escapeValue: false, 
    },
    backend: {
      loadPath: '/locales/{{lng}}.json',
    },
    // Using a single namespace for simplicity in this project's single-file-per-language setup
    defaultNS: 'translation',
    ns: ['translation'],
  });

export default i18n;
