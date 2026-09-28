import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useGetApiV1Sources } from '../api/generated/content-sources/content-sources';
import { Pagination } from '../components/ui/Pagination';
import { Skeleton } from '../components/ui/Skeleton';
import { Link } from '@tanstack/react-router';
import { Search, Globe } from 'lucide-react';
import { YoutubeIcon } from '../components/ui/YoutubeIcon';
import { useDebounce } from '../hooks/useDebounce';
import { cn } from '../util/cn';

export function SourcesLibraryPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size] = useState(12);
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 500);

  const { data: response, isLoading } = useGetApiV1Sources({
    page,
    size,
    search: debouncedSearch || undefined
  }, {
    query: {
      placeholderData: (previousData) => previousData,
    }
  });

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const data = response?.data;
  const items = data?.items ?? [];
  const totalCount = data?.totalCount ?? 0;
  const totalPages = Math.ceil(totalCount / size);

  function renderContent() {
    if (isLoading && items.length === 0) {
      return (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {['a', 'b', 'c', 'd', 'e', 'f'].map((id) => (
            <Skeleton key={id} className="h-48 w-full" />
          ))}
        </div>
      );
    }

    if (items.length === 0) {
      // Sources are scoped to the user's topics (POV-219): without a search, an empty list means none yet
      const hasNoSources = !search && !debouncedSearch;
      return (
        <div className="glass border border-dashed border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] p-12 text-center">
          <div className="w-16 h-16 rounded-full bg-[var(--color-surface-deep)] flex items-center justify-center mx-auto mb-4 text-[var(--color-text-secondary)]">
            <Search className="w-8 h-8" strokeWidth={1.5} />
          </div>
          {hasNoSources ? (
            <>
              <h3 className="text-lg font-bold text-[var(--color-text-primary)]">{t('sources.library.empty_title')}</h3>
              <p className="text-sm text-[var(--color-text-secondary)] mt-1">{t('sources.library.empty_hint')}</p>
              <Link to="/topics" className="inline-block mt-4 text-sm font-bold text-[var(--color-action-primary)] hover:underline">
                {t('sources.library.empty_link')}
              </Link>
            </>
          ) : (
            <h3 className="text-lg font-bold text-[var(--color-text-primary)]">{t('sources.library.no_results')}</h3>
          )}
        </div>
      );
    }

    return (
      <>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {items.map((source) => (
            <Link
              key={source.id}
              to="/sources/$sourceId"
              params={{ sourceId: source.id?.toString() ?? '' }}
              className="group glass border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] p-6 hover:bg-[var(--color-surface-hover)] hover:border-[var(--color-action-primary)]/50 transition-all hover:scale-[1.02] active:scale-[0.98] relative overflow-hidden"
            >
              <div className="absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity pointer-events-none">
                {source.platform === 'YOUTUBE' ? <YoutubeIcon size={64} strokeWidth={1.5} /> : <Globe size={64} strokeWidth={1.5} />}
              </div>

              <div className="flex items-start justify-between mb-4">
                <div className={cn("w-10 h-10 rounded-xl flex items-center justify-center shadow-inner bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)]")}>
                  {source.platform === 'YOUTUBE' ? <YoutubeIcon size={20} strokeWidth={1.5} /> : <Globe size={20} strokeWidth={1.5} />}
                </div>
              </div>

              <h3 className="font-bold text-[var(--color-text-primary)] text-lg mb-1 truncate group-hover:text-[var(--color-action-primary)] transition-colors">
                {source.name}
              </h3>
              <p className="text-xs font-mono text-[var(--color-text-secondary)] mb-3 opacity-70">
                {source.sourceIdentifier}
              </p>

              <p className="text-sm text-[var(--color-text-secondary)] line-clamp-2 mb-4 h-10">
                {source.description ?? t('sources.library.no_description')}
              </p>

              <div className="flex items-center justify-between pt-4 border-t border-[var(--color-border-subtle)]/50">
                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-action-primary)] bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] px-2 py-1 rounded-xl">
                  {source.platform}
                </div>
                <div className="flex items-center gap-1 text-xs text-[var(--color-text-secondary)]">
                  <span className="font-bold text-[var(--color-text-primary)]">{source.subscriberCount?.toLocaleString() ?? 0}</span>
                  <span>{t('sources.library.subscribers')}</span>
                </div>
              </div>
            </Link>
          ))}
        </div>

        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
          isLoading={isLoading}
        />
      </>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h2 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] mb-2 text-glow">{t('sources.library.title')}</h2>
          <p className="text-[var(--color-text-secondary)] max-w-lg">
            {t('sources.library.subtitle')}
          </p>
        </div>

        <div className="flex items-center space-x-2 glass border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] px-4 py-2">
          <div className="text-right">
            <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">{t('sources.library.counter_label')}</div>
            <div className="text-sm font-mono text-[var(--color-action-primary)] font-bold">{t('sources.library.counter_value', { count: totalCount })}</div>
          </div>
          <div className="w-8 h-8 rounded-xl bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] flex items-center justify-center text-[var(--color-action-primary)] ml-2">
            <Globe className="w-4 h-4" strokeWidth={1.5} />
          </div>
        </div>
      </div>

      <div className="bg-[var(--color-background-alt)] border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] p-6 shadow-xl shadow-black/5 backdrop-blur-sm">
        <div className="relative group">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--color-text-secondary)] group-focus-within:text-[var(--color-action-primary)] transition-colors" strokeWidth={1.5} />
          <input
            type="text"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder={t('sources.library.search_placeholder')}
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] pl-11 pr-4 py-3 text-sm focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none text-[var(--color-text-primary)] shadow-inner"
          />
        </div>
      </div>

      <div className="space-y-4">
        {renderContent()}
      </div>
    </div>
  );
}
