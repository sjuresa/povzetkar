import { Link } from '@tanstack/react-router';
import { useAuthStore } from '../store/authStore';
import { useShallow } from 'zustand/react/shallow';
import { useTranslation } from 'react-i18next';

export function LandingPage() {
  const { t } = useTranslation();
  const { login, isAuthenticated } = useAuthStore(
    useShallow((state) => ({
      login: state.login,
      isAuthenticated: state.isAuthenticated,
    }))
  );

  return (
    <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] flex flex-col items-center justify-center p-6 text-center animate-in fade-in duration-1000">
      <div className="max-w-4xl space-y-12">
        <div className="space-y-4">
          <h1 className="text-6xl md:text-8xl font-black tracking-tighter bg-gradient-to-br from-blue-500 via-indigo-500 to-emerald-500 bg-clip-text text-transparent animate-gradient drop-shadow-sm">
            Povzetkar
          </h1>
          <p className="text-2xl md:text-3xl text-[var(--color-text-secondary)] leading-relaxed max-w-2xl mx-auto font-medium opacity-90">
            Your daily AI-powered content briefing. <br />
            <span className="text-[var(--color-text-primary)]">Stay informed without the noise.</span>
          </p>
        </div>
        
        <div className="flex flex-col sm:flex-row items-center justify-center gap-6 pt-8">
          {isAuthenticated ? (
            <Link
              to="/dashboard"
              className="px-10 py-5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-2xl font-bold text-xl transition-all transform hover:scale-105 shadow-xl shadow-emerald-900/20 active:scale-95 flex items-center gap-3"
            >
              <span>{t('navigation.dashboard')}</span>
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2/5} d="M13 7l5 5m0 0l-5 5m5-5H6" /></svg>
            </Link>
          ) : (
            <button
              onClick={login}
              className="px-10 py-5 bg-[var(--color-accent)] hover:opacity-90 text-white rounded-2xl font-bold text-xl transition-all transform hover:scale-105 shadow-xl shadow-blue-900/20 active:scale-95 flex items-center gap-3"
            >
              <span>Start Your Briefing</span>
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" /></svg>
            </button>
          )}
          <a
            href="#features"
            className="px-10 py-5 bg-[var(--color-surface-elevated)] hover:bg-[var(--color-surface-hover)] text-[var(--color-text-primary)] rounded-2xl font-bold text-xl border border-[var(--color-border-subtle)] transition-all active:scale-95 shadow-sm"
          >
            Learn More
          </a>
        </div>
      </div>
      
      {/* Decorative background elements */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none -z-10 opacity-30">
        <div className="absolute top-1/4 left-1/4 w-[500px] h-[500px] bg-blue-500/10 rounded-full blur-[120px] animate-pulse"></div>
        <div className="absolute bottom-1/4 right-1/4 w-[600px] h-[600px] bg-emerald-500/10 rounded-full blur-[120px] animate-pulse delay-1000"></div>
      </div>
    </div>
  );
}
