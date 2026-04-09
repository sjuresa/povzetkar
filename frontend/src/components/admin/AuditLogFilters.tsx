import { useState } from 'react';

interface AuditLogFiltersProps {
  onFilterChange: (filters: {
    userId?: number;
    method?: string;
    status?: number;
    correlationId?: string;
  }) => void;
}

export function AuditLogFilters({ onFilterChange }: AuditLogFiltersProps) {
  const [userId, setUserId] = useState<string>('');
  const [method, setMethod] = useState<string>('');
  const [status, setStatus] = useState<string>('');
  const [correlationId, setCorrelationId] = useState<string>('');

  const [expanded, setExpanded] = useState(false);

  const applyFilters = () => {
    onFilterChange({
      userId: userId ? parseInt(userId, 10) : undefined,
      method: method || undefined,
      status: status ? parseInt(status, 10) : undefined,
      correlationId: correlationId || undefined,
    });
  };

  const handleClear = () => {
    setUserId('');
    setMethod('');
    setStatus('');
    setCorrelationId('');
    onFilterChange({});
  };

  return (
    <div className="bg-[var(--color-background-alt)] border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] p-6 shadow-xl shadow-black/5 backdrop-blur-sm">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl md:rounded-[28px] bg-[var(--color-action-primary)]/[0.04] border border-[var(--color-border-subtle)] flex items-center justify-center text-[var(--color-action-primary)] shadow-sm">
             <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
               <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
             </svg>
          </div>
          <div>
            <h3 className="font-bold text-[var(--color-text-primary)] tracking-tight">System Audit Filter</h3>
            <p className="text-xs text-[var(--color-text-secondary)]">Search thru technical API request logs</p>
          </div>
        </div>
        
        <div className="flex items-center space-x-3">
           <button 
             onClick={() => setExpanded(!expanded)}
             className="px-4 py-2 text-xs font-bold uppercase tracking-wider text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
           >
             {expanded ? 'Collapse' : 'Refine Search'}
           </button>
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
      </div>

      <div className={`grid gap-4 md:grid-cols-4 lg:grid-cols-5 mt-6 animate-in slide-in-from-top-4 duration-300 ${!expanded ? 'hidden md:grid' : ''}`}>
        <div className="space-y-1.5">
          <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest px-1">User ID</label>
          <input 
            type="number" 
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="e.g. 1"
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-xs focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)]"
          />
        </div>
        
        <div className="space-y-1.5">
          <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest px-1">HTTP Method</label>
          <select 
            value={method}
            onChange={(e) => setMethod(e.target.value)}
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-xs focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)]"
          >
            <option value="">Any Method</option>
            <option value="GET">GET</option>
            <option value="POST">POST</option>
            <option value="PUT">PUT</option>
            <option value="DELETE">DELETE</option>
            <option value="PATCH">PATCH</option>
          </select>
        </div>

        <div className="space-y-1.5">
          <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest px-1">Status Code</label>
          <input 
            type="number" 
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            placeholder="e.g. 200"
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-xs focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)]"
          />
        </div>

        <div className="space-y-1.5 md:col-span-1 lg:col-span-2">
          <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest px-1">Correlation ID</label>
          <input 
            type="text" 
            value={correlationId}
            onChange={(e) => setCorrelationId(e.target.value)}
            placeholder="Trace ID (Partial ok)"
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-xs focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)]"
          />
        </div>

        <div className="md:col-start-4 lg:col-start-5 flex items-end">
          <button 
            onClick={applyFilters}
            className="w-full h-[38px] bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] text-[var(--color-action-primary-text)] rounded-xl text-xs font-bold uppercase tracking-widest shadow-lg shadow-[var(--color-action-primary)]/20 active:scale-95 transition-all focus:ring-2 focus:ring-[var(--color-ring-focus)]/50 outline-none"
          >
            Apply Filter
          </button>
        </div>
      </div>
    </div>
  );
}
