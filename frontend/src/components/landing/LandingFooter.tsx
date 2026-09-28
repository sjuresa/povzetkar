import { useTranslation } from 'react-i18next';
import { BrandMark } from '../layout/BrandMark';

interface LandingFooterProps {
  readonly metadata: { version: string; releaseDate: string } | undefined;
}

export function LandingFooter({ metadata }: LandingFooterProps) {
  const { t } = useTranslation();

  return (
    <footer className="border-t border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]">
      <div className="max-w-6xl mx-auto px-4 sm:px-8 py-10 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-2">
          <BrandMark />
          <p className="text-sm text-[var(--color-text-secondary)]">{t('landing.footer.mission')}</p>
        </div>
        {metadata && (
          <p className="text-xs text-[var(--color-text-secondary)]">
            {t('landing.footer.version_info', { version: metadata.version, releaseDate: metadata.releaseDate })}
          </p>
        )}
      </div>
    </footer>
  );
}
