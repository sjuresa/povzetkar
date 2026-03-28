import { useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { useShallow } from 'zustand/react/shallow';
import { useTranslation } from 'react-i18next';
import { SystemMetadata } from '../components/system/SystemMetadata';
import { useSystemMetadata } from '../hooks/useSystemMetadata';
import { useThemeStore } from '../store/themeStore';

// Landing Sub-components
import { LandingNav } from '../components/landing/LandingNav';
import { HeroSection } from '../components/landing/HeroSection';
import { VisionSection } from '../components/landing/VisionSection';
import { LandingFooter } from '../components/landing/LandingFooter';

export function LandingPage() {
  const { i18n } = useTranslation();
  const [isMetadataOpen, setIsMetadataOpen] = useState(false);
  const [metadataTab, setMetadataTab] = useState<'changelog' | 'roadmap' | 'wisdom'>('changelog');
  const { theme, setTheme } = useThemeStore();
  const { login, isAuthenticated } = useAuthStore(
    useShallow((state) => ({
      login: state.login,
      isAuthenticated: state.isAuthenticated,
    }))
  );

  const { data: metadata } = useSystemMetadata();

  const toggleLanguage = () => {
    const newLng = i18n.language === 'en' ? 'sl' : 'en';
    void i18n.changeLanguage(newLng);
  };

  const toggleTheme = () => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
  };

  const handleOpenMetadataTab = (tab: 'changelog' | 'roadmap' | 'wisdom') => {
    setMetadataTab(tab);
    setIsMetadataOpen(true);
  };

  return (
    <>
      <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] flex flex-col font-sans selection:bg-[var(--color-accent)] selection:text-white transition-colors duration-500">
        <LandingNav 
          isAuthenticated={isAuthenticated}
          theme={theme}
          toggleLanguage={toggleLanguage}
          toggleTheme={toggleTheme}
        />

        <main className="flex-1 pt-40 pb-20 px-8 md:px-16 space-y-48">
          <HeroSection 
            isAuthenticated={isAuthenticated}
            login={login}
            onOpenRealityCheck={() => handleOpenMetadataTab('wisdom')}
          />

          <VisionSection />
        </main>

        <LandingFooter 
          metadata={metadata}
          onOpenTab={handleOpenMetadataTab}
        />
      </div>

      <SystemMetadata 
        isOpen={isMetadataOpen} 
        onClose={() => setIsMetadataOpen(false)} 
        initialTab={metadataTab}
      />
    </>
  );
}
