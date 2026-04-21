import { useState, useEffect } from 'react';
import { useGetApiV1Sources } from '../../api/generated/content-sources/content-sources';
import type { ContentSourceDTO } from '../../api/generated/model';
import { Search, X, Loader2, Plus, ChevronRight, ChevronLeft, AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { QuickSourceIntake } from './QuickSourceIntake';

interface SourceSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (source: ContentSourceDTO) => void;
  excludeIds?: number[];
}

export function SourceSearchModal({ isOpen, onClose, onSelect, excludeIds = [] }: SourceSearchModalProps) {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);
  const [showQuickAdd, setShowQuickAdd] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const { data: sourcesResponse, isLoading } = useGetApiV1Sources(
    { 
      search: debouncedSearch, 
      page, 
      size: 5, 
      excludeIds: excludeIds.length > 0 ? excludeIds.join(',') : undefined 
    },
    { query: { enabled: isOpen && !showQuickAdd } }
  );

  if (!isOpen) return null;

  const sources = sourcesResponse?.data?.items || [];
  const totalCount = sourcesResponse?.data?.totalCount || 0;
  const totalPages = Math.ceil(totalCount / 5);

  const filteredSources = sources;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-[var(--color-surface-elevated)] w-full max-w-lg rounded-xl md:rounded-[28px] shadow-2xl border border-[var(--color-border-subtle)] overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-6 border-b border-[var(--color-border-subtle)] flex items-center justify-between bg-[var(--color-background-alt)]">
          <div>
            <h2 className="text-xl font-bold text-[var(--color-text-primary)] italic tracking-tight">{t('topics.form.search_modal.title')}</h2>
            <p className="text-xs text-[var(--color-text-secondary)] font-medium">{t('topics.form.search_modal.subtitle')}</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 hover:bg-[var(--color-surface-hover)] rounded-xl transition-colors"
          >
            <X className="w-5 h-5 text-[var(--color-text-secondary)]"  strokeWidth={1.5}/>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 scrollbar-hide">
          {showQuickAdd ? (
            <QuickSourceIntake
              onSuccess={(source) => {
                onSelect(source);
                onClose();
              }}
              onCancel={() => setShowQuickAdd(false)}
            />
          ) : (
            <div className="space-y-6">
              {/* Search Box */}
              <div className="relative group">
                <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--color-text-secondary)] group-focus-within:text-[var(--color-action-primary)] transition-colors"  strokeWidth={1.5}/>
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder={t('topics.form.search_modal.search_placeholder')}
                  className="w-full pl-11 pr-4 py-3 bg-[var(--color-background-base)] border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 text-sm text-[var(--color-text-primary)] transition-all"
                  autoFocus
                />
              </div>

              {/* Results */}
              <div className="space-y-2">
                {isLoading ? (
                  <div className="flex flex-col items-center justify-center py-12 text-[var(--color-text-secondary)]">
                    <Loader2 className="w-8 h-8 animate-spin mb-3 text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
                    <span className="text-xs font-bold uppercase tracking-widest">{t('topics.form.search_modal.searching')}</span>
                  </div>
                ) : filteredSources.length > 0 ? (
                  <>
                    {filteredSources.map((source) => (
                      <button
                        key={source.id}
                        onClick={() => {
                          onSelect(source);
                          onClose();
                        }}
                        className="w-full group flex items-center justify-between p-4 bg-[var(--color-background-alt)] hover:bg-[var(--color-surface-hover)] rounded-xl md:rounded-[28px] border border-transparent hover:border-[var(--color-action-primary)]/30 transition-all text-left"
                      >
                        <div className="flex items-center min-w-0">
                          <div className="w-10 h-10 rounded-xl bg-[var(--color-bg-secondary)] border border-[var(--color-border-subtle)] flex items-center justify-center mr-3 shrink-0 shadow-sm group-hover:scale-110 transition-transform">
                            <span className="text-[10px] font-black italic text-[var(--color-action-primary)]">
                              {source.platform?.[0]}
                            </span>
                          </div>
                          <div className="truncate">
                            <h4 className="text-sm font-bold text-[var(--color-text-primary)] truncate">
                              {source.name || source.sourceIdentifier}
                            </h4>
                            <p className="text-[10px] text-[var(--color-text-secondary)] font-medium truncate uppercase tracking-tight">
                              {source.platform} • {source.sourceIdentifier}
                            </p>
                          </div>
                        </div>
                        <ChevronRight className="w-4 h-4 text-[var(--color-text-secondary)] group-hover:translate-x-1 group-hover:text-[var(--color-action-primary)] transition-all"  strokeWidth={1.5}/>
                      </button>
                    ))}

                    {/* Pagination */}
                    {totalPages > 1 && (
                      <div className="flex items-center justify-center gap-4 pt-4">
                        <button
                          disabled={page === 0}
                          onClick={() => setPage(p => p - 1)}
                          className="p-1.5 disabled:opacity-30 rounded-xl hover:bg-[var(--color-surface-hover)] transition-colors"
                        >
                          <ChevronLeft className="w-4 h-4 text-[var(--color-text-primary)]"  strokeWidth={1.5}/>
                        </button>
                        <span className="text-xs font-bold text-[var(--color-text-secondary)]">
                          {t('topics.form.search_modal.page_info', { current: page + 1, total: totalPages })}
                        </span>
                        <button
                          disabled={page >= totalPages - 1}
                          onClick={() => setPage(p => p + 1)}
                          className="p-1.5 disabled:opacity-30 rounded-xl hover:bg-[var(--color-surface-hover)] transition-colors"
                        >
                          <ChevronRight className="w-4 h-4 text-[var(--color-text-primary)]"  strokeWidth={1.5}/>
                        </button>
                      </div>
                    )}
                  </>
                ) : (
                  <div className="text-center py-12 bg-[var(--color-background-alt)] rounded-xl md:rounded-[28px] border border-dashed border-[var(--color-border-subtle)]">
                    <AlertCircle className="w-8 h-8 text-[var(--color-text-secondary)] opacity-50 mx-auto mb-2"  strokeWidth={1.5}/>
                    <p className="text-sm font-bold text-[var(--color-text-secondary)] italic">{t('topics.form.search_modal.no_results')}</p>
                    <p className="text-[10px] text-[var(--color-text-secondary)] opacity-80 font-medium mt-1">{t('topics.form.search_modal.no_results_tip')}</p>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        {!showQuickAdd && (
          <div className="p-6 border-t border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]">
            <button
              onClick={() => setShowQuickAdd(true)}
              className="w-full flex items-center justify-center gap-2 py-4 bg-[var(--color-surface-elevated)] hover:bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] text-sm font-bold text-[var(--color-action-primary)] group transition-all"
            >
              <Plus className="w-4 h-4 group-hover:rotate-90 transition-transform"  strokeWidth={1.5}/>
              {t('topics.form.search_modal.quick_add_trigger')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
