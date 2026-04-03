import { useState, useEffect } from 'react';
import { useGetApiV1Briefings } from '../api/generated/briefings/briefings';
import { useGetApiV1Topics } from '../api/generated/topics/topics';
import { useGetApiV1Sources } from '../api/generated/content-sources/content-sources';
import { Pagination } from '../components/ui/Pagination';
import { Skeleton } from '../components/ui/Skeleton';
import { Link } from '@tanstack/react-router';
import { Search, FileText, Calendar, X, SlidersHorizontal } from 'lucide-react';
import { useDebounce } from '../hooks/useDebounce';
import { useTranslation } from 'react-i18next';

import { ExportMenu } from '../components/briefing/ExportMenu';

/**
 * BriefingLibraryPage displays a searchable, filterable, paginated archive of all briefings.
 * Part of POV-161 implementation.
 */
export function BriefingLibraryPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [search, setSearch] = useState('');
  const [topicId, setTopicId] = useState<number | undefined>(undefined);
  const [sourceId, setSourceId] = useState<number | undefined>(undefined);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const debouncedSearch = useDebounce(search, 300);

  const { data: response, isLoading } = useGetApiV1Briefings({
    page,
    size,
    search: debouncedSearch || undefined,
    topicId: topicId,
    sourceId: sourceId,
    startDate: startDate || undefined,
    endDate: endDate || undefined,
  }, {
    query: {
      placeholderData: (previousData) => previousData,
    }
  });

  const { data: topicsResponse } = useGetApiV1Topics({ size: 100 });
  const { data: sourcesResponse } = useGetApiV1Sources({ size: 100 });

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, topicId, sourceId, startDate, endDate]);

  const data = response?.data;
  const items = data?.items || [];
  const totalCount = data?.totalCount || 0;
  const totalPages = Math.ceil(totalCount / size);

  const topics = (topicsResponse?.data?.items || []) as { id?: number; name?: string }[];
  const sources = (sourcesResponse?.data?.items || []) as { id?: number; name?: string }[];

  const hasActiveFilters = debouncedSearch || topicId || sourceId || startDate || endDate;

  const clearFilters = () => {
    setSearch('');
    setTopicId(undefined);
    setSourceId(undefined);
    setStartDate('');
    setEndDate('');
    setPage(0);
  };

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h2 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] mb-2 text-glow">
            {t('briefingLibrary.title')}
          </h2>
          <p className="text-[var(--color-text-secondary)] max-w-lg">
            {t('briefingLibrary.subtitle')}
          </p>
        </div>

        <div className="flex items-center space-x-2 glass border border-[var(--color-border-subtle)] rounded-2xl px-4 py-2">
          <div className="text-right">
            <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">
              {t('briefingLibrary.stats.totalBriefings')}
            </div>
            <div className="text-sm font-mono text-blue-500 font-bold">
              {totalCount.toLocaleString()} {t('briefingLibrary.stats.totalBriefingsDesc')}
            </div>
          </div>
          <div className="w-8 h-8 rounded-lg bg-blue-500/10 flex items-center justify-center text-blue-500 ml-2">
            <FileText className="w-4 h-4" />
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-[var(--color-background-alt)] border border-[var(--color-border-subtle)] rounded-3xl p-6 shadow-xl shadow-black/5 backdrop-blur-sm space-y-4">
        {/* Search */}
        <div className="relative group">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--color-text-secondary)] group-focus-within:text-blue-500 transition-colors" />
          <input
            id="briefing-library-search"
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t('briefingLibrary.search.placeholder')}
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-2xl pl-11 pr-4 py-3 text-sm focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all outline-none text-[var(--color-text-primary)] shadow-inner"
          />
        </div>

        {/* Filter Row */}
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
          <div className="flex items-center gap-2 text-[var(--color-text-secondary)]">
            <SlidersHorizontal className="w-4 h-4 flex-shrink-0" />
          </div>

          {/* Topic Filter */}
          <select
            id="briefing-library-topic-filter"
            value={topicId || ''}
            onChange={(e) => setTopicId(e.target.value ? Number(e.target.value) : undefined)}
            className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all min-w-[140px]"
          >
            <option value="">{t('briefingLibrary.filters.topic')}</option>
            {topics.map((topic) => (
              <option key={topic.id} value={topic.id}>{topic.name}</option>
            ))}
          </select>

          {/* Source Filter */}
          <select
            id="briefing-library-source-filter"
            value={sourceId || ''}
            onChange={(e) => setSourceId(e.target.value ? Number(e.target.value) : undefined)}
            className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all min-w-[140px]"
          >
            <option value="">{t('briefingLibrary.filters.source')}</option>
            {sources.map((source) => (
              <option key={source.id} value={source.id}>{source.name}</option>
            ))}
          </select>

          {/* Date Range */}
          <div className="flex items-center gap-2">
            <div className="relative">
              <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[var(--color-text-secondary)] pointer-events-none" />
              <input
                id="briefing-library-start-date"
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl pl-9 pr-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                placeholder={t('briefingLibrary.filters.startDate')}
              />
            </div>
            <span className="text-[var(--color-text-secondary)] text-xs">—</span>
            <div className="relative">
              <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[var(--color-text-secondary)] pointer-events-none" />
              <input
                id="briefing-library-end-date"
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl pl-9 pr-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                placeholder={t('briefingLibrary.filters.endDate')}
              />
            </div>
          </div>

          {/* Reset Filters */}
          {hasActiveFilters && (
            <button
              id="briefing-library-clear-filters"
              onClick={clearFilters}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-red-500/30 text-red-400 hover:bg-red-500/10 transition-all text-xs font-bold uppercase tracking-wider cursor-pointer"
            >
              <X className="w-3.5 h-3.5" />
              {t('briefingLibrary.filters.reset')}
            </button>
          )}
        </div>
      </div>

      {/* Data Table */}
      <div className="space-y-4">
        {(() => {
          if (isLoading && items.length === 0) {
            return (
              <div className="space-y-3">
                {[1, 2, 3, 4, 5].map((id) => (
                  <Skeleton key={`skeleton-${id}`} className="h-16 w-full" />
                ))}
              </div>
            );
          }

          if (items.length === 0) {
            return (
              <div className="glass border border-dashed border-[var(--color-border-subtle)] rounded-3xl p-12 text-center">
                <div className="w-16 h-16 rounded-full bg-[var(--color-surface-deep)] flex items-center justify-center mx-auto mb-4 text-[var(--color-text-secondary)]">
                  <Search className="w-8 h-8" />
                </div>
                <h3 className="text-lg font-bold text-[var(--color-text-primary)]">
                  {t('briefingLibrary.empty.title')}
                </h3>
                <p className="text-sm text-[var(--color-text-secondary)] mt-1">
                  {t('briefingLibrary.empty.description')}
                </p>
                {hasActiveFilters && (
                  <button
                    onClick={clearFilters}
                    className="mt-4 px-4 py-2 rounded-xl bg-blue-600/10 text-blue-400 hover:bg-blue-600/20 transition-all text-sm font-medium cursor-pointer"
                  >
                    {t('briefingLibrary.empty.clearFilters')}
                  </button>
                )}
              </div>
            );
          }

          return (
          <>
            {/* Table */}
            <div className="glass border border-[var(--color-border-subtle)] rounded-3xl overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-[var(--color-border-subtle)]">
                      <th className="text-left px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-[var(--color-text-secondary)]">
                        {t('briefingLibrary.table.date')}
                      </th>
                      <th className="text-left px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-[var(--color-text-secondary)]">
                        {t('briefingLibrary.table.topic')}
                      </th>
                      <th className="text-left px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-[var(--color-text-secondary)]">
                        {t('briefingLibrary.table.title')}
                      </th>
                      <th className="text-right px-6 py-4 text-[10px] font-bold uppercase tracking-widest text-[var(--color-text-secondary)]">
                        {t('briefingLibrary.table.actions')}
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--color-border-subtle)]/50">
                    {items.map((briefing) => (
                      <tr
                        key={briefing.id}
                        className="hover:bg-[var(--color-surface-hover)] transition-colors group"
                      >
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className="text-xs font-mono text-[var(--color-text-secondary)]">
                            {briefing.briefingDate ? new Date(briefing.briefingDate + 'T00:00:00').toLocaleDateString() : '—'}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className="text-xs font-bold uppercase tracking-wider text-blue-500 bg-blue-500/10 px-2 py-1 rounded-lg">
                            {briefing.topicName}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <span className="text-[var(--color-text-primary)] font-medium group-hover:text-blue-400 transition-colors line-clamp-1">
                            {briefing.title}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <div className="flex items-center justify-end gap-2">
                            {briefing.id != null && (
                              <ExportMenu 
                                type="briefing"
                                data={{
                                  id: briefing.id,
                                  uuid: briefing.uuid ?? null,
                                  title: briefing.title || '',
                                  topicName: briefing.topicName || '',
                                  date: briefing.briefingDate || '',
                                }}
                                variant="ghost"
                                className="opacity-0 group-hover:opacity-100 transition-opacity"
                              />
                            )}
                            {briefing.id != null && (
                              <Link
                                to="/briefings/$briefingId"
                                params={{ briefingId: String(briefing.id) }}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-[var(--color-border-subtle)] glass hover:bg-[var(--color-surface-hover)] hover:border-blue-500/50 transition-all text-xs font-bold uppercase tracking-wider text-[var(--color-text-primary)] hover:text-blue-400"
                              >
                                <FileText className="w-3.5 h-3.5" />
                                {t('briefingLibrary.table.view')}
                              </Link>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={setPage}
              isLoading={isLoading}
            />
          </>
        );
      })()}
      </div>
    </div>
  );
}
