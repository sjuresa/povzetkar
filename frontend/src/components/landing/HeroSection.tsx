import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { ArrowRight, LogIn } from 'lucide-react';
import { SampleBriefing } from './SampleBriefing';
import { CTA_PRIMARY } from './buttonStyles';

interface HeroSectionProps {
  readonly isAuthenticated: boolean;
  readonly onLogin: () => void;
}

export function HeroSection({ isAuthenticated, onLogin }: HeroSectionProps) {
  const { t } = useTranslation();

  return (
    <section className="relative overflow-hidden">
      {/* Decoration: a soft glow and a faint grid, both from theme tokens (no image). */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 [background-image:radial-gradient(ellipse_60%_50%_at_75%_35%,var(--color-accent-primary-soft),transparent_70%),linear-gradient(to_right,var(--color-border-subtle)_1px,transparent_1px),linear-gradient(to_bottom,var(--color-border-subtle)_1px,transparent_1px)] [background-size:100%_100%,48px_48px,48px_48px] [mask-image:linear-gradient(to_bottom,black_40%,transparent)]"
      />
      <div className="relative max-w-6xl mx-auto px-4 sm:px-8 pt-10 pb-8 md:pt-20 md:pb-12 grid gap-12 lg:grid-cols-[1.1fr_1fr] lg:items-center">
        <div className="space-y-6">
          <span className="inline-block px-3 py-1 rounded-full border border-[var(--color-border-subtle)] bg-[var(--color-background-alt)] text-xs font-semibold text-[var(--color-text-secondary)]">
            {t('landing.hero.badge')}
          </span>
          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight leading-[1.05] text-[var(--color-text-primary)]">
            {t('landing.hero.title')}
          </h1>
          <p className="text-lg sm:text-xl leading-relaxed text-[var(--color-text-secondary)] max-w-xl">
            {t('landing.hero.subtitle')}
          </p>
          <div className="space-y-3 pt-2">
            {isAuthenticated ? (
              <Link to="/dashboard" className={CTA_PRIMARY}>
                {t('landing.hero.cta_dashboard')}
                <ArrowRight className="w-5 h-5" strokeWidth={1.5} />
              </Link>
            ) : (
              <>
                <button onClick={onLogin} data-testid="hero-cta" className={CTA_PRIMARY}>
                  <LogIn className="w-5 h-5" strokeWidth={1.5} />
                  {t('landing.hero.cta')}
                </button>
                <p className="text-sm text-[var(--color-text-secondary)] max-w-md">{t('landing.hero.cta_note')}</p>
              </>
            )}
          </div>
        </div>
        <div className="relative landing-rise">
          {/* A second, offset card suggests a stack of daily briefings. */}
          <div
            aria-hidden="true"
            className="absolute inset-0 translate-x-3 translate-y-3 sm:translate-x-5 sm:translate-y-5 rotate-2 rounded-xl md:rounded-[28px] border border-[var(--color-border-subtle)] bg-[var(--color-surface-deep)]"
          />
          <div className="relative">
            <SampleBriefing />
          </div>
        </div>
      </div>
    </section>
  );
}
