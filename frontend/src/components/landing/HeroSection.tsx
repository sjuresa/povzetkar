import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';

interface HeroSectionProps {
  readonly isAuthenticated: boolean;
  readonly login: () => void;
  readonly onOpenRealityCheck: () => void;
}

export function HeroSection({ isAuthenticated, login, onOpenRealityCheck }: HeroSectionProps) {
  const { t } = useTranslation();

  const getLevelStyles = (idx: number) => {
    switch (idx) {
      case 0: return 'opacity-100 hover:text-[var(--color-action-primary)]';
      case 1: return 'opacity-80 hover:text-[var(--color-action-primary)]';
      default: return 'opacity-60 hover:text-[var(--color-action-primary)]';
    }
  };

  return (
    <section className="relative max-w-[1400px] mx-auto">
      <div className="relative z-10">
        <h1 className="text-[10vw] md:text-[clamp(5rem,9vw,10rem)] leading-[0.8] font-black tracking-tighter uppercase italic max-w-[95vw]">
          {t('landing.hero.title')}
        </h1>
        
        <div className="mt-20 flex flex-col lg:flex-row items-start justify-between gap-16">
          <div className="max-w-2xl space-y-12">
            <p className="text-3xl md:text-5xl font-black leading-[1.1] tracking-tighter uppercase italic text-[var(--color-text-secondary)]">
              {t('landing.hero.subtitle')}
            </p>
            
            <div className="flex flex-wrap gap-6 pt-4">
              {isAuthenticated ? (
                 <Link
                   to="/dashboard"
                   className="group relative px-10 py-5 bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] font-black uppercase tracking-widest transition-transform active:scale-95 overflow-hidden"
                 >
                   <span className="relative z-10">{t('landing.hero.cta_primary')}</span>
                   <div className="absolute inset-0 bg-[var(--color-action-primary-text)]/10 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
                 </Link>
              ) : (
                <button
                  onClick={login}
                  data-testid="hero-cta"
                  className="group relative px-10 py-5 bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] font-black uppercase tracking-widest transition-transform active:scale-95 overflow-hidden"
                >
                  <span className="relative z-10">{t('landing.hero.cta_primary')}</span>
                  <div className="absolute inset-0 bg-[var(--color-action-primary-text)]/10 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
                </button>
              )}
              <a
                href="#vision"
                className="px-10 py-5 bg-transparent border-4 border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-deep)] text-[var(--color-text-primary)] font-black uppercase tracking-widest transition-all"
              >
                {t('landing.hero.cta_vision')}
              </a>
            </div>
          </div>
          
          {/* Pyramid of Value - The Strategic Advantage */}
          <div className="relative w-full lg:w-[400px] aspect-square flex flex-col justify-end bg-[var(--color-surface-deep)] border-4 border-[var(--color-border-subtle)] p-8 shadow-[32px_32px_0px_rgba(0,0,0,0.1)]">
             <div className="space-y-8 relative z-10">
                {[
                  { title: t('landing.pyramid.level3'), desc: t('landing.pyramid.level3_desc') },
                  { title: t('landing.pyramid.level2'), desc: t('landing.pyramid.level2_desc') },
                  { title: t('landing.pyramid.level1'), desc: t('landing.pyramid.level1_desc') }
                ].map((level, idx) => (
                  <div key={idx} className={`group p-4 border-l-4 border-transparent hover:border-current transition-all ${getLevelStyles(idx)}`}>
                    <h3 className="text-sm font-black uppercase tracking-widest mb-1">{level.title}</h3>
                    <p className="text-xs font-bold leading-relaxed">{level.desc}</p>
                  </div>
                ))}
                <div className="pt-4 border-t-2 border-[var(--color-border-subtle)] flex items-center justify-between">
                   <button 
                     onClick={onOpenRealityCheck}
                     className="text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] hover:text-[var(--color-action-primary)] transition-colors"
                   >
                     {t('landing.pyramid.reality_check')}
                   </button>
                   <span className="text-[10px] font-black uppercase tracking-widest text-[var(--color-status-success)]">Active</span>
                </div>
             </div>
          </div>
        </div>
      </div>
    </section>
  );
}
