import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';

export function BriefingDetailPage() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="w-24 h-24 rounded-3xl bg-[var(--color-accent-soft)] border border-[var(--color-accent)]/20 shadow-inner flex items-center justify-center mb-4">
        <svg
          className="w-12 h-12 text-[var(--color-accent)]"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
          />
        </svg>
      </div>
      
      <div className="space-y-4">
        <h1 className="text-5xl font-extrabold tracking-tight text-[var(--color-text-primary)] text-glow">
          Briefing Details
        </h1>
        <p className="text-[var(--color-text-secondary)] max-w-lg mx-auto text-lg leading-relaxed">
          We are currently polishing the AI visualization for detailed briefings. Check back soon for the full experience.
        </p>
      </div>

      <div className="flex items-center space-x-4">
        <Link
          to="/dashboard"
          className="px-8 py-3 rounded-2xl glass hover:bg-[var(--color-surface-hover)] text-[var(--color-text-primary)] transition-all border border-[var(--color-border-subtle)] font-bold shadow-sm active:scale-95"
        >
          {t('navigation.dashboard')}
        </Link>
      </div>
      
      {/* Decorative background element */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-[var(--color-accent)]/5 rounded-full blur-[100px] -z-10"></div>
    </div>
  );
}
