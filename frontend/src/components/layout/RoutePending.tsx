import { Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Minimal pending UI shown while a lazy route chunk is being fetched (POV-202).
 * Authenticated routes still render inside AppShell; only the Outlet content area
 * shows this briefly on slow networks. Wired as the router's `defaultPendingComponent`.
 */
export function RoutePending() {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-center py-24" role="status">
      <Loader2
        className="w-8 h-8 animate-spin text-[var(--color-action-primary)]"
        strokeWidth={1.5}
      />
      <span className="sr-only">{t('common.loading')}</span>
    </div>
  );
}
