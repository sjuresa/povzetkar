import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import type { BackgroundJobDTO, JobType } from '../../api/generated/model';
import {
  getGetApiV1AdminJobsQueryKey,
  postApiV1AdminJobsIdRestart,
} from '../../api/generated/admin/admin';
import { JobAuditModal } from './JobAuditModal';

const JOB_TYPE_LABELS: Record<JobType, string> = {
  SYNC_YOUTUBE_FEEDS: 'Sync Feeds',
  GENERATE_BRIEFINGS: 'Generate Briefings',
  PROCESS_TOPICS_AND_SUMMARIZE: 'Process & Summarize',
  GENERATE_TOPIC_BRIEFING: 'Topic Briefing',
  GENERATE_TOPIC_SUMMARIES: 'Topic Summaries',
  GENERATE_ITEM_SUMMARY: 'Item Summary',
  RETRY_ITEM_PROCESSING: 'Retry Item',
};

const JOB_STATUS_BADGE: Record<string, string> = {
  QUEUED: 'bg-amber-500/10 text-amber-400 border border-amber-500/20',
  RUNNING: 'bg-[var(--color-action-primary)]/[0.08] text-[var(--color-action-primary)] border border-[var(--color-action-primary)]/20',
  COMPLETED: 'bg-green-500/10 text-green-400 border border-green-500/20',
  FAILED: 'bg-red-500/10 text-red-400 border border-red-500/20',
};
const JOB_STATUS_BADGE_DEFAULT = 'bg-[var(--color-surface-deep)] text-[var(--color-text-secondary)] border border-[var(--color-border-subtle)]';

function formatDuration(startedAt: string | null | undefined, endedAt: string | null | undefined): string {
  if (!startedAt) return '—';
  const start = new Date(startedAt).getTime();
  const end = endedAt ? new Date(endedAt).getTime() : Date.now();
  const totalSeconds = Math.max(0, Math.floor((end - start) / 1000));
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function formatStartedAt(value: string | null | undefined): string {
  if (!value) return '—';
  return new Date(value).toLocaleString();
}

type SortDirection = 'asc' | 'desc';

interface AdminJobsTableProps {
  readonly jobs: BackgroundJobDTO[];
  readonly sort: string;
  readonly onSortChange: (sort: string) => void;
}

export function AdminJobsTable({ jobs, sort, onSortChange }: AdminJobsTableProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [auditJobId, setAuditJobId] = useState<string | null>(null);

  const currentDirection: SortDirection = sort.endsWith(',asc') ? 'asc' : 'desc';

  const handleSortToggle = () => {
    onSortChange(currentDirection === 'desc' ? 'startedAt,asc' : 'startedAt,desc');
  };

  const invalidateJobs = () => void queryClient.invalidateQueries({ queryKey: getGetApiV1AdminJobsQueryKey() });

  const restart = useMutation({
    mutationFn: (jobId: string) => postApiV1AdminJobsIdRestart(jobId),
    onSuccess: () => {
      toast.success(t('admin.jobs.toast.restarted'));
      invalidateJobs();
    },
    onError: () => {
      toast.error(t('admin.jobs.toast.restart_failed'));
      invalidateJobs();
    },
  });

  return (
    <>
      <div className="overflow-x-auto rounded-xl md:rounded-[28px] border border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]/50 shadow-sm">
        <table className="w-full text-left border-collapse">
          <thead className="bg-[var(--color-surface-deep)]/80 border-b border-[var(--color-border-subtle)]">
            <tr>
              <th
                className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest cursor-pointer hover:text-[var(--color-text-primary)] transition-colors select-none"
                onClick={handleSortToggle}
              >
                {t('admin.jobs.column.started_at')}
                <span className="ml-1 text-[var(--color-action-primary)]">
                  {currentDirection === 'desc' ? '↓' : '↑'}
                </span>
              </th>
              <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">{t('admin.jobs.column.type')}</th>
              <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">{t('admin.jobs.column.duration')}</th>
              <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">{t('admin.jobs.column.status')}</th>
              <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest text-right">{t('admin.jobs.column.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--color-border-subtle)]/50">
            {jobs.map((job) => (
              <tr key={job.jobId} className="hover:bg-[var(--color-surface-hover)]/30 transition-colors group">
                <td className="px-6 py-4">
                  <div className="text-xs font-mono text-[var(--color-text-secondary)] group-hover:text-[var(--color-action-primary)] transition-colors">
                    {formatStartedAt(job.startedAt)}
                  </div>
                </td>
                <td className="px-6 py-4">
                  <div className="text-sm font-medium text-[var(--color-text-primary)]">
                    {job.jobType ? JOB_TYPE_LABELS[job.jobType] : '—'}
                  </div>
                </td>
                <td className="px-6 py-4">
                  <div className="text-xs font-mono text-[var(--color-text-secondary)]">
                    {formatDuration(job.startedAt, job.endedAt)}
                  </div>
                </td>
                <td className="px-6 py-4">
                  <span className={`px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-tight ${JOB_STATUS_BADGE[job.status ?? ''] ?? JOB_STATUS_BADGE_DEFAULT}`}>
                    {job.status}
                  </span>
                </td>
                <td className="px-6 py-4">
                  <div className="flex items-center justify-end gap-2">
                    {job.status === 'FAILED' && (
                      <button
                        onClick={() => job.jobId && restart.mutate(job.jobId)}
                        disabled={restart.isPending}
                        className="px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider bg-[var(--color-action-primary)]/[0.08] text-[var(--color-action-primary)] border border-[var(--color-action-primary)]/20 rounded-lg hover:bg-[var(--color-action-primary)]/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title={t('admin.jobs.action.restart')}
                      >
                        ↻
                      </button>
                    )}
                    <button
                      onClick={() => setAuditJobId(job.jobId ?? null)}
                      className="px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider bg-[var(--color-surface-elevated)] text-[var(--color-text-secondary)] border border-[var(--color-border-subtle)] rounded-lg hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-hover)] transition-colors"
                      title={t('admin.jobs.action.view_audit')}
                    >
                      📋
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {jobs.length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-12 text-center text-[var(--color-text-secondary)] italic">
                  {t('admin.jobs.empty')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <JobAuditModal jobId={auditJobId} onClose={() => setAuditJobId(null)} />
    </>
  );
}
