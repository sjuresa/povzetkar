import { useState } from 'react';
import { Link } from '@tanstack/react-router';
import { useAuthStore } from '../store/authStore';
import { useShallow } from 'zustand/react/shallow';
import { useTranslation } from 'react-i18next';
import { SystemMetadata } from '../components/system/SystemMetadata';
import heroAsset from '../assets/hero-distillation.png';
import { useSystemMetadata } from '../hooks/useSystemMetadata';

import { useThemeStore } from '../store/themeStore';

export function LandingPage() {
  const { t, i18n } = useTranslation();
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

  const getLevelStyles = (idx: number) => {
    switch (idx) {
      case 0: return 'opacity-100 hover:text-amber-500';
      case 1: return 'opacity-80 hover:text-blue-500';
      default: return 'opacity-60 hover:text-[var(--color-accent)]';
    }
  };

  return (
    <>
      <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] flex flex-col font-sans selection:bg-[var(--color-accent)] selection:text-white transition-colors duration-500">
        {/* Navigation - Ultra Minimalist */}
        <nav className="fixed top-0 w-full h-20 border-b-2 border-[var(--color-border-subtle)] bg-[var(--color-background-base)]/80 backdrop-blur-md flex items-center justify-between px-8 md:px-16 z-50">
          <div className="flex items-center gap-8">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 bg-[var(--color-accent)] shadow-[4px_4px_0px_black]"></div>
              <span className="text-xl font-black tracking-tighter uppercase italic">Povzetkar</span>
            </div>
            
            {/* Quick Settings */}
            <div className="hidden md:flex items-center gap-6 border-l-2 border-[var(--color-border-subtle)] pl-8">
               <button 
                 onClick={toggleLanguage} 
                 className="text-[10px] font-black uppercase tracking-widest hover:text-[var(--color-accent)] transition-colors"
                >
                 {i18n.language === 'en' ? 'SL' : 'EN'}
               </button>
               <button 
                 onClick={toggleTheme}
                 className="text-[10px] font-black uppercase tracking-widest hover:text-[var(--color-accent)] transition-colors"
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

        <main className="flex-1 pt-40 pb-20 px-8 md:px-16 space-y-48">
          {/* Hero Section - The Mandate */}
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
                         className="group relative px-10 py-5 bg-[var(--color-accent)] text-white font-black uppercase tracking-widest transition-transform active:scale-95 overflow-hidden"
                       >
                         <span className="relative z-10">{t('landing.hero.cta_primary')}</span>
                         <div className="absolute inset-0 bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
                       </Link>
                    ) : (
                      <button
                        onClick={login}
                        className="group relative px-10 py-5 bg-[var(--color-accent)] text-white font-black uppercase tracking-widest transition-transform active:scale-95 overflow-hidden"
                      >
                        <span className="relative z-10">{t('landing.hero.cta_primary')}</span>
                        <div className="absolute inset-0 bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
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
                           onClick={() => { setMetadataTab('wisdom'); setIsMetadataOpen(true); }}
                           className="text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] hover:text-[var(--color-accent)] transition-colors"
                         >
                           {t('landing.pyramid.reality_check')}
                         </button>
                         <span className="text-[10px] font-black uppercase tracking-widest text-emerald-500">Active</span>
                      </div>
                   </div>
                </div>
              </div>
            </div>
          </section>

          {/* Vision Section - Augmented Consciousness */}
          <section id="vision" className="max-w-[1400px] mx-auto">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-24 items-center">
               <div className="space-y-16">
                  <div className="space-y-8">
                     <h2 className="text-6xl md:text-8xl font-black uppercase tracking-tighter leading-[0.9] italic">
                        {t('landing.vision.title')}
                     </h2>
                     <div className="space-y-6 max-w-xl">
                        <p className="text-2xl font-black uppercase tracking-tight text-[var(--color-accent)]">
                          {t('landing.vision.desc1')}
                        </p>
                        <p className="text-xl font-medium leading-relaxed text-[var(--color-text-secondary)]">
                          {t('landing.vision.desc2')}
                        </p>
                     </div>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-12 border-t-4 border-[var(--color-border-subtle)] pt-12">
                     {[
                       { title: t('landing.vision.pillar1_title'), desc: t('landing.vision.pillar1_desc') },
                       { title: t('landing.vision.pillar2_title'), desc: t('landing.vision.pillar2_desc') },
                       { title: t('landing.vision.pillar3_title'), desc: t('landing.vision.pillar3_desc') },
                       { title: t('landing.vision.pillar4_title'), desc: t('landing.vision.pillar4_desc') }
                     ].map((pillar, idx) => (
                       <div key={idx} className="space-y-3">
                          <h4 className="text-lg font-black uppercase tracking-widest italic flex items-center gap-2">
                             <div className="w-2 h-6 bg-[var(--color-accent)]"></div>
                             {pillar.title}
                          </h4>
                          <p className="text-sm font-bold text-[var(--color-text-secondary)] leading-relaxed">{pillar.desc}</p>
                       </div>
                     ))}
                  </div>
               </div>

               <div className="relative group perspective-[2000px]">
                  <div className="absolute inset-0 bg-[var(--color-accent)] translate-x-8 translate-y-8 group-hover:translate-x-4 group-hover:translate-y-4 transition-transform duration-700"></div>
                  <div className="relative border-4 border-black bg-[var(--color-surface-deep)] overflow-hidden">
                     <img 
                       src={heroAsset} 
                       alt="Wisdom Inception" 
                       className="w-full h-full object-cover grayscale brightness-110 group-hover:grayscale-0 transition-all duration-1000 scale-105 group-hover:scale-100" 
                     />
                     <div className="absolute inset-x-0 bottom-0 p-8 bg-gradient-to-t from-black/80 to-transparent flex flex-col justify-end h-1/2">
                        <span className="text-[10px] font-black uppercase tracking-[0.4em] text-white/60 mb-2 italic">{t('landing.vision.image_label')}</span>
                        <h5 className="text-2xl font-black text-white uppercase tracking-tighter italic">{t('landing.vision.image_text')}</h5>
                     </div>
                  </div>
               </div>
            </div>
          </section>
        </main>

        {/* Footer - Brutalist Grounding */}
        <footer className="mt-48 border-t-4 border-[var(--color-border-subtle)] bg-[var(--color-surface-deep)] py-20 px-8 md:px-16 overflow-hidden relative">
          <div className="max-w-[1400px] mx-auto flex flex-col lg:flex-row justify-between items-start gap-24 relative z-10">
            <div className="space-y-12">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 bg-black shadow-[6px_6px_0px_var(--color-accent)]"></div>
                <span className="text-3xl font-black tracking-tighter uppercase italic">Povzetkar</span>
              </div>
              <div className="space-y-4">
                 <p className="text-lg font-black uppercase tracking-tight italic max-w-md">
                    {t('landing.footer.mission')}
                 </p>
                 <p className="text-xs font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">
                    {metadata ? t('landing.footer.version_info', { version: metadata.version, releaseDate: metadata.releaseDate }) : ''}
                 </p>
              </div>
            </div>
            
            <div className="grid grid-cols-2 md:grid-cols-3 gap-24">
              <div className="space-y-6">
                <p className="text-[10px] font-black uppercase tracking-[0.3em] text-[var(--color-text-secondary)] italic">Knowledge Path</p>
                <div className="flex flex-col gap-4 text-xs font-black uppercase tracking-widest">
                  <button 
                    onClick={() => { setMetadataTab('roadmap'); setIsMetadataOpen(true); }} 
                    className="text-left hover:text-[var(--color-accent)] transition-all flex items-center gap-2 group"
                  >
                    <div className="w-0 group-hover:w-4 h-0.5 bg-[var(--color-accent)] transition-all"></div>
                    {t('system.tabs.roadmap')}
                  </button>
                  <button 
                    onClick={() => { setMetadataTab('changelog'); setIsMetadataOpen(true); }} 
                    className="text-left hover:text-[var(--color-accent)] transition-all flex items-center gap-2 group"
                  >
                    <div className="w-0 group-hover:w-4 h-0.5 bg-[var(--color-accent)] transition-all"></div>
                    {t('system.tabs.changelog')}
                  </button>
                </div>
              </div>

              <div className="space-y-6">
                 <p className="text-[10px] font-black uppercase tracking-[0.3em] text-[var(--color-text-secondary)] italic">{t('landing.footer.partnership')}</p>
                 <div className="flex flex-col gap-4 text-xs font-black uppercase tracking-widest">
                    <a href="mailto:wisdom@povzetkar.ai" className="hover:text-[var(--color-accent)] transition-colors">{t('landing.footer.contact')}</a>
                     <button 
                       onClick={() => { setMetadataTab('wisdom'); setIsMetadataOpen(true); }}
                       className="hover:text-[var(--color-accent)] transition-colors text-left font-black uppercase text-[10px]"
                     >
                       {t('landing.vision.pillar4_title')}
                     </button>
                 </div>
              </div>

               <div className="col-span-2 md:col-span-1 space-y-8">
                  <div className="p-6 bg-black text-white space-y-4 border-l-8 border-[var(--color-accent)]">
                     <span className="text-[10px] font-black uppercase tracking-widest text-white/40">{t('landing.footer.status_label')}</span>
                     <p className="text-sm font-bold leading-tight">{t('landing.footer.status_text')}</p>
                  </div>
               </div>
            </div>
          </div>
          
           {/* Background Text Decor */}
           <div className="absolute bottom-0 right-0 translate-y-1/2 translate-x-1/4 text-[25vw] font-black uppercase italic opacity-[0.03] select-none pointer-events-none">
              {t('landing.pyramid.level3')}
           </div>
        </footer>
      </div>
      <SystemMetadata 
        isOpen={isMetadataOpen} 
        onClose={() => setIsMetadataOpen(false)} 
        initialTab={metadataTab}
      />
    </>
  );
}
