import type { AuditLogEntryDTO } from '../../api/generated/model';

interface AuditLogTableProps {
  logs: AuditLogEntryDTO[];
}

export function AuditLogTable({ logs }: AuditLogTableProps) {
  return (
    <div className="overflow-x-auto rounded-xl md:rounded-[28px] border border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]/50 shadow-sm">
      <table className="w-full text-left border-collapse">
        <thead className="bg-[var(--color-surface-deep)]/80 border-b border-[var(--color-border-subtle)]">
          <tr>
            <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Time</th>
            <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Method</th>
            <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Path</th>
            <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Status</th>
            <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">User ID</th>
            <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest text-right">Duration</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--color-border-subtle)]/50">
          {logs.map((log) => (
            <tr key={log.id} className="hover:bg-[var(--color-surface-hover)]/30 transition-colors group">
              <td className="px-6 py-4">
                <div className="text-xs font-mono text-[var(--color-text-secondary)] group-hover:text-[var(--color-action-primary)] transition-colors">
                  {log.createdAt ? new Date(log.createdAt).toLocaleString() : '-'}
                </div>
              </td>
              <td className="px-6 py-4">
                <span className={`px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-tight ${
                  log.httpMethod === 'GET' ? 'bg-[var(--color-action-primary)]/[0.08] text-[var(--color-action-primary)] border border-[var(--color-action-primary)]/20 shadow-sm' :
                  log.httpMethod === 'POST' ? 'bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] border border-[var(--color-action-primary)] shadow-md' :
                  log.httpMethod === 'DELETE' ? 'bg-[var(--color-surface-deep)] text-[var(--color-text-primary)] border border-[var(--color-border-primary)] shadow-sm opacity-80' :
                  'bg-[var(--color-surface-deep)] text-[var(--color-text-secondary)] border border-[var(--color-border-subtle)]'
                }`}>
                  {log.httpMethod}
                </span>
              </td>
              <td className="px-6 py-4">
                <div className="text-sm font-medium text-[var(--color-text-primary)] truncate max-w-xs" title={log.requestPath}>
                  {log.requestPath}
                </div>
                {log.correlationId && (
                  <div className="text-[10px] font-mono text-[var(--color-text-secondary)]/60 mt-1 uppercase tracking-tighter">
                    CID: {log.correlationId.substring(0, 8)}...
                  </div>
                )}
              </td>
              <td className="px-6 py-4">
                <div className={`text-sm font-bold ${
                  log.responseStatus && log.responseStatus >= 400 ? 'text-[var(--color-status-error)]' : 'text-[var(--color-status-success)]'
                }`}>
                  {log.responseStatus}
                </div>
              </td>
              <td className="px-6 py-4">
                <div className="text-xs text-[var(--color-text-secondary)]">
                  {log.userId || <span className="italic opacity-50">Anonymous</span>}
                </div>
              </td>
              <td className="px-6 py-4 text-right">
                <div className={`text-xs font-mono font-bold ${
                  log.durationMs && log.durationMs > 500 ? 'text-[var(--color-action-primary)]' : 'text-[var(--color-text-secondary)]'
                }`}>
                  {log.durationMs ? `${log.durationMs}ms` : '-'}
                </div>
              </td>
            </tr>
          ))}
          {logs.length === 0 && (
             <tr>
               <td colSpan={6} className="px-6 py-12 text-center text-[var(--color-text-secondary)] italic">
                 No audit logs found matching the criteria.
               </td>
             </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
