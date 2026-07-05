import { lazy, Suspense, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { useShallow } from 'zustand/react/shallow';
import { useTranslation } from 'react-i18next';
import { useSystemMetadata } from '../hooks/useSystemMetadata';

// Lazy-loaded (POV-202): the changelog/roadmap modal pulls in the markdown pipeline
// (react-markdown). It is default-closed, so we defer its code off the landing page's
// initial chunk and only fetch it when the user opens the modal.
const SystemMetadata = lazy(() =>
  import('../components/system/SystemMetadata').then((m) => ({ default: m.SystemMetadata }))
);
import { useThemeStore } from '../store/themeStore';

// Landing Sub-components
import { LandingNav } from '../components/landing/LandingNav';
import { HeroSection } from '../components/landing/HeroSection';
import { VisionSection } from '../components/landing/VisionSection';
import { LandingFooter } from '../components/landing/LandingFooter';
import { useSearch } from '@tanstack/react-router';
import { AlertCircle, X } from 'lucide-react';

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
  const { notice } = useSearch({ from: '/' });
  const [showNotice, setShowNotice] = useState(!!notice);

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
      <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] flex flex-col font-sans selection:bg-[var(--color-action-primary)] selection:text-[var(--color-action-primary-text)] transition-colors duration-500">
        <LandingNav 
          isAuthenticated={isAuthenticated}
          theme={theme}
          toggleLanguage={toggleLanguage}
          toggleTheme={toggleTheme}
        />

        {showNotice && notice === 'restricted' && (
          <div className="fixed top-24 left-1/2 -translate-x-1/2 z-50 w-full max-w-sm px-4 animate-in fade-in slide-in-from-top-4 duration-500">
            <div className="bg-[var(--color-background-elevated)]/90 border border-[var(--color-status-warning)]/50 backdrop-blur-md rounded-xl md:rounded-[28px] p-4 flex items-start gap-3 shadow-2xl shadow-black/50">
              <AlertCircle className="w-5 h-5 text-[var(--color-status-warning)] shrink-0 mt-0.5"  strokeWidth={1.5}/>
              <div className="flex-1">
                <h3 className="text-sm font-bold text-[var(--color-text-primary)]">Restricted Content</h3>
                <p className="text-xs text-[var(--color-text-secondary)] mt-1 leading-relaxed italic">
                  You need to be signed in to view this briefing. Please log in using your Google account.
                </p>
              </div>
              <button 
                onClick={() => setShowNotice(false)}
                className="text-[var(--color-text-secondary)]/50 hover:text-[var(--color-text-primary)] transition-colors"
                aria-label="Close notice"
              >
                <X className="w-4 h-4"  strokeWidth={1.5}/>
              </button>
            </div>
          </div>
        )}

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

      {isMetadataOpen && (
        <Suspense fallback={null}>
          <SystemMetadata
            isOpen={isMetadataOpen}
            onClose={() => setIsMetadataOpen(false)}
            initialTab={metadataTab}
          />
        </Suspense>
      )}
    </>
  );
}
