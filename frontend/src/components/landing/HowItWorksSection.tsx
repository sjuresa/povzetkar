import { useTranslation } from 'react-i18next';
import { ListPlus, Sparkles, Newspaper } from 'lucide-react';

const STEPS = [
  { n: 1, Icon: ListPlus },
  { n: 2, Icon: Sparkles },
  { n: 3, Icon: Newspaper },
] as const;

export function HowItWorksSection() {
  const { t } = useTranslation();

  return (
    <section className="bg-[var(--color-background-alt)] border-y border-[var(--color-border-subtle)]">
      <div className="max-w-6xl mx-auto px-4 sm:px-8 py-12 md:py-16">
        <h2 className="text-3xl sm:text-4xl font-bold tracking-tight text-[var(--color-text-primary)] mb-10">
          {t('landing.how.title')}
        </h2>
        <ol className="grid gap-8 md:grid-cols-3">
          {STEPS.map(({ n, Icon }) => (
            <li key={n} className="relative space-y-3">
              {n < STEPS.length && (
                // Connector from this step's icon to the next step's number (wide screens only).
                <div aria-hidden="true" className="hidden md:block absolute top-6 left-[5.5rem] -right-8 h-px bg-[var(--color-border-hover)]" />
              )}
              <div className="flex items-center gap-3">
                <span className="w-12 h-12 rounded-2xl bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] flex items-center justify-center text-lg font-bold">
                  {n}
                </span>
                <Icon className="w-5 h-5 text-[var(--color-text-secondary)]" strokeWidth={1.5} />
              </div>
              <h3 className="text-lg font-semibold text-[var(--color-text-primary)]">{t(`landing.how.step${n}_title`)}</h3>
              <p className="text-sm leading-relaxed text-[var(--color-text-secondary)]">{t(`landing.how.step${n}_desc`)}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
