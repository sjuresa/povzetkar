import { useTranslation } from 'react-i18next';
import { Link } from '@tanstack/react-router';
import type { AccountStatus } from '../../api/generated/model';

interface AccountStatusBannerProps {
  readonly status: AccountStatus;
}

/**
 * Informational banner for non-enabled accounts (no actions: the header already offers logout, and a
 * pending account's status is re-checked automatically — POV-216). While pending, it points the user
 * to the demo content they can already explore.
 */
export function AccountStatusBanner({ status }: AccountStatusBannerProps) {
  const { t } = useTranslation();

  const statusKeyMap: Partial<Record<AccountStatus, string>> = {
    PENDING_APPROVAL: 'pending',
    DISABLED: 'disabled',
    REJECTED: 'rejected',
  };
  const statusKey = statusKeyMap[status] ?? 'rejected';
  const titleKey = `account.${statusKey}.title`;
  const messageKey = `account.${statusKey}.message`;

  const linkClass = 'font-semibold text-[var(--color-action-primary)] hover:underline';

  return (
    <div className="w-full bg-[var(--color-status-warning)]/10 border-b border-[var(--color-status-warning)]/30 px-6 py-4 z-50">
      <p className="text-sm font-semibold text-[var(--color-text-primary)]">
        {t(titleKey)}
      </p>
      <p className="text-xs text-[var(--color-text-secondary)] mt-0.5">
        {t(messageKey)}
      </p>
      {status === 'PENDING_APPROVAL' && (
        <div className="mt-3 text-xs text-[var(--color-text-secondary)]">
          <p className="font-semibold text-[var(--color-text-primary)]">{t('account.pending.demo.title')}</p>
          <ol className="list-decimal list-inside mt-1 space-y-0.5">
            <li>
              <Link to="/topics" className={linkClass}>{t('account.pending.demo.topicsLink')}</Link>
              {': '}
              {t('account.pending.demo.topics')}
            </li>
            <li>
              <Link to="/briefings" className={linkClass}>{t('account.pending.demo.briefingsLink')}</Link>
              {': '}
              {t('account.pending.demo.briefings')}
            </li>
          </ol>
          <p className="mt-1">{t('account.pending.demo.outro')}</p>
        </div>
      )}
    </div>
  );
}
