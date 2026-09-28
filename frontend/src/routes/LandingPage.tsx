import { useEffect } from 'react';
import { Helmet } from 'react-helmet-async';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { useShallow } from 'zustand/react/shallow';
import { useSystemMetadata } from '../hooks/useSystemMetadata';

// Landing Sub-components
import { LandingNav } from '../components/landing/LandingNav';
import { HeroSection } from '../components/landing/HeroSection';
import { BenefitStrip } from '../components/landing/BenefitStrip';
import { HowItWorksSection } from '../components/landing/HowItWorksSection';
import { FaqSection } from '../components/landing/FaqSection';
import { RoadmapSection } from '../components/landing/RoadmapSection';
import { FinalCta } from '../components/landing/FinalCta';
import { LandingFooter } from '../components/landing/LandingFooter';

/**
 * Points the static head tags from index.html (English defaults for crawlers) at the selected language.
 * The tags are updated in place — emitting new <meta> elements would leave the English ones first in <head>,
 * where first-match consumers read them. The original values return when the landing page unmounts.
 */
function useLocalizedHeadMeta(title: string, description: string) {
  useEffect(() => {
    const targets: Array<[string, string]> = [
      ['meta[name="description"]', description],
      ['meta[property="og:title"]', title],
      ['meta[property="og:description"]', description],
    ];
    const restorers = targets.flatMap(([selector, content]) => {
      const tag = document.head.querySelector<HTMLMetaElement>(selector);
      if (!tag) return [];
      const original = tag.content;
      tag.content = content;
      return [() => { tag.content = original; }];
    });
    return () => restorers.forEach((restore) => restore());
  }, [title, description]);
}

// The project changelog/roadmap modal (SystemMetadata) is deliberately not offered here — its content is generated
// from the engineering docs; visitors get the curated "Coming soon" section instead (POV-220, amendment A1).
export function LandingPage() {
  const { t } = useTranslation();
  const { login, isAuthenticated } = useAuthStore(
    useShallow((state) => ({
      login: state.login,
      isAuthenticated: state.isAuthenticated,
    }))
  );

  // The version line is decoration: a failed metadata call must not replace the landing page with the error page
  // (the global QueryClient default is throwOnError: true). The footer simply omits the version.
  const { data: metadata } = useSystemMetadata({ throwOnError: false });

  useLocalizedHeadMeta(t('landing.meta.title'), t('landing.meta.description'));

  return (
    <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] flex flex-col font-sans transition-colors duration-300">
      {/* The document title follows the selected language (React hoists it before index.html's default). */}
      <Helmet>
        <title>{t('landing.meta.title')}</title>
      </Helmet>
      <LandingNav isAuthenticated={isAuthenticated} onLogin={login} />

      <main className="flex-1">
        <HeroSection isAuthenticated={isAuthenticated} onLogin={login} />
        <BenefitStrip />
        <HowItWorksSection />
        <FaqSection />
        <RoadmapSection />
        <FinalCta isAuthenticated={isAuthenticated} onLogin={login} />
      </main>

      <LandingFooter metadata={metadata} />
    </div>
  );
}
