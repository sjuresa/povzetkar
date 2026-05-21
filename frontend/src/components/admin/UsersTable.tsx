import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { getGetApiV1UsersQueryKey } from '../../api/generated/users/users';
import type { UserDTO, AccountStatus } from '../../api/generated/model';

type SortDirection = 'asc' | 'desc' | null;
type SortField = 'email' | 'accountStatus' | 'lastLoginAt' | 'createdAt';

interface SortState {
  field: SortField | null;
  direction: SortDirection;
}

interface UsersTableProps {
  users: UserDTO[];
  sort: string;
  onSortChange: (sort: string) => void;
}

function formatDate(value: unknown): string {
  if (!value) return '';
  return new Date(value as string).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function formatRelative(value: unknown, neverLabel: string, t: (key: string, opts?: object) => string): string {
  if (!value) return neverLabel;
  const date = new Date(value as string);
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

function SortIcon({ field, current }: { field: SortField; current: SortState }) {
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
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [pendingUserId, setPendingUserId] = useState<number | null>(null);
  const sortState = parseSortState(sort);

  const invalidateUsers = () => {
    queryClient.invalidateQueries({ queryKey: getGetApiV1UsersQueryKey() });
  };

  const handleAction = async (userId: number, action: string) => {
    setPendingUserId(userId);
    try {
      // TODO: POV-185 — replace with generated mutation hooks once action endpoints are implemented
      // POST /api/v1/users/{userId}/{action}
      const response = await fetch(`/api/v1/users/${userId}/${action}`, { method: 'POST' });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        const code = body?.code ?? '';
        if (code === 'CANNOT_SELF_DISABLE') {
          toast.error(t('admin.users.error.self_disable'));
        } else if (code === 'LAST_ADMIN_REQUIRED') {
          toast.error(t('admin.users.error.last_admin'));
        } else if (code === 'INVALID_STATE_TRANSITION') {
          toast.error(t('admin.users.error.invalid_state'));
          invalidateUsers();
        } else {
          toast.error(`Action failed: ${response.status}`);
        }
        return;
      }
      invalidateUsers();
    } finally {
      setPendingUserId(null);
    }
  };

  const thClass = 'px-4 py-3 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest cursor-pointer select-none hover:text-[var(--color-text-primary)] transition-colors';
  const thStaticClass = 'px-4 py-3 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest text-right';

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
            <th className={thClass} onClick={() => onSortChange(nextSort(sortState, 'email'))}>
              {t('admin.users.table.email')}<SortIcon field="email" current={sortState} />
            </th>
            <th className={thClass} onClick={() => onSortChange(nextSort(sortState, 'accountStatus'))}>
              {t('admin.users.table.status')}<SortIcon field="accountStatus" current={sortState} />
            </th>
            <th className={thClass} onClick={() => onSortChange(nextSort(sortState, 'lastLoginAt'))}>
              {t('admin.users.table.lastLogin')}<SortIcon field="lastLoginAt" current={sortState} />
            </th>
            <th className={thClass} onClick={() => onSortChange(nextSort(sortState, 'createdAt'))}>
              {t('admin.users.table.created')}<SortIcon field="createdAt" current={sortState} />
            </th>
            <th className={thStaticClass}>{t('admin.users.table.actions')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--color-border-subtle)]/50">
          {users.map((user) => {
            const isPending = pendingUserId === user.id;
            const status = user.accountStatus as AccountStatus | undefined;
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
                      {status.replace('_', ' ')}
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
                    {formatDate(user.createdAt)}
                  </span>
                </td>
                <td className="px-4 py-4">
                  <div className="flex items-center justify-end gap-2">
                    {status === 'PENDING_APPROVAL' && (
                      <>
                        <ActionButton
                          label={t('admin.users.actions.approve')}
                          onClick={() => handleAction(user.id!, 'approve')}
                          disabled={isPending}
                          variant="neutral"
                        />
                        <ActionButton
                          label={t('admin.users.actions.reject')}
                          onClick={() => handleAction(user.id!, 'reject')}
                          disabled={isPending}
                          variant="destructive"
                        />
                      </>
                    )}
                    {status === 'ENABLED' && (
                      <ActionButton
                        label={t('admin.users.actions.disable')}
                        onClick={() => handleAction(user.id!, 'disable')}
                        disabled={isPending}
                        variant="destructive"
                      />
                    )}
                    {(status === 'DISABLED' || status === 'REJECTED') && (
                      <ActionButton
                        label={status === 'REJECTED' ? t('admin.users.actions.reapprove') : t('admin.users.actions.enable')}
                        onClick={() => handleAction(user.id!, status === 'REJECTED' ? 'approve' : 'enable')}
                        disabled={isPending}
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
  label: string;
  onClick: () => void;
  disabled: boolean;
  variant: 'neutral' | 'destructive';
}

function ActionButton({ label, onClick, disabled, variant }: ActionButtonProps) {
  const base = 'px-3 py-1.5 rounded-lg text-[10px] font-bold uppercase tracking-wider transition-all active:scale-95 focus:outline-none focus:ring-2 disabled:opacity-50 disabled:cursor-not-allowed';
  const styles = variant === 'destructive'
    ? `${base} bg-red-500/10 text-red-400 border border-red-500/20 hover:bg-red-500/20 focus:ring-red-500/30`
    : `${base} bg-[var(--color-action-primary)]/[0.08] text-[var(--color-action-primary)] border border-[var(--color-action-primary)]/20 hover:bg-[var(--color-action-primary)]/[0.15] focus:ring-[var(--color-ring-focus)]/30`;
  return (
    <button className={styles} onClick={onClick} disabled={disabled}>
      {label}
    </button>
  );
}
