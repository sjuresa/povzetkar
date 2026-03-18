import { useState } from 'react';
import { useGetApiV1AdminAuditLog } from '../api/generated/admin/admin';
import { AuditLogTable } from '../components/admin/AuditLogTable';
import { AuditLogFilters } from '../components/admin/AuditLogFilters';
import type { AuditLogPaginatedResponse } from '../api/generated/model';

export function AuditLogPage() {
  const [page, setPage] = useState(0);
  const [size] = useState(25);
  const [filters, setFilters] = useState<{
    userId?: number;
    method?: string;
    status?: number;
    correlationId?: string;
  }>({});

  const { data: response, isLoading, isError, refetch } = useGetApiV1AdminAuditLog({
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

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-4">
        <div className="bg-red-500/10 p-4 rounded-full text-red-500 mb-2">
          <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>
        <h3 className="text-xl font-bold text-white">Ops! Something went wrong</h3>
        <p className="text-slate-400">Failed to load audit logs. Please try again.</p>
        <button 
          onClick={() => { void refetch(); }} 
          className="px-6 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-xl transition-colors"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h2 className="text-4xl font-extrabold tracking-tight text-white mb-2 text-glow-amber">System Audit</h2>
          <p className="text-slate-400 max-w-lg">
            Track all incoming API requests and system events. Use the filters to narrow down technical issues or trace user activities.
          </p>
        </div>
        
        <div className="flex items-center space-x-2 bg-slate-900 border border-slate-800 rounded-2xl px-4 py-2">
           <div className="text-right">
             <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Database State</div>
             <div className="text-sm font-mono text-amber-500 font-bold">{totalCount.toLocaleString()} Entries</div>
           </div>
           <div className="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center text-amber-500 ml-2">
             <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
               <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
             </svg>
           </div>
        </div>
      </div>

      <AuditLogFilters onFilterChange={(newFilters) => {
        setFilters(newFilters);
        setPage(0); // Reset to first page on filter change
      }} />

      <div className="space-y-4">
        {isLoading ? (
          <div className="space-y-4">
             {[...Array(5)].map((_, i) => (
                <div key={i} className="h-20 bg-slate-900/50 rounded-xl animate-pulse border border-slate-800/50"></div>
             ))}
          </div>
        ) : (
          <>
            <AuditLogTable logs={items} />
            
            {/* Pagination */}
            <div className="flex items-center justify-between pt-4">
              <p className="text-xs text-slate-500 italic">
                Showing page <span className="text-slate-300 font-bold">{page + 1}</span> of <span className="text-slate-300 font-bold">{totalPages || 1}</span>
              </p>
              
              <div className="flex items-center space-x-2">
                <button
                  disabled={page === 0 || isLoading}
                  onClick={() => setPage(p => p - 1)}
                  className="px-4 py-2 rounded-xl glass hover:bg-slate-800 disabled:opacity-30 disabled:hover:scale-100 transition-all hover:scale-105 active:scale-95 text-xs font-bold uppercase tracking-widest"
                >
                  Previous
                </button>
                <div className="w-px h-6 bg-slate-800 mx-2"></div>
                <button
                  disabled={page >= totalPages - 1 || isLoading}
                  onClick={() => setPage(p => p + 1)}
                  className="px-4 py-2 rounded-xl glass hover:bg-slate-800 disabled:opacity-30 disabled:hover:scale-100 transition-all hover:scale-105 active:scale-95 text-xs font-bold uppercase tracking-widest"
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
