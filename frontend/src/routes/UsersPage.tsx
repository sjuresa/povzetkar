import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { keepPreviousData } from '@tanstack/react-query';
import { useGetApiV1Users } from '../api/generated/users/users';
import type { PaginatedResponse, UserDTO } from '../api/generated/model';
import { UsersFilters } from '../components/admin/UsersFilters';
import type { UsersFilters as UsersFiltersType } from '../components/admin/UsersFilters';
import { UsersTable } from '../components/admin/UsersTable';
import { Skeleton } from '../components/ui/Skeleton';
import { Pagination } from '../components/ui/Pagination';

export function UsersPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const size = 25;
  const [filters, setFilters] = useState<UsersFiltersType>({ email: '' });
  const [sort, setSort] = useState('');

  const { data: response, isLoading } = useGetApiV1Users(
    {
      page,
      size,
      sort: sort ? [sort] : undefined,
      accountStatus: filters.accountStatus,
      email: filters.email || undefined,
    },
    {
      query: {
        placeholderData: keepPreviousData,
      },
    }
  );

  const data = response?.data as PaginatedResponse | undefined;
  const users = (data?.items ?? []) as UserDTO[];
  const totalCount = data?.totalCount ?? 0;
  const totalPages = Math.ceil(totalCount / size);

  const handleFiltersChange = useCallback((newFilters: UsersFiltersType) => {
    setFilters(newFilters);
    setPage(0);
  }, []);

  const handleSortChange = useCallback((newSort: string) => {
    setSort(newSort);
    setPage(0);
  }, []);

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h2 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] mb-2 text-glow">
            {t('admin.users.title')}
          </h2>
          <p className="text-[var(--color-text-secondary)] max-w-lg">
            {t('admin.users.subtitle')}
          </p>
        </div>

        <div className="flex items-center space-x-2 glass border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] px-4 py-2 hover:bg-[var(--color-surface-hover)] transition-colors group">
          <div className="text-right">
            <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Total Users</div>
            <div className="text-sm font-mono text-[var(--color-action-primary)] font-bold">{totalCount.toLocaleString()}</div>
          </div>
          <div className="w-8 h-8 rounded-xl bg-[var(--color-action-primary)]/[0.05] flex items-center justify-center text-[var(--color-action-primary)] ml-2 group-hover:scale-110 transition-transform">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
          </div>
        </div>
      </div>

      <UsersFilters filters={filters} onFiltersChange={handleFiltersChange} />

      <div className="space-y-4">
        {isLoading && !data ? (
          <div className="space-y-4">
            {['a', 'b', 'c', 'd', 'e'].map((id) => (
              <Skeleton key={id} className="h-16 w-full" />
            ))}
          </div>
        ) : (
          <>
            <UsersTable users={users} sort={sort} onSortChange={handleSortChange} />
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={setPage}
              isLoading={isLoading}
            />
          </>
        )}
      </div>
    </div>
  );
}
