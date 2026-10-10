import { useTranslation } from 'react-i18next';
import { Clock, CalendarCheck, UserCheck } from 'lucide-react';

const BENEFITS = [
  { n: 1, Icon: Clock },
  { n: 2, Icon: CalendarCheck },
  { n: 3, Icon: UserCheck },
] as const;

export function BenefitStrip() {
  const { t } = useTranslation();

  return (
    <section className="max-w-6xl mx-auto px-4 sm:px-8 pb-12 md:pb-16">
      <ul className="grid gap-6 sm:grid-cols-3 border-t border-[var(--color-border-subtle)] pt-8">
        {BENEFITS.map(({ n, Icon }) => (
          <li key={n} data-testid="benefit" className="flex gap-3">
            <Icon className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-action-primary)]" strokeWidth={1.5} />
            <div>
              <p className="font-semibold text-[var(--color-text-primary)]">{t(`landing.benefits.b${n}_title`)}</p>
              <p className="text-sm leading-relaxed text-[var(--color-text-secondary)] mt-1">{t(`landing.benefits.b${n}_desc`)}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
