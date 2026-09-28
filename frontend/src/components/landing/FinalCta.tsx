import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { ArrowRight, LogIn } from 'lucide-react';
import { CTA_INVERTED } from './buttonStyles';

interface FinalCtaProps {
  readonly isAuthenticated: boolean;
  readonly onLogin: () => void;
}

export function FinalCta({ isAuthenticated, onLogin }: FinalCtaProps) {
  const { t } = useTranslation();

  return (
    <section className="max-w-6xl mx-auto px-4 sm:px-8 pb-16 md:pb-24">
      <div className="rounded-xl md:rounded-[28px] bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] px-6 py-10 sm:px-12 sm:py-14 flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div className="space-y-2">
          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">{t('landing.final.title')}</h2>
          <p className="opacity-80">{t('landing.final.subtitle')}</p>
        </div>
        {/* Inverted button: the band uses the action colour, so the button uses the page background. */}
        {isAuthenticated ? (
          <Link
            to="/dashboard"
            className={`${CTA_INVERTED} shrink-0`}
          >
            {t('landing.hero.cta_dashboard')}
            <ArrowRight className="w-5 h-5" strokeWidth={1.5} />
          </Link>
        ) : (
          <button
            onClick={onLogin}
            data-testid="final-cta"
            className={`${CTA_INVERTED} shrink-0`}
          >
            <LogIn className="w-5 h-5" strokeWidth={1.5} />
            {t('landing.hero.cta')}
          </button>
        )}
      </div>
    </section>
  );
}
