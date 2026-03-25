import { useThemeStore } from '../../store/themeStore';
import { Sun, Moon } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export function ThemeToggle() {
  const { theme, setTheme } = useThemeStore();
  const { t } = useTranslation();

  const toggleTheme = () => {
    setTheme(theme === 'light' ? 'dark' : 'light');
  };

  const isDark = theme === 'dark';
  const Icon = isDark ? Moon : Sun;
  const label = isDark ? t('common.theme_settings.dark') : t('common.theme_settings.light');

  return (
    <button
      onClick={toggleTheme}
      className="p-2 rounded-xl transition-all glass hover:bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)] hover:text-blue-400 group relative flex items-center justify-center border border-[var(--color-border-subtle)]"
    >
      <Icon className="w-5 h-5 transition-transform group-active:scale-95" />
      <span className="absolute -bottom-10 opacity-0 group-hover:opacity-100 transition-opacity bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] text-[var(--color-text-primary)] text-[10px] px-2 py-1 rounded whitespace-nowrap z-50 shadow-xl pointer-events-none">
        {t('common.theme')}: {label}
      </span>
    </button>
  );
}
