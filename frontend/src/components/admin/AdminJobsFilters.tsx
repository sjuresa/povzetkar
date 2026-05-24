import { useTranslation } from 'react-i18next';
import type { JobStatus, JobType } from '../../api/generated/model';

export interface AdminJobsFilters {
  status?: JobStatus;
  jobType?: JobType;
}

interface AdminJobsFiltersProps {
  readonly filters: AdminJobsFilters;
  readonly onFilterChange: (filters: AdminJobsFilters) => void;
}

const JOB_STATUSES: JobStatus[] = ['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'];
const JOB_TYPES: JobType[] = [
  'SYNC_YOUTUBE_FEEDS',
  'GENERATE_BRIEFINGS',
  'PROCESS_TOPICS_AND_SUMMARIZE',
  'GENERATE_TOPIC_BRIEFING',
  'GENERATE_TOPIC_SUMMARIES',
  'GENERATE_ITEM_SUMMARY',
  'RETRY_ITEM_PROCESSING',
];


export function AdminJobsFilters({ filters, onFilterChange }: AdminJobsFiltersProps) {
  const { t } = useTranslation();

  const handleStatusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onFilterChange({ ...filters, status: (e.target.value as JobStatus) || undefined });
  };

  const handleTypeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onFilterChange({ ...filters, jobType: (e.target.value as JobType) || undefined });
  };

  const handleClear = () => {
    onFilterChange({});
  };

  return (
    <div className="bg-[var(--color-background-alt)] border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] p-6 shadow-xl shadow-black/5 backdrop-blur-sm">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl md:rounded-[28px] bg-[var(--color-action-primary)]/[0.04] border border-[var(--color-border-subtle)] flex items-center justify-center text-[var(--color-action-primary)] shadow-sm">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
            </svg>
          </div>
          <div>
            <h3 className="font-bold text-[var(--color-text-primary)] tracking-tight">{t('admin.jobs.title')}</h3>
            <p className="text-xs text-[var(--color-text-secondary)]">{t('admin.jobs.subtitle')}</p>
          </div>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest px-1">
              {t('admin.jobs.filter.status')}
            </label>
            <select
              value={filters.status ?? ''}
              onChange={handleStatusChange}
              className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-xs focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)]"
            >
              <option value="">{t('admin.jobs.filter.all_statuses')}</option>
              {JOB_STATUSES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest px-1">
              {t('admin.jobs.filter.type')}
            </label>
            <select
              value={filters.jobType ?? ''}
              onChange={handleTypeChange}
              className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-xs focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)]"
            >
              <option value="">{t('admin.jobs.filter.all_types')}</option>
              {JOB_TYPES.map((jt) => (
                <option key={jt} value={jt}>{t(`admin.jobs.type.${jt.toLowerCase()}`)}</option>
              ))}
            </select>
          </div>

          <div className="flex items-end pb-0.5">
            <button
              onClick={handleClear}
              className="p-2 text-[var(--color-text-secondary)] hover:text-[var(--color-status-error)] transition-colors"
              title={t('admin.jobs.filter.clear_title')}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
