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
      className="p-2 rounded-xl transition-all bg-[var(--color-bg-secondary)] border border-[var(--color-border-primary)] hover:border-[var(--color-action-primary)] text-[var(--color-text-secondary)] hover:text-[var(--color-action-primary)] group relative flex items-center justify-center outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-ring-focus)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-bg-primary)]"
    >
      <Icon className="w-5 h-5 transition-transform group-active:scale-95" strokeWidth={1.5} />
      <span className="absolute -bottom-10 opacity-0 group-hover:opacity-100 transition-opacity bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-text-primary)] text-[10px] px-2 py-1 rounded whitespace-nowrap z-50 pointer-events-none uppercase tracking-tighter">
        {t('common.theme')}: {label}
      </span>
    </button>
  );
}
