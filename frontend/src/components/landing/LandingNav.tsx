import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';

interface LandingNavProps {
  readonly isAuthenticated: boolean;
  readonly theme: string;
  readonly toggleLanguage: () => void;
  readonly toggleTheme: () => void;
}

export function LandingNav({ isAuthenticated, theme, toggleLanguage, toggleTheme }: LandingNavProps) {
  const { t, i18n } = useTranslation();
  
  return (
    <nav className="fixed top-0 w-full h-20 border-b-2 border-[var(--color-border-subtle)] bg-[var(--color-background-base)]/80 backdrop-blur-md flex items-center justify-between px-8 md:px-16 z-50">
      <div className="flex items-center gap-8">
        <Link to="/" className="flex items-center gap-2 group outline-none" aria-label="Povzetkar Home">
          <div className="w-8 h-8 bg-[var(--color-accent)] shadow-[4px_4px_0px_black] group-hover:scale-105 transition-transform"></div>
          <span className="text-xl font-black tracking-tighter uppercase italic group-hover:text-[var(--color-accent)] transition-colors">Povzetkar</span>
        </Link>
        
        {/* Quick Settings */}
        <div className="hidden md:flex items-center gap-6 border-l-2 border-[var(--color-border-subtle)] pl-8">
           <button 
             onClick={toggleLanguage} 
             className="text-[10px] font-black uppercase tracking-widest hover:text-[var(--color-accent)] transition-colors"
             aria-label={t('landing.settings.language')}
            >
             {i18n.language === 'en' ? 'SL' : 'EN'}
           </button>
           <button 
             onClick={toggleTheme}
             className="text-[10px] font-black uppercase tracking-widest hover:text-[var(--color-accent)] transition-colors"
             aria-label={t('landing.settings.title')}
           >
             {theme === 'dark' ? t('landing.settings.light') : t('landing.settings.dark')}
           </button>
        </div>
      </div>
      <div>
        {isAuthenticated && (
          <Link to="/dashboard" className="text-xs font-black uppercase tracking-widest hover:text-[var(--color-accent)] transition-all flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            {t('navigation.dashboard')}
          </Link>
        )}
      </div>
    </nav>
  );
}
