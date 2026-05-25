import { useTranslation } from 'react-i18next';
import { useGetApiV1AdminJobsIdAudit } from '../../api/generated/admin/admin';
import type { JobExecutionAudit } from '../../api/generated/model';
import { Skeleton } from '../ui/Skeleton';

interface JobAuditModalProps {
  readonly jobId: string | null;
  readonly onClose: () => void;
}

const STATUS_BADGE: Record<string, string> = {
  RUNNING: 'bg-[var(--color-action-primary)]/[0.08] text-[var(--color-action-primary)] border border-[var(--color-action-primary)]/20',
  COMPLETED: 'bg-green-500/10 text-green-400 border border-green-500/20',
  FAILED: 'bg-red-500/10 text-red-400 border border-red-500/20',
  QUEUED: 'bg-amber-500/10 text-amber-400 border border-amber-500/20',
};
const STATUS_BADGE_DEFAULT = 'bg-[var(--color-surface-deep)] text-[var(--color-text-secondary)] border border-[var(--color-border-subtle)]';

export function JobAuditModal({ jobId, onClose }: JobAuditModalProps) {
  const { t } = useTranslation();

  const { data: response, isLoading } = useGetApiV1AdminJobsIdAudit(
    jobId ?? '',
    { query: { enabled: jobId !== null } }
  );

  const entries = (response?.data as JobExecutionAudit[] | undefined) ?? [];

  if (jobId === null) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <button
        type="button"
        className="absolute inset-0 bg-black/50 backdrop-blur-sm cursor-default"
        onClick={onClose}
        aria-label={t('common.close')}
      />
      <div
        className="relative bg-[var(--color-background-alt)] border border-[var(--color-border-subtle)] rounded-[28px] shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col mx-4"
      >
        <div className="flex items-center justify-between p-6 border-b border-[var(--color-border-subtle)]">
          <h3 className="text-lg font-bold text-[var(--color-text-primary)] tracking-tight">
            {t('admin.jobs.audit_modal.title')}
          </h3>
          <button
            onClick={onClose}
            aria-label={t('common.close')}
            className="p-2 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors rounded-xl hover:bg-[var(--color-surface-hover)]"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="overflow-y-auto flex-1 p-6">
          {isLoading && (
            <div className="space-y-3">
              {['a', 'b', 'c', 'd'].map((k) => (
                <Skeleton key={k} className="h-14 w-full" />
              ))}
            </div>
          )}
          {!isLoading && entries.length === 0 && (
            <p className="text-center text-[var(--color-text-secondary)] italic py-8">
              {t('admin.jobs.audit_modal.empty')}
            </p>
          )}
          {!isLoading && entries.length > 0 && (
            <div className="space-y-2">
              {entries.map((entry) => (
                <div
                  key={entry.id}
                  className="flex items-start gap-3 p-3 rounded-xl bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)]"
                >
                  <span className={`mt-0.5 px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-tight shrink-0 ${STATUS_BADGE[entry.status ?? ''] ?? STATUS_BADGE_DEFAULT}`}>
                    {entry.status ? t(`admin.jobs.status.${entry.status.toLowerCase()}`) : '—'}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs text-[var(--color-text-primary)] break-words">{entry.message}</p>
                    {entry.itemTitle && (
                      <p className="text-[10px] text-[var(--color-text-secondary)] mt-0.5 truncate">{entry.itemTitle}</p>
                    )}
                  </div>
                  <span className="text-[10px] font-mono text-[var(--color-text-secondary)] shrink-0">
                    {entry.createdAt ? new Date(entry.createdAt).toLocaleTimeString() : ''}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
