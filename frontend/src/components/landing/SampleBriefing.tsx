import { useTranslation } from 'react-i18next';
import { YoutubeIcon } from '../ui/YoutubeIcon';

const ITEMS = [1, 2, 3] as const;

/** A static, fictional daily briefing that shows what the product delivers. Labelled as an example. */
export function SampleBriefing() {
  const { t } = useTranslation();

  return (
    <section
      aria-label={t('landing.sample.aria')}
      data-testid="sample-briefing"
      className="rounded-xl md:rounded-[28px] border border-[var(--color-border-subtle)] bg-[var(--color-background-alt)] p-5 sm:p-6 shadow-xl shadow-[var(--color-shadow-base)] space-y-5"
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 className="text-lg font-bold text-[var(--color-text-primary)]">{t('landing.sample.title')}</h2>
          <p className="text-xs text-[var(--color-text-secondary)] mt-1">{t('landing.sample.meta')}</p>
        </div>
        <span className="shrink-0 px-2.5 py-1 rounded-full border border-[var(--color-border-subtle)] text-xs font-semibold text-[var(--color-text-secondary)]">
          {t('landing.sample.badge')}
        </span>
      </div>

      <div className="rounded-xl bg-[var(--color-background-base)] border-l-4 border-[var(--color-action-primary)] p-4">
        <p className="text-xs font-semibold text-[var(--color-text-secondary)] mb-1">{t('landing.sample.exec_label')}</p>
        <p className="text-sm leading-relaxed text-[var(--color-text-primary)]">{t('landing.sample.exec_text')}</p>
      </div>

      <ul className="space-y-3">
        {ITEMS.map((n) => (
          <li key={n} className="flex gap-3">
            <span className="w-9 h-9 shrink-0 rounded-xl bg-[var(--color-background-base)] border border-[var(--color-border-subtle)] flex items-center justify-center text-[var(--color-text-secondary)]">
              <YoutubeIcon size={18} strokeWidth={1.5} />
            </span>
            <div className="min-w-0">
              <p className="text-xs font-semibold text-[var(--color-text-secondary)]">{t(`landing.sample.item${n}_channel`)}</p>
              <h3 className="text-sm font-semibold text-[var(--color-text-primary)] leading-snug">{t(`landing.sample.item${n}_title`)}</h3>
              <p className="text-xs text-[var(--color-text-secondary)] leading-relaxed mt-0.5">{t(`landing.sample.item${n}_summary`)}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
