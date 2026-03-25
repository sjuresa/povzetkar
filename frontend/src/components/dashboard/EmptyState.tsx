import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';

export function EmptyState() {
  const { t } = useTranslation();
  
  const steps = [
    { 
      title: t('dashboard.empty.connectTitle'), 
      description: t('dashboard.empty.connectDesc'), 
      icon: 'M13 10V3L4 14h7v7l9-11h-7z' 
    },
    { 
      title: t('dashboard.empty.createTopicTitle'), 
      description: t('dashboard.empty.createTopicDesc'), 
      icon: 'M7 7h.01M7 11h.01M7 15h.01M11 7h.01M11 11h.01M11 15h.01M15 7h.01M15 11h.01M15 15h.01' 
    },
    { 
      title: t('dashboard.empty.getBriefingTitle'), 
      description: t('dashboard.empty.getBriefingDesc'), 
      icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z' 
    },
  ];

  return (
    <div className="max-w-4xl mx-auto py-12 px-4 animate-in fade-in duration-1000">
      <div className="text-center mb-12">
        <h1 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] mb-4">
          {t('dashboard.empty.welcome')} <span className="text-[var(--color-accent)] text-glow">Povzetkar</span>
        </h1>
        <p className="text-xl text-[var(--color-text-secondary)]">
          {t('dashboard.empty.subtitle')}
        </p>
      </div>

      <div className="grid md:grid-cols-3 gap-8 mb-12">
        {steps.map((step, idx) => (
          <div key={idx} className="glass p-8 rounded-3xl relative overflow-hidden group border border-transparent hover:border-[var(--color-accent)]/20 transition-all">
             <div className="text-[var(--color-accent)] mb-6 bg-[var(--color-accent-soft)] w-14 h-14 rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
               <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                 <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={step.icon} />
               </svg>
             </div>
             <h3 className="text-lg font-bold text-[var(--color-text-primary)] mb-2">{step.title}</h3>
             <p className="text-[var(--color-text-secondary)] text-sm leading-relaxed opacity-80">{step.description}</p>
             <div className="absolute top-0 right-0 w-24 h-24 bg-[var(--color-accent-soft)] blur-3xl -z-10"></div>
          </div>
        ))}
      </div>

      <div className="flex flex-col items-center space-y-6">
        <Link 
          to="/topics"
          className="px-8 py-4 bg-[var(--color-accent)] hover:opacity-90 text-white font-bold rounded-2xl shadow-lg shadow-blue-900/40 transition-all hover:-translate-y-1 active:scale-95 text-lg"
        >
          {t('dashboard.empty.action')}
        </Link>
        <p className="text-sm text-[var(--color-text-secondary)]">
          {t('dashboard.empty.help')} <span className="italic">{t('dashboard.empty.guide')}</span>
        </p>
      </div>
    </div>
  );
}
