import { useState } from 'react';
import { useGetApiV1AdminAuditLog } from '../api/generated/admin/admin';
import { AuditLogTable } from '../components/admin/AuditLogTable';
import { AuditLogFilters } from '../components/admin/AuditLogFilters';
import type { AuditLogPaginatedResponse } from '../api/generated/model';
import { Skeleton } from '../components/ui/Skeleton';
import { Pagination } from '../components/ui/Pagination';

export function AuditLogPage() {
  const [page, setPage] = useState(0);
  const [size] = useState(25);
  const [filters, setFilters] = useState<{
    userId?: number;
    method?: string;
    status?: number;
    correlationId?: string;
  }>({});

  const { data: response, isLoading } = useGetApiV1AdminAuditLog({
    page,
    size,
    userId: filters.userId,
    method: filters.method,
    status: filters.status,
    correlationId: filters.correlationId
  }, {
    query: {
      placeholderData: (previousData) => previousData, // Smooth transitions
    }
  });

  const data = response?.data as AuditLogPaginatedResponse | undefined;
  const items = data?.items || [];
  const totalCount = data?.totalCount || 0;
  const totalPages = Math.ceil(totalCount / size);

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h2 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] mb-2 text-glow">System Audit</h2>
          <p className="text-[var(--color-text-secondary)] max-w-lg">
            Track all incoming API requests and system events. Use the filters to narrow down technical issues or trace user activities.
          </p>
        </div>
        
        <div className="flex items-center space-x-2 glass border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] px-4 py-2 hover:bg-[var(--color-surface-hover)] transition-colors group">
           <div className="text-right">
             <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Database State</div>
             <div className="text-sm font-mono text-[var(--color-action-primary)] font-bold">{totalCount.toLocaleString()} Entries</div>
           </div>
           <div className="w-8 h-8 rounded-xl bg-[var(--color-action-primary)]/[0.05] flex items-center justify-center text-[var(--color-action-primary)] ml-2 group-hover:scale-110 transition-transform">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
              </svg>
           </div>
        </div>
      </div>

      <AuditLogFilters onFilterChange={(newFilters) => {
        setFilters(newFilters);
        setPage(0); // Reset to first page on filter change
      }} />

      <div className="space-y-4">
        {isLoading && !data ? (
          <div className="space-y-4">
             {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-20 w-full" />
             ))}
          </div>
        ) : (
          <>
            <AuditLogTable logs={items} />
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
