import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AccountStatus } from '../../api/generated/model';

export interface UsersFilters {
  accountStatus?: AccountStatus;
  email: string;
}

interface UsersFiltersProps {
  readonly filters: UsersFilters;
  readonly onFiltersChange: (filters: UsersFilters) => void;
}

const DEBOUNCE_MS = 300;

export function UsersFilters({ filters, onFiltersChange }: UsersFiltersProps) {
  const { t } = useTranslation();
  const [emailInput, setEmailInput] = useState(filters.email);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const filtersRef = useRef(filters);

  useEffect(() => {
    setEmailInput(filters.email);
  }, [filters.email]);
  filtersRef.current = filters;

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      onFiltersChange({ ...filtersRef.current, email: emailInput });
    }, DEBOUNCE_MS);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [emailInput, onFiltersChange]);

  const handleStatusChange = (value: string) => {
    onFiltersChange({ ...filters, accountStatus: value ? (value as AccountStatus) : undefined });
  };

  const handleClear = () => {
    setEmailInput('');
    onFiltersChange({ accountStatus: undefined, email: '' });
  };

  const statusOptions: { value: AccountStatus | ''; label: string }[] = [
    { value: '', label: t('admin.users.filters.statusAll') },
    { value: 'PENDING_APPROVAL', label: t('admin.users.status.pending_approval') },
    { value: 'ENABLED', label: t('admin.users.status.enabled') },
    { value: 'DISABLED', label: t('admin.users.status.disabled') },
    { value: 'REJECTED', label: t('admin.users.status.rejected') },
  ];

  return (
    <div className="bg-[var(--color-background-alt)] border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] p-6 shadow-xl shadow-black/5 backdrop-blur-sm">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-[var(--color-action-primary)]/[0.04] border border-[var(--color-border-subtle)] flex items-center justify-center text-[var(--color-action-primary)] shadow-sm">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
            </svg>
          </div>
          <div>
            <h3 className="font-bold text-[var(--color-text-primary)] tracking-tight">{t('admin.users.title')}</h3>
            <p className="text-xs text-[var(--color-text-secondary)]">{t('admin.users.subtitle')}</p>
          </div>
        </div>
        <button
          onClick={handleClear}
          className="p-2 text-[var(--color-text-secondary)] hover:text-[var(--color-status-error)] transition-colors"
          title="Clear Filters"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-1.5">
          <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest px-1">
            {t('admin.users.filters.status')}
          </label>
          <select
            value={filters.accountStatus ?? ''}
            onChange={(e) => handleStatusChange(e.target.value)}
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-xs focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)]"
          >
            {statusOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>

        <div className="space-y-1.5 lg:col-span-2">
          <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest px-1">
            {t('admin.users.table.email')}
          </label>
          <input
            type="text"
            value={emailInput}
            onChange={(e) => setEmailInput(e.target.value)}
            placeholder={t('admin.users.filters.email')}
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-xs focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)]"
          />
        </div>
      </div>
    </div>
  );
}
