import type { AuditLogEntryDTO } from '../../api/generated/model';

interface AuditLogTableProps {
  logs: AuditLogEntryDTO[];
}

export function AuditLogTable({ logs }: AuditLogTableProps) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/50 backdrop-blur-sm">
      <table className="w-full text-left border-collapse">
        <thead className="bg-slate-900 border-b border-slate-800">
          <tr>
            <th className="px-6 py-4 text-[10px] font-bold text-slate-500 uppercase tracking-widest">Time</th>
            <th className="px-6 py-4 text-[10px] font-bold text-slate-500 uppercase tracking-widest">Method</th>
            <th className="px-6 py-4 text-[10px] font-bold text-slate-500 uppercase tracking-widest">Path</th>
            <th className="px-6 py-4 text-[10px] font-bold text-slate-500 uppercase tracking-widest">Status</th>
            <th className="px-6 py-4 text-[10px] font-bold text-slate-500 uppercase tracking-widest">User ID</th>
            <th className="px-6 py-4 text-[10px] font-bold text-slate-500 uppercase tracking-widest text-right">Duration</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/50">
          {logs.map((log) => (
            <tr key={log.id} className="hover:bg-slate-800/30 transition-colors group">
              <td className="px-6 py-4">
                <div className="text-xs font-mono text-slate-400 group-hover:text-amber-400 transition-colors">
                  {log.createdAt ? new Date(log.createdAt).toLocaleString() : '-'}
                </div>
              </td>
              <td className="px-6 py-4">
                <span className={`px-2 py-1 rounded-md text-[10px] font-bold ${
                  log.httpMethod === 'GET' ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20' :
                  log.httpMethod === 'POST' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                  log.httpMethod === 'DELETE' ? 'bg-red-500/10 text-red-400 border border-red-500/20' :
                  'bg-slate-500/10 text-slate-400 border border-slate-500/20'
                }`}>
                  {log.httpMethod}
                </span>
              </td>
              <td className="px-6 py-4">
                <div className="text-sm font-medium text-slate-300 truncate max-w-xs" title={log.requestPath}>
                  {log.requestPath}
                </div>
                {log.correlationId && (
                  <div className="text-[10px] font-mono text-slate-600 mt-1 uppercase tracking-tighter">
                    CID: {log.correlationId.substring(0, 8)}...
                  </div>
                )}
              </td>
              <td className="px-6 py-4">
                <div className={`text-sm font-bold ${
                  log.responseStatus && log.responseStatus >= 400 ? 'text-red-400' : 'text-emerald-400'
                }`}>
                  {log.responseStatus}
                </div>
              </td>
              <td className="px-6 py-4">
                <div className="text-xs text-slate-500">
                  {log.userId || <span className="italic text-slate-700">Anonymous</span>}
                </div>
              </td>
              <td className="px-6 py-4 text-right">
                <div className={`text-xs font-mono ${
                  log.durationMs && log.durationMs > 500 ? 'text-amber-400' : 'text-slate-400'
                }`}>
                  {log.durationMs ? `${log.durationMs}ms` : '-'}
                </div>
              </td>
            </tr>
          ))}
          {logs.length === 0 && (
             <tr>
               <td colSpan={6} className="px-6 py-12 text-center text-slate-600 italic">
                 No audit logs found matching the criteria.
               </td>
             </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
