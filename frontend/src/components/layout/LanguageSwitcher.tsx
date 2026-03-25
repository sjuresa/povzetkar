import { useTranslation } from 'react-i18next';
import { Languages } from 'lucide-react';

export function LanguageSwitcher() {
  const { i18n } = useTranslation();

  const languages = [
    { code: 'en', label: 'English', flag: '🇺🇸' },
    { code: 'sl', label: 'Slovenščina', flag: '🇸🇮' },
  ];

  const toggleLanguage = () => {
    const nextLang = i18n.language === 'en' ? 'sl' : 'en';
    void i18n.changeLanguage(nextLang);
  };

  const currentLang = languages.find((l) => l.code === i18n.language) || languages[0];

  return (
    <button
      onClick={toggleLanguage}
      className="px-3 py-2 rounded-xl transition-all bg-[var(--color-surface-elevated)] hover:bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)] hover:text-blue-400 group relative flex items-center space-x-2 border border-[var(--color-border-subtle)]"
    >
      <Languages className="w-5 h-5 transition-transform group-active:scale-95" />
      <span className="text-[10px] font-bold uppercase tracking-widest hidden lg:block">
        {currentLang.code}
      </span>
      <span className="absolute -bottom-10 opacity-0 group-hover:opacity-100 transition-opacity bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] text-[var(--color-text-primary)] text-[10px] px-2 py-1 rounded whitespace-nowrap z-50 shadow-xl pointer-events-none">
        {currentLang.label} {currentLang.flag}
      </span>
    </button>
  );
}
