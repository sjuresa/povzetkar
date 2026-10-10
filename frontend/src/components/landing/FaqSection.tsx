import { useTranslation } from 'react-i18next';
import { ChevronDown } from 'lucide-react';

const QUESTIONS = [1, 2, 3] as const;

export function FaqSection() {
  const { t } = useTranslation();

  return (
    <section className="max-w-6xl mx-auto px-4 sm:px-8 py-12 md:py-16">
      <h2 className="text-3xl sm:text-4xl font-bold tracking-tight text-[var(--color-text-primary)] mb-10">
        {t('landing.faq.title')}
      </h2>
      <div className="space-y-3 max-w-3xl">
        {QUESTIONS.map((n) => (
          <details
            key={n}
            className="group rounded-xl md:rounded-2xl border border-[var(--color-border-subtle)] bg-[var(--color-background-alt)] px-5 py-4"
          >
            <summary className="flex items-center justify-between gap-4 cursor-pointer list-none font-semibold text-[var(--color-text-primary)] [&::-webkit-details-marker]:hidden">
              {t(`landing.faq.q${n}`)}
              <ChevronDown className="w-5 h-5 shrink-0 text-[var(--color-text-secondary)] transition-transform group-open:rotate-180" strokeWidth={1.5} />
            </summary>
            <p className="mt-3 text-sm leading-relaxed text-[var(--color-text-secondary)]">{t(`landing.faq.a${n}`)}</p>
          </details>
        ))}
      </div>
    </section>
  );
}
