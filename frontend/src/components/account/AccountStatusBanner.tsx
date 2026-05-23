import { useTranslation } from 'react-i18next';
import type { AccountStatus } from '../../api/generated/model';

interface AccountStatusBannerProps {
  readonly status: AccountStatus;
  readonly onRefresh: () => void;
  readonly onSignOut: () => void;
}

export function AccountStatusBanner({ status, onRefresh, onSignOut }: AccountStatusBannerProps) {
  const { t } = useTranslation();

  const statusKeyMap: Partial<Record<AccountStatus, string>> = {
    PENDING_APPROVAL: 'pending',
    DISABLED: 'disabled',
    REJECTED: 'rejected',
  };
  const statusKey = statusKeyMap[status] ?? 'rejected';
  const titleKey = `account.${statusKey}.title`;
  const messageKey = `account.${statusKey}.message`;

  return (
    <div className="w-full bg-[var(--color-status-warning)]/10 border-b border-[var(--color-status-warning)]/30 px-6 py-4 flex items-center justify-between gap-4 z-50">
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-[var(--color-text-primary)]">
          {t(titleKey)}
        </p>
        <p className="text-xs text-[var(--color-text-secondary)] mt-0.5">
          {t(messageKey)}
        </p>
      </div>
      <div className="flex items-center gap-3 flex-shrink-0">
        {status === 'PENDING_APPROVAL' && (
          <button
            onClick={onRefresh}
            className="px-3 py-1.5 text-xs font-medium rounded-lg bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] hover:opacity-90 transition-opacity"
          >
            {t('account.pending.refresh')}
          </button>
        )}
        <button
          onClick={onSignOut}
          className="px-3 py-1.5 text-xs font-medium rounded-lg border border-[var(--color-border-primary)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
        >
          {t(`account.${statusKey}.signout`)}
        </button>
      </div>
    </div>
  );
}
