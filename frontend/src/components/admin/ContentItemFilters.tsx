import { useGetApiV1Topics } from '../../api/generated/topics/topics';
import { ProcessingStatus } from '../../api/generated/model/processingStatus';
import { useTranslation } from 'react-i18next';

export interface ContentItemFiltersProps {
  onFilterChange: (filters: { status?: ProcessingStatus[]; topicId?: number }) => void;
}

export function ContentItemFilters({ onFilterChange }: ContentItemFiltersProps) {
  const { t } = useTranslation();
  const { data: topicsData } = useGetApiV1Topics({ page: 0, size: 100 });
  const topics = topicsData?.data?.items || [];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 p-6 glass border border-[var(--color-border-subtle)] rounded-3xl shadow-xl animate-in fade-in duration-1000">
      <div className="space-y-2">
        <label className="text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] px-1">
          {t('contentManagement.filters.topic')}
        </label>
        <select 
          onChange={(e) => onFilterChange({ topicId: e.target.value ? Number(e.target.value) : undefined })}
          className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] text-[var(--color-text-primary)] rounded-xl px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition-all appearance-none cursor-pointer"
        >
          <option value="">{t('common.filter')}</option>
          {topics.map(topic => (
            <option key={topic.id} value={topic.id}>{topic.name}</option>
          ))}
        </select>
      </div>

      <div className="space-y-2">
        <label className="text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] px-1">
          {t('contentManagement.filters.status')}
        </label>
        <select 
          onChange={(e) => onFilterChange({ status: e.target.value ? [e.target.value as ProcessingStatus] : undefined })}
          className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] text-[var(--color-text-primary)] rounded-xl px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition-all appearance-none cursor-pointer"
        >
          <option value="">{t('common.filter')}</option>
          {Object.values(ProcessingStatus).map(status => (
            <option key={status} value={status}>{status}</option>
          ))}
        </select>
      </div>

      <div className="flex items-end">
        <button 
          onClick={() => {
            const selects = document.querySelectorAll('select');
            selects.forEach(s => (s as HTMLSelectElement).value = '');
            onFilterChange({});
          }}
          className="w-full md:w-auto px-6 py-3 rounded-xl bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-deep)] transition-all font-medium border border-[var(--color-border-subtle)]"
        >
          {t('common.cancel')}
        </button>
      </div>
    </div>
  );
}
