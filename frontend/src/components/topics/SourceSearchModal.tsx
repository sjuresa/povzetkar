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
      <div className="bg-white dark:bg-[#1a1c1e] w-full max-w-lg rounded-3xl shadow-2xl border border-gray-200 dark:border-gray-800 overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-6 border-b border-gray-100 dark:border-gray-800 flex items-center justify-between bg-gray-50/50 dark:bg-gray-800/10">
          <div>
            <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100 italic tracking-tight">{t('topics.form.search_modal.title')}</h2>
            <p className="text-xs text-gray-500 font-medium">{t('topics.form.search_modal.subtitle')}</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 hover:bg-gray-200 dark:hover:bg-gray-800 rounded-xl transition-colors"
          >
            <X className="w-5 h-5 text-gray-500" />
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
                <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 group-focus-within:text-blue-500 transition-colors" />
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder={t('topics.form.search_modal.search_placeholder')}
                  className="w-full pl-11 pr-4 py-3 bg-gray-100 dark:bg-gray-900 border-none rounded-2xl focus:ring-2 focus:ring-blue-500/20 text-sm dark:text-gray-100 transition-all"
                  autoFocus
                />
              </div>

              {/* Results */}
              <div className="space-y-2">
                {isLoading ? (
                  <div className="flex flex-col items-center justify-center py-12 text-gray-400">
                    <Loader2 className="w-8 h-8 animate-spin mb-3 text-blue-500" />
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
                        className="w-full group flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-900/40 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-2xl border border-transparent hover:border-blue-200 dark:hover:border-blue-800 transition-all text-left"
                      >
                        <div className="flex items-center min-w-0">
                          <div className="w-10 h-10 rounded-xl bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 flex items-center justify-center mr-3 shrink-0 shadow-sm group-hover:scale-110 transition-transform">
                            <span className="text-[10px] font-black italic text-blue-600 dark:text-blue-400">
                              {source.platform?.[0]}
                            </span>
                          </div>
                          <div className="truncate">
                            <h4 className="text-sm font-bold text-gray-900 dark:text-gray-100 truncate">
                              {source.name || source.sourceIdentifier}
                            </h4>
                            <p className="text-[10px] text-gray-500 font-medium truncate uppercase tracking-tight">
                              {source.platform} • {source.sourceIdentifier}
                            </p>
                          </div>
                        </div>
                        <ChevronRight className="w-4 h-4 text-gray-300 group-hover:translate-x-1 group-hover:text-blue-500 transition-all" />
                      </button>
                    ))}

                    {/* Pagination */}
                    {totalPages > 1 && (
                      <div className="flex items-center justify-center gap-4 pt-4">
                        <button
                          disabled={page === 0}
                          onClick={() => setPage(p => p - 1)}
                          className="p-1.5 disabled:opacity-30 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                        >
                          <ChevronLeft className="w-4 h-4" />
                        </button>
                        <span className="text-xs font-bold text-gray-500">
                          {t('topics.form.search_modal.page_info', { current: page + 1, total: totalPages })}
                        </span>
                        <button
                          disabled={page >= totalPages - 1}
                          onClick={() => setPage(p => p + 1)}
                          className="p-1.5 disabled:opacity-30 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                        >
                          <ChevronRight className="w-4 h-4" />
                        </button>
                      </div>
                    )}
                  </>
                ) : (
                  <div className="text-center py-12 bg-gray-50 dark:bg-gray-900/20 rounded-3xl border border-dashed border-gray-200 dark:border-gray-800">
                    <AlertCircle className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                    <p className="text-sm font-bold text-gray-500 italic">{t('topics.form.search_modal.no_results')}</p>
                    <p className="text-[10px] text-gray-400 font-medium mt-1">{t('topics.form.search_modal.no_results_tip')}</p>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        {!showQuickAdd && (
          <div className="p-6 border-t border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/10">
            <button
              onClick={() => setShowQuickAdd(true)}
              className="w-full flex items-center justify-center gap-2 py-4 bg-white dark:bg-gray-800/50 hover:bg-blue-50 dark:hover:bg-blue-900/20 border border-gray-200 dark:border-gray-800 rounded-2xl text-sm font-bold text-blue-600 dark:text-blue-400 group transition-all"
            >
              <Plus className="w-4 h-4 group-hover:rotate-90 transition-transform" />
              {t('topics.form.search_modal.quick_add_trigger')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
