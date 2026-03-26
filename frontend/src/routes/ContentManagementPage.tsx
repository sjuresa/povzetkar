import { useState } from 'react';
import { useGetApiV1ContentItems } from '../api/generated/content-items/content-items';
import { ContentItemTable } from '../components/admin/ContentItemTable';
import { ContentItemFilters } from '../components/admin/ContentItemFilters';
import type { ProcessingStatus } from '../api/generated/model/processingStatus';
import type { ContentItem } from '../api/generated/model/contentItem';
import { Skeleton } from '../components/ui/Skeleton';
import { Pagination } from '../components/ui/Pagination';
import { useTranslation } from 'react-i18next';
import { SummaryModal } from '../components/admin/SummaryModal';

export function ContentManagementPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [summaryItemId, setSummaryItemId] = useState<number | null>(null);
  const [filters, setFilters] = useState<{
    status?: ProcessingStatus[];
    topicId?: number;
  }>({});

  const { data: response, isLoading } = useGetApiV1ContentItems({
    page,
    size,
    status: filters.status,
    topicId: filters.topicId,
    sort: ['publishedAt,desc']
  }, {
    query: {
      placeholderData: (previousData) => previousData,
    }
  });

  const data = response?.data;
  const items = (data?.items || []) as ContentItem[];
  const totalCount = data?.totalCount || 0;
  const totalPages = Math.ceil(totalCount / size);

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h2 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] mb-2 text-glow">
            {t('contentManagement.title')}
          </h2>
          <p className="text-[var(--color-text-secondary)] max-w-2xl text-lg">
             {t('contentManagement.subtitle')}
          </p>
        </div>
        
        <div className="flex items-center space-x-2 glass border border-[var(--color-border-subtle)] rounded-2xl px-5 py-3 shadow-lg shadow-blue-900/10">
          <div className="text-right">
            <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Global Items</div>
            <div className="text-xl font-mono text-blue-500 font-bold">{totalCount.toLocaleString()}</div>
          </div>
          <div className="w-10 h-10 rounded-xl bg-blue-500/10 flex items-center justify-center text-blue-500 ml-3">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
            </svg>
          </div>
        </div>
      </div>

      <ContentItemFilters onFilterChange={(newFilters) => {
        setFilters(newFilters);
        setPage(0);
      }} />

      <div className="space-y-6">
        {isLoading && !items.length ? (
          <div className="space-y-4">
             {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-20 w-full rounded-2xl" />
             ))}
          </div>
        ) : items.length > 0 ? (
          <>
            <ContentItemTable items={items} onViewSummary={setSummaryItemId} />
            <div className="flex justify-center pt-4">
              <Pagination 
                page={page} 
                totalPages={totalPages} 
                onPageChange={setPage} 
                isLoading={isLoading} 
              />
            </div>
          </>
        ) : (
          <div className="glass border border-[var(--color-border-dashed)] rounded-3xl p-20 text-center animate-pulse">
             <p className="text-[var(--color-text-secondary)] italic">{t('contentManagement.table.noItems')}</p>
          </div>
        )}
      </div>

      <SummaryModal 
        itemId={summaryItemId} 
        onClose={() => setSummaryItemId(null)} 
      />
    </div>
  );
}
