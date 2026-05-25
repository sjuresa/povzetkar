import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { keepPreviousData } from '@tanstack/react-query';
import { useGetApiV1AdminJobs } from '../api/generated/admin/admin';
import type { BackgroundJobPaginatedResponse } from '../api/generated/model';
import { AdminJobsFilters } from '../components/admin/AdminJobsFilters';
import type { AdminJobsFilters as AdminJobsFiltersType } from '../components/admin/AdminJobsFilters';
import { AdminJobsTable } from '../components/admin/AdminJobsTable';
import { Skeleton } from '../components/ui/Skeleton';
import { Pagination } from '../components/ui/Pagination';

function isPaginatedResponse(data: unknown): data is BackgroundJobPaginatedResponse {
  return data !== null && typeof data === 'object' && 'items' in data;
}

export function AdminJobsPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const size = 25;
  const [filters, setFilters] = useState<AdminJobsFiltersType>({});
  const [sort, setSort] = useState('startedAt,desc');

  const { data: response, isLoading } = useGetApiV1AdminJobs(
    {
      page,
      size,
      sort: sort ? [sort] : undefined,
      status: filters.status,
      jobType: filters.jobType,
    },
    {
      query: {
        placeholderData: keepPreviousData,
        refetchInterval: (query) => {
          const payload = query.state.data?.data;
          const items = isPaginatedResponse(payload) ? payload.items : undefined;
          return items?.some((j) => j.status === 'RUNNING') ? 30_000 : false;
        },
      },
    }
  );

  const payload = response?.data;
  const data = isPaginatedResponse(payload) ? payload : undefined;
  const jobs = data?.items ?? [];
  const totalCount = data?.totalCount ?? 0;
  const totalPages = Math.ceil(totalCount / size);

  const handleFiltersChange = useCallback((newFilters: AdminJobsFiltersType) => {
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
            {t('admin.jobs.title')}
          </h2>
          <p className="text-[var(--color-text-secondary)] max-w-lg">
            {t('admin.jobs.subtitle')}
          </p>
        </div>

        <div className="flex items-center space-x-2 glass border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] px-4 py-2 hover:bg-[var(--color-surface-hover)] transition-colors group">
          <div className="text-right">
            <div className="text-[10px] text-interactive text-[var(--color-text-secondary)] uppercase tracking-widest">{t('admin.jobs.total_jobs_label')}</div>
            <div className="text-sm font-mono text-[var(--color-action-primary)] text-interactive">{totalCount.toLocaleString()}</div>
          </div>
          <div className="w-8 h-8 rounded-xl bg-[var(--color-action-primary)]/[0.05] flex items-center justify-center text-[var(--color-action-primary)] ml-2 group-hover:scale-110 transition-transform">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
          </div>
        </div>
      </div>

      <AdminJobsFilters filters={filters} onFilterChange={handleFiltersChange} />

      <div className="space-y-4">
        {isLoading && !data ? (
          <div className="space-y-4">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={`skeleton-${i}`} className="h-16 w-full" />
            ))}
          </div>
        ) : (
          <>
            <AdminJobsTable jobs={jobs} sort={sort} onSortChange={handleSortChange} />
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
