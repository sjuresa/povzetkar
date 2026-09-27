import { Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Suspense fallback for the lazy-loaded {@link SystemMetadata} modal (POV-202).
 * Renders the modal's full-screen backdrop with a centered spinner so opening the
 * version button on a slow network gives immediate visual feedback while the chunk
 * loads, instead of a "dead click". Styling mirrors the modal's own backdrop.
 */
export function SystemMetadataFallback() {
  const { t } = useTranslation();
  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-[var(--color-background-base)]/80 backdrop-blur-xl"
      role="status"
    >
      <Loader2
        className="w-8 h-8 animate-spin text-[var(--color-action-primary)]"
        strokeWidth={1.5}
      />
      <span className="sr-only">{t('common.loading')}</span>
    </div>
  );
}
