import { useTranslation } from 'react-i18next';

// Every item must be backed by docs/overview/roadmap.md — this section promises nothing that is not planned.
const ITEMS = [1, 2, 3] as const;

export function RoadmapSection() {
  const { t } = useTranslation();

  return (
    <section className="max-w-6xl mx-auto px-4 sm:px-8 py-12 md:py-16">
      <div className="mb-10 space-y-3">
        <h2 className="text-3xl sm:text-4xl font-bold tracking-tight text-[var(--color-text-primary)]">
          {t('landing.roadmap.title')}
        </h2>
        <p className="text-[var(--color-text-secondary)] max-w-2xl">{t('landing.roadmap.intro')}</p>
      </div>
      <ul className="grid gap-4 md:grid-cols-3">
        {ITEMS.map((n) => (
          <li
            key={n}
            className="rounded-xl md:rounded-[28px] border border-dashed border-[var(--color-border-hover)] p-6 space-y-3"
          >
            <span
              data-testid="planned-badge"
              className="inline-block px-2.5 py-1 rounded-full bg-[var(--color-background-alt)] text-xs font-semibold text-[var(--color-text-secondary)]"
            >
              {t('landing.roadmap.planned')}
            </span>
            <h3 className="text-lg font-semibold text-[var(--color-text-primary)]">{t(`landing.roadmap.item${n}_title`)}</h3>
            <p className="text-sm leading-relaxed text-[var(--color-text-secondary)]">{t(`landing.roadmap.item${n}_desc`)}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}
