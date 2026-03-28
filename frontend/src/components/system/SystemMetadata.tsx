import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { X, GitBranch, History, Map, Loader2, BrainCircuit } from 'lucide-react';
import { useSystemMetadata } from '../../hooks/useSystemMetadata';

interface SystemMetadataProps {
  readonly isOpen: boolean;
  readonly onClose: () => void;
  readonly initialTab?: 'changelog' | 'roadmap' | 'wisdom';
}

export interface SystemMetadataData {
  readonly version: string;
  readonly environment: string;
  readonly status: string;
  readonly releaseDate: string;
}

export function SystemMetadata({ isOpen, onClose, initialTab = 'changelog' }: SystemMetadataProps) {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<'changelog' | 'roadmap' | 'wisdom'>(initialTab);

  // Sync tab when opening or prop changes
  useEffect(() => {
    if (isOpen) {
      setActiveTab(initialTab);
    }
  }, [isOpen, initialTab]);

  const { data: metadata, isLoading } = useSystemMetadata({
    enabled: isOpen,
  });

  if (!isOpen) return null;

  const renderVersion = () => {
    if (isLoading) return <Loader2 className="inline w-3 h-3 animate-spin text-[var(--color-accent)]" />;
    return metadata?.version || 'N/A';
  };

  const renderStatus = () => {
    if (isLoading) return <Loader2 className="inline w-3 h-3 animate-spin text-[var(--color-accent)]" />;
    const statusKey = metadata?.status 
      ? `system.status_${metadata.status.toLowerCase()}` 
      : 'system.status_active_evolution';
    return t(statusKey);
  };

  const isStatusActive = metadata?.status?.toUpperCase() === 'ACTIVE_EVOLUTION';

  const renderTabContent = () => {
    switch (activeTab) {
      case 'changelog':
        return (
          <div className="space-y-12">
            <div className="space-y-8">
              <h3 className="text-2xl font-black uppercase tracking-tight flex items-center gap-3">
                <div className="w-3 h-8 bg-[var(--color-accent)]" />
                {t('system.changelog.title')}
              </h3>
              
              <div className="space-y-12">
                <div className="relative pl-8 border-l-4 border-black">
                  <div className="absolute top-0 -left-[10px] w-4 h-4 bg-[var(--color-accent)] shadow-[4px_4px_0px_black]" />
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <span className="px-3 py-1 bg-blue-500 text-white text-[10px] font-black uppercase tracking-widest">{t('system.changelog.badges.new')}</span>
                      <span className="text-xs font-bold text-[var(--color-text-secondary)]">{t('system.changelog.entry1_date')}</span>
                    </div>
                    <h4 className="text-xl font-black tracking-tight">{t('system.changelog.entry1_title')}</h4>
                    <p className="text-[var(--color-text-secondary)] font-bold leading-relaxed max-w-2xl">
                      {t('system.changelog.entry1_desc')}
                    </p>
                  </div>
                </div>

                <div className="relative pl-8 border-l-4 border-black">
                  <div className="absolute top-0 -left-[10px] w-4 h-4 bg-black" />
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <span className="px-3 py-1 bg-[var(--color-border-subtle)] text-black text-[10px] font-black uppercase tracking-widest">{t('system.changelog.badges.improved')}</span>
                      <span className="text-xs font-bold text-[var(--color-text-secondary)]">{t('system.changelog.entry2_date')}</span>
                    </div>
                    <h4 className="text-xl font-black tracking-tight">{t('system.changelog.entry2_title')}</h4>
                    <p className="text-[var(--color-text-secondary)] font-bold leading-relaxed max-w-2xl">
                      {t('system.changelog.entry2_desc')}
                    </p>
                  </div>
                </div>

                <div className="relative pl-8 border-l-4 border-black">
                  <div className="absolute top-0 -left-[10px] w-4 h-4 bg-[var(--color-accent)]" />
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <span className="px-3 py-1 bg-amber-500 text-white text-[10px] font-black uppercase tracking-widest">{t('system.changelog.badges.new')}</span>
                      <span className="text-xs font-bold text-[var(--color-text-secondary)]">{t('system.changelog.entry3_date')}</span>
                    </div>
                    <h4 className="text-xl font-black tracking-tight">{t('system.changelog.entry3_title')}</h4>
                    <p className="text-[var(--color-text-secondary)] font-bold leading-relaxed max-w-2xl">
                      {t('system.changelog.entry3_desc')}
                    </p>
                  </div>
                </div>

                <div className="relative pl-8 border-l-4 border-black opacity-60">
                  <div className="absolute top-0 -left-[10px] w-4 h-4 bg-black" />
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <span className="px-3 py-1 bg-emerald-500 text-white text-[10px] font-black uppercase tracking-widest">{t('system.changelog.badges.fixed')}</span>
                      <span className="text-xs font-bold text-[var(--color-text-secondary)]">{t('system.changelog.entry4_date')}</span>
                    </div>
                    <h4 className="text-xl font-black tracking-tight">{t('system.changelog.entry4_title')}</h4>
                    <p className="text-[var(--color-text-secondary)] font-bold leading-relaxed max-w-2xl">
                      {t('system.changelog.entry4_desc')}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* Focus Banner */}
            <div className="p-8 bg-black text-white border-l-8 border-amber-500">
              <p className="text-sm font-black text-amber-500 mb-2 uppercase tracking-[0.2em]">{t('landing.pyramid.level3')}</p>
              <p className="text-lg font-bold leading-tight italic">
                {t('system.focus_text')}
              </p>
            </div>
          </div>
        );
      case 'roadmap':
        return (
          <div className="space-y-12">
             <div className="space-y-8">
              <h3 className="text-2xl font-black uppercase tracking-tight flex items-center gap-3">
                <div className="w-3 h-8 bg-amber-500" />
                {t('system.roadmap.title')}
              </h3>
              
              <div className="relative pl-12 border-l-4 border-black space-y-20">
                 {[
                  { title: t('system.roadmap.q1_title'), subtitle: `${t('landing.pyramid.level2')} Mastery`, desc: t('system.roadmap.q1_desc_full') },
                  { title: t('system.roadmap.q2_title'), subtitle: `${t('landing.pyramid.level3')} Implementation`, desc: t('system.roadmap.q2_desc_full') },
                  { title: t('system.roadmap.q3_title'), subtitle: "Platform Integrity", desc: t('system.roadmap.q3_desc_full') },
                  { title: t('system.roadmap.q4_title'), subtitle: "Intelligent Synthesis", desc: t('system.roadmap.q4_desc_full') },
                  { title: t('system.roadmap.q5_title'), subtitle: "Universal Wisdom", desc: t('system.roadmap.q5_desc_full') }
                ].map((phase, idx) => (
                  <div key={idx} className={`relative ${idx > 1 ? 'opacity-40' : ''}`}>
                    <span className={`absolute -left-[60px] top-0 w-8 h-8 bg-white border-4 border-black flex items-center justify-center font-black text-xs ${idx === 0 ? 'shadow-[4px_4px_0px_var(--color-accent)]' : ''}`}>0{idx + 1}</span>
                    <div className="space-y-3">
                      <h4 className="text-sm font-black uppercase tracking-widest text-[var(--color-accent)]">{phase.title}</h4>
                      <h3 className="text-2xl font-black uppercase tracking-tight">{phase.subtitle}</h3>
                      <p className="text-lg font-bold text-[var(--color-text-secondary)] leading-relaxed max-w-2xl">
                         {phase.desc}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        );
      case 'wisdom':
        return (
          <div className="space-y-12">
             <div className="space-y-8">
              <h3 className="text-2xl font-black uppercase tracking-tight flex items-center gap-3">
                <div className="w-3 h-8 bg-emerald-500" />
                {t('landing.pyramid.reality_check')}
              </h3>
              
              <div className="p-8 bg-[var(--color-background-base)] border-4 border-black text-[var(--color-text-primary)] border-l-8 border-l-emerald-500 space-y-6 shadow-[16px_16px_0px_rgba(0,0,0,0.05)]">
                 <h4 className="text-3xl font-black uppercase italic tracking-tighter text-emerald-500">
                   The Wisdom Layer
                 </h4>
                 <p className="text-xl font-bold leading-relaxed opacity-80">
                   {t('landing.vision.pillar4_desc')}
                 </p>
                 <div className="pt-8 border-t border-white/20 grid grid-cols-1 md:grid-cols-2 gap-8">
                    <div className="space-y-2">
                       <p className="text-[10px] font-black uppercase tracking-widest text-emerald-500">Methodology</p>
                       <p className="font-bold italic">Retrospective bias assessment and prediction scoring.</p>
                    </div>
                    <div className="space-y-2">
                       <p className="text-[10px] font-black uppercase tracking-widest text-emerald-500">Goal</p>
                       <p className="font-bold italic">Establishing source reliability metrics across all distilled content.</p>
                    </div>
                 </div>
              </div>
            </div>
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 md:p-8 animate-in fade-in duration-300">
      <div 
        className="absolute inset-0 bg-[var(--color-background-base)]/80 backdrop-blur-xl cursor-default" 
        onClick={onClose}
        onKeyDown={(e) => { if (e.key === 'Escape' || e.key === 'Enter') onClose(); }}
        role="button"
        tabIndex={0}
        aria-label={t('common.close')} 
      />
      
      <div className="relative w-full max-w-4xl max-h-[90vh] bg-[var(--color-background-alt)] border-4 border-black shadow-[32px_32px_0px_rgba(0,0,0,0.2)] flex flex-col overflow-hidden italic">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b-4 border-black bg-[var(--color-surface-deep)]">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 bg-[var(--color-accent)] flex items-center justify-center shadow-[4px_4px_0px_black]">
              <GitBranch className="text-white w-6 h-6" />
            </div>
            <div>
              <h2 className="text-2xl font-black uppercase tracking-tighter">{t('system.title')}</h2>
              <div className="flex items-center gap-3">
                <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-text-secondary)]">
                  {t('system.version')}: <span className="text-[var(--color-text-primary)]">
                    {renderVersion()}
                  </span>
                </span>
                <div className="w-1 h-1 rounded-full bg-[var(--color-border-subtle)]" />
                <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-text-secondary)]">
                  {t('system.status')}: <span className={isStatusActive ? 'text-emerald-500' : 'text-amber-500'}>
                    {renderStatus()}
                  </span>
                </span>
              </div>
            </div>
          </div>
          <button onClick={onClose} className="p-2 hover:bg-black hover:text-white transition-colors border-2 border-transparent" aria-label={t('common.close')}>
            <X className="w-6 h-6" />
          </button>
        </div>

        {/* Tabs */}
        <div className="flex border-b-4 border-black bg-[var(--color-background-base)]">
          <button 
            onClick={() => setActiveTab('changelog')}
            className={`flex-1 p-4 flex flex-col items-center justify-center gap-1 font-black uppercase tracking-widest text-[10px] transition-all border-b-4 ${activeTab === 'changelog' ? 'border-[var(--color-accent)] bg-black text-white' : 'border-transparent hover:bg-[var(--color-surface-deep)] text-[var(--color-text-secondary)]'}`}
          >
            <History className="w-4 h-4" />
            {t('system.tabs.changelog')}
          </button>
          <button 
            onClick={() => setActiveTab('roadmap')}
            className={`flex-1 p-4 flex flex-col items-center justify-center gap-1 font-black uppercase tracking-widest text-[10px] transition-all border-b-4 ${activeTab === 'roadmap' ? 'border-[var(--color-accent)] bg-black text-white' : 'border-transparent hover:bg-[var(--color-surface-deep)] text-[var(--color-text-secondary)]'}`}
          >
            <Map className="w-4 h-4" />
            {t('system.tabs.roadmap')}
          </button>
          <button 
            onClick={() => setActiveTab('wisdom')}
            className={`flex-1 p-4 flex flex-col items-center justify-center gap-1 font-black uppercase tracking-widest text-[10px] transition-all border-b-4 ${activeTab === 'wisdom' ? 'border-[var(--color-accent)] bg-black text-white' : 'border-transparent hover:bg-[var(--color-surface-deep)] text-[var(--color-text-secondary)]'}`}
          >
            <BrainCircuit className="w-4 h-4" />
            {t('landing.pyramid.level3')}
          </button>
        </div>

        {/* Content Area */}
        <div className="flex-1 overflow-y-auto p-6 md:p-12 bg-[var(--color-background-alt)] custom-scrollbar">
          {renderTabContent()}
        </div>

        {/* Footer */}
        <div className="p-8 border-t-4 border-black bg-[var(--color-surface-deep)] flex items-center justify-between">
          <p className="text-xs font-black uppercase tracking-widest text-[var(--color-text-secondary)] italic">
            {t('system.footer.built_with')}
          </p>
          <button 
            onClick={onClose}
            className="px-8 py-3 bg-black text-white font-black uppercase tracking-widest text-xs hover:bg-[var(--color-accent)] transition-all shadow-[4px_4px_0px_rgba(0,0,0,0.1)]"
          >
            {t('system.footer.close')}
          </button>
        </div>
      </div>
    </div>
  );
}
