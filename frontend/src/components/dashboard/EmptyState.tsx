import { useTranslation } from 'react-i18next';

export function EmptyState() {
  const { t } = useTranslation();

  return (
    <div className="text-center py-6 animate-in fade-in duration-1000">
      <h1 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] mb-4">
        {t('dashboard.empty.welcome')} <span className="text-[var(--color-accent-primary)] text-glow">Povzetkar</span>
      </h1>
      <p className="text-xl text-[var(--color-text-secondary)] max-w-2xl mx-auto">
        {t('dashboard.empty.subtitle')}
      </p>
    </div>
  );
}
