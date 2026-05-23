import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import type { UserDTO, AccountStatus } from '../../api/generated/model';
import {
  getGetApiV1UsersQueryKey,
  usePostApiV1UsersIdApprove,
  usePostApiV1UsersIdReject,
  usePostApiV1UsersIdDisable,
  usePostApiV1UsersIdEnable,
} from '../../api/generated/users/users';

type SortDirection = 'asc' | 'desc' | null;
type SortField = 'email' | 'accountStatus' | 'lastLoginAt' | 'createdAt';

interface SortState {
  field: SortField | null;
  direction: SortDirection;
}

interface UsersTableProps {
  readonly users: UserDTO[];
  readonly sort: string;
  readonly onSortChange: (sort: string) => void;
}

function toDate(value: unknown): Date | null {
  if (!value || typeof value !== 'string') return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

function formatDate(value: unknown, lang: string): string {
  const d = toDate(value);
  if (!d) return '';
  return d.toLocaleDateString(lang, { year: 'numeric', month: 'short', day: 'numeric' });
}

function formatRelative(value: unknown, neverLabel: string, t: (key: string, opts?: object) => string): string {
  const date = toDate(value);
  if (!date) return neverLabel;
  const diff = Date.now() - date.getTime();
  const days = Math.floor(diff / 86400000);
  if (days === 0) return t('admin.users.table.today');
  if (days === 1) return t('admin.users.table.yesterday');
  if (days < 30) return t('admin.users.table.daysAgo', { count: days });
  const months = Math.floor(days / 30);
  if (months < 12) return t('admin.users.table.monthsAgo', { count: months });
  return t('admin.users.table.yearsAgo', { count: Math.floor(months / 12) });
}

function parseSortState(sort: string): SortState {
  if (!sort) return { field: null, direction: null };
  const [field, dir] = sort.split(',');
  return { field: field as SortField, direction: dir as SortDirection };
}

function nextSort(current: SortState, field: SortField): string {
  if (current.field !== field) return `${field},asc`;
  if (current.direction === 'asc') return `${field},desc`;
  return '';
}

function SortIcon({ field, current }: { readonly field: SortField; readonly current: SortState }) {
  if (current.field !== field) {
    return <span className="ml-1 text-[var(--color-text-secondary)]/30">↕</span>;
  }
  return <span className="ml-1 text-[var(--color-action-primary)]">{current.direction === 'asc' ? '↑' : '↓'}</span>;
}

const STATUS_BADGE: Record<string, string> = {
  PENDING_APPROVAL: 'bg-amber-500/10 text-amber-400 border border-amber-500/20',
  ENABLED: 'bg-[var(--color-action-primary)]/[0.08] text-[var(--color-action-primary)] border border-[var(--color-action-primary)]/20',
  DISABLED: 'bg-[var(--color-surface-deep)] text-[var(--color-text-secondary)] border border-[var(--color-border-subtle)]',
  REJECTED: 'bg-red-500/10 text-red-400 border border-red-500/20',
};

export function UsersTable({ users, sort, onSortChange }: UsersTableProps) {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();

  const invalidateUsers = () => queryClient.invalidateQueries({ queryKey: getGetApiV1UsersQueryKey() });

  const onError = (action: string) => {
    toast.error(t('admin.users.error.action_failed', { action }));
  };

  const approve = usePostApiV1UsersIdApprove({
    mutation: {
      onSuccess: () => { toast.success(t('admin.users.success.approved')); invalidateUsers(); },
      onError: () => onError(t('admin.users.actions.approve')),
    },
  });

  const reject = usePostApiV1UsersIdReject({
    mutation: {
      onSuccess: () => { toast.success(t('admin.users.success.rejected')); invalidateUsers(); },
      onError: () => onError(t('admin.users.actions.reject')),
    },
  });

  const disable = usePostApiV1UsersIdDisable({
    mutation: {
      onSuccess: () => { toast.success(t('admin.users.success.disabled')); invalidateUsers(); },
      onError: (err: unknown) => {
        const code = (err as { data?: { error?: { errorCode?: string } } })?.data?.error?.errorCode;
        if (code === 'CANNOT_SELF_DISABLE') toast.error(t('admin.users.error.cannot_self_disable'));
        else if (code === 'LAST_ADMIN_REQUIRED') toast.error(t('admin.users.error.last_admin_required'));
        else onError(t('admin.users.actions.disable'));
      },
    },
  });

  const enable = usePostApiV1UsersIdEnable({
    mutation: {
      onSuccess: () => { toast.success(t('admin.users.success.enabled')); invalidateUsers(); },
      onError: () => onError(t('admin.users.actions.enable')),
    },
  });

  const pendingId = approve.isPending || reject.isPending || disable.isPending || enable.isPending;

  const thClass = 'px-4 py-3 text-[10px] text-interactive text-[var(--color-text-secondary)] uppercase tracking-widest cursor-pointer select-none hover:text-[var(--color-text-primary)] transition-colors';
  const thStaticClass = 'px-4 py-3 text-[10px] text-interactive text-[var(--color-text-secondary)] uppercase tracking-widest text-right';

  if (users.length === 0) {
    return (
      <div className="rounded-xl md:rounded-[28px] border border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]/50 px-6 py-12 text-center">
        <p className="text-sm text-[var(--color-text-secondary)]">{t('admin.users.table.noResults')}</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-xl md:rounded-[28px] border border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]/50 shadow-sm">
      <table className="w-full text-left border-collapse">
        <thead className="bg-[var(--color-surface-deep)]/80 border-b border-[var(--color-border-subtle)]">
          <tr>
            <th className={thClass} onClick={() => onSortChange(nextSort(parseSortState(sort), 'email'))}>
              {t('admin.users.table.email')}<SortIcon field="email" current={parseSortState(sort)} />
            </th>
            <th className={thClass} onClick={() => onSortChange(nextSort(parseSortState(sort), 'accountStatus'))}>
              {t('admin.users.table.status')}<SortIcon field="accountStatus" current={parseSortState(sort)} />
            </th>
            <th className={thClass} onClick={() => onSortChange(nextSort(parseSortState(sort), 'lastLoginAt'))}>
              {t('admin.users.table.lastLogin')}<SortIcon field="lastLoginAt" current={parseSortState(sort)} />
            </th>
            <th className={thClass} onClick={() => onSortChange(nextSort(parseSortState(sort), 'createdAt'))}>
              {t('admin.users.table.created')}<SortIcon field="createdAt" current={parseSortState(sort)} />
            </th>
            <th className={thStaticClass}>{t('admin.users.table.actions')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--color-border-subtle)]/50">
          {users.map((user) => {
            const status: AccountStatus | undefined = user.accountStatus;
            const userId = user.id as number;
            return (
              <tr key={user.id} className="hover:bg-[var(--color-surface-hover)]/30 transition-colors group">
                <td className="px-4 py-4">
                  <div className="text-sm font-medium text-[var(--color-text-primary)] truncate max-w-xs" title={user.email}>
                    {user.email}
                  </div>
                </td>
                <td className="px-4 py-4">
                  {status ? (
                    <span className={`px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-tight ${STATUS_BADGE[status] ?? ''}`}>
                      {t(`admin.users.status.${status.toLowerCase()}`)}
                    </span>
                  ) : (
                    <span className="text-xs text-[var(--color-text-secondary)]">—</span>
                  )}
                </td>
                <td className="px-4 py-4">
                  <span className="text-xs text-[var(--color-text-secondary)]">
                    {formatRelative(user.lastLoginAt, t('admin.users.table.never'), t)}
                  </span>
                </td>
                <td className="px-4 py-4">
                  <span className="text-xs text-[var(--color-text-secondary)]">
                    {formatDate(user.createdAt, i18n.language)}
                  </span>
                </td>
                <td className="px-4 py-4">
                  <div className="flex items-center justify-end gap-2">
                    {status === 'PENDING_APPROVAL' && (
                      <>
                        <ActionButton
                          label={t('admin.users.actions.approve')}
                          onClick={() => approve.mutate({ id: userId })}
                          disabled={pendingId}
                          variant="neutral"
                        />
                        <ActionButton
                          label={t('admin.users.actions.reject')}
                          onClick={() => reject.mutate({ id: userId })}
                          disabled={pendingId}
                          variant="destructive"
                        />
                      </>
                    )}
                    {status === 'ENABLED' && (
                      <ActionButton
                        label={t('admin.users.actions.disable')}
                        onClick={() => disable.mutate({ id: userId })}
                        disabled={pendingId}
                        variant="destructive"
                      />
                    )}
                    {(status === 'DISABLED' || status === 'REJECTED') && (
                      <ActionButton
                        label={status === 'REJECTED' ? t('admin.users.actions.reapprove') : t('admin.users.actions.enable')}
                        onClick={() => enable.mutate({ id: userId })}
                        disabled={pendingId}
                        variant="neutral"
                      />
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

interface ActionButtonProps {
  readonly label: string;
  readonly onClick: () => void;
  readonly disabled: boolean;
  readonly variant: 'neutral' | 'destructive';
}

function ActionButton({ label, onClick, disabled, variant }: ActionButtonProps) {
  const base = 'px-3 py-1.5 rounded-lg text-[10px] text-interactive uppercase tracking-wider transition-all active:scale-95 focus:outline-none focus:ring-2 disabled:opacity-50 disabled:cursor-not-allowed';
  const styles = variant === 'destructive'
    ? `${base} bg-red-500/10 text-red-400 border border-red-500/20 hover:bg-red-500/20 focus:ring-red-500/30`
    : `${base} bg-[var(--color-action-primary)]/[0.08] text-[var(--color-action-primary)] border border-[var(--color-action-primary)]/20 hover:bg-[var(--color-action-primary)]/[0.15] focus:ring-[var(--color-ring-focus)]/30`;
  return (
    <button className={styles} onClick={onClick} disabled={disabled}>
      {label}
    </button>
  );
}
