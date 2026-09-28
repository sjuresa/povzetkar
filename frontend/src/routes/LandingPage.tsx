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

// The project changelog/roadmap modal (SystemMetadata) is deliberately not offered here — its content is generated
// from the engineering docs; visitors get the curated "Coming soon" section instead (POV-220, amendment A1).
export function LandingPage() {
  const { login, isAuthenticated } = useAuthStore(
    useShallow((state) => ({
      login: state.login,
      isAuthenticated: state.isAuthenticated,
    }))
  );

  // The version line is decoration: a failed metadata call must not replace the landing page with the error page
  // (the global QueryClient default is throwOnError: true). The footer simply omits the version.
  const { data: metadata } = useSystemMetadata({ throwOnError: false });

  return (
    <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] flex flex-col font-sans transition-colors duration-300">
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
