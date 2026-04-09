import { useTranslation } from 'react-i18next';

interface LandingFooterProps {
  readonly metadata: { version: string; releaseDate: string } | undefined;
  readonly onOpenTab: (tab: 'changelog' | 'roadmap' | 'wisdom') => void;
}

export function LandingFooter({ metadata, onOpenTab }: LandingFooterProps) {
  const { t } = useTranslation();

  return (
    <footer className="mt-48 border-t-4 border-[var(--color-border-subtle)] bg-[var(--color-surface-deep)] py-20 px-8 md:px-16 overflow-hidden relative">
      <div className="max-w-[1400px] mx-auto flex flex-col lg:flex-row justify-between items-start gap-24 relative z-10">
        <div className="space-y-12">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 bg-black shadow-[6px_6px_0px_var(--color-action-primary)]"></div>
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
                onClick={() => onOpenTab('roadmap')} 
                className="text-left hover:text-[var(--color-action-primary)] transition-all flex items-center gap-2 group"
              >
                <div className="w-0 group-hover:w-4 h-0.5 bg-[var(--color-action-primary)] transition-all"></div>
                {t('system.tabs.roadmap')}
              </button>
              <button 
                onClick={() => onOpenTab('changelog')} 
                className="text-left hover:text-[var(--color-action-primary)] transition-all flex items-center gap-2 group"
              >
                <div className="w-0 group-hover:w-4 h-0.5 bg-[var(--color-action-primary)] transition-all"></div>
                {t('system.tabs.changelog')}
              </button>
            </div>
          </div>

          <div className="space-y-6">
             <p className="text-[10px] font-black uppercase tracking-[0.3em] text-[var(--color-text-secondary)] italic">{t('landing.footer.partnership')}</p>
             <div className="flex flex-col gap-4 text-xs font-black uppercase tracking-widest">
                <a href="mailto:wisdom@povzetkar.ai" className="hover:text-[var(--color-action-primary)] transition-colors">{t('landing.footer.contact')}</a>
                 <button 
                   onClick={() => onOpenTab('wisdom')}
                   className="hover:text-[var(--color-action-primary)] transition-colors text-left font-black uppercase text-[10px]"
                 >
                   {t('landing.vision.pillar4_title')}
                 </button>
             </div>
          </div>

           <div className="col-span-2 md:col-span-1 space-y-8">
              <div className="p-6 bg-black text-white space-y-4 border-l-8 border-[var(--color-action-primary)]">
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
  );
}
