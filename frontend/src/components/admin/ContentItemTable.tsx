import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import type { ContentItem } from '../../api/generated/model/contentItem';
import { ProcessingStatus } from '../../api/generated/model/processingStatus';
import { 
  usePostApiV1ContentItemsIdCommandsStatus, 
  usePostApiV1ContentItemsIdCommandsGenerateSummary 
} from '../../api/generated/content-items/content-items';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { getGetApiV1ContentItemsQueryKey } from '../../api/generated/content-items/content-items';
import { Check, X, FileText, Sparkles, Youtube, Globe } from 'lucide-react';

interface ContentItemTableProps {
  items: ContentItem[];
  onViewSummary: (id: number) => void;
}

export function ContentItemTable({ items, onViewSummary }: ContentItemTableProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { mutate: updateStatus, isPending } = usePostApiV1ContentItemsIdCommandsStatus();
  const { mutate: generateSummary, isPending: isGenerating } = usePostApiV1ContentItemsIdCommandsGenerateSummary();

  const handleStatusChange = (id: number, newStatus: ProcessingStatus) => {
    if (!newStatus) return;
    updateStatus({ id, data: { status: newStatus } }, {
      onSuccess: (updatedEntry) => {
        toast.success(t('common.success'));
        
        const updatedItem = updatedEntry as { data: Partial<ContentItem> };
        
        queryClient.setQueriesData({ queryKey: getGetApiV1ContentItemsQueryKey() }, (oldData: unknown) => {
          const data = oldData as { data?: { items?: ContentItem[] } } | undefined;
          if (!data?.data?.items) return oldData;
          return {
            ...data,
            data: {
              ...data.data,
              items: data.data.items.map((item) => 
                item.id === id ? { ...item, ...updatedItem.data } : item
              )
            }
          };
        });
      },
      onError: () => toast.error(t('common.error')),
    });
  };

  const handleGenerateSummary = (id: number) => {
    generateSummary({ id }, {
      onSuccess: () => {
        toast.success(t('contentManagement.table.generating'));
        void queryClient.invalidateQueries({ queryKey: getGetApiV1ContentItemsQueryKey() });
      },
      onError: (err: unknown) => {
        const axiosError = err as { response?: { data?: { message?: string } } };
        const message = axiosError.response?.data?.message || t('common.error');
        toast.error(message);
      }
    });
  };

  const getStatusColor = (status?: ProcessingStatus) => {
    if (!status) return 'bg-[var(--color-bg-secondary)]0/10 text-[var(--color-text-secondary)] border-[var(--color-border-subtle)]';
    switch (status) {
      case 'COMPLETED': return 'bg-[var(--color-status-success)]/10 text-[var(--color-status-success)] border-[var(--color-status-success)]/20';
      case 'FAILED': return 'bg-[var(--color-status-error)]/10 text-[var(--color-status-error)] border-[var(--color-status-error)]/20';
      case 'PENDING': return 'bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)] border-[var(--color-action-primary)]/20';
      case 'RETRY_LATER': return 'bg-[var(--color-status-warning)]/10 text-[var(--color-status-warning)] border-[var(--color-status-warning)]/20';
      case 'SKIPPED_TOO_SHORT': return 'bg-[var(--color-bg-secondary)]0/10 text-[var(--color-text-secondary)] border-[var(--color-border-subtle)]';
      case 'IGNORED': return 'bg-[var(--color-bg-secondary)]/5 text-[var(--color-text-secondary)] border-[var(--color-border-subtle)]/50';
      default: return 'bg-[var(--color-bg-secondary)]/10 text-[var(--color-text-secondary)] border-[var(--color-border-subtle)]';
    }
  };

  return (
    <div className="w-full space-y-4">
      {/* Mobile Card View (shown only on small screens) */}
      <div className="grid grid-cols-1 gap-4 lg:hidden">
        {items.map((item) => (
          <div key={item.id} className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-xl p-5 shadow-sm space-y-4">
            <div className="flex justify-between items-start gap-3">
               <div className="space-y-1 flex-1">
                  <a 
                    href={item.sourceUrl} 
                    target="_blank" 
                    rel="noopener noreferrer"
                    className="font-bold text-[var(--color-text-primary)] leading-tight block hover:text-[var(--color-action-primary)] transition-colors"
                  >
                    {item.title}
                  </a>
                  <div className="flex items-center space-x-2 text-[10px] text-[var(--color-text-secondary)] font-bold uppercase tracking-tight">
                     <span>{item.publishedAt ? format(new Date(item.publishedAt), 'MMM dd, HH:mm') : 'Unknown'}</span>
                     <span>•</span>
                     <span>ID: {item.id}</span>
                  </div>
               </div>
               <div className="shrink-0 pt-1">
                 {item.contentSource?.platform === 'YOUTUBE' ? <Youtube className="w-5 h-5 text-[var(--color-action-primary)]" /> : <Globe className="w-5 h-5 text-[var(--color-action-primary)]" />}
               </div>
            </div>

            <div className="flex flex-wrap items-center gap-2 pt-2">
               <span className="text-[10px] font-black uppercase px-2 py-1 rounded-lg bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)]">
                 {item.topicName || 'General'}
               </span>
               <span className={`px-2 py-1 rounded-full text-[10px] font-black uppercase border ${getStatusColor(item.processingStatus)}`}>
                 {item.processingStatus}
               </span>
            </div>

            <div className="pt-4 border-t border-[var(--color-border-subtle)] flex items-center justify-between">
               <select 
                  value={item.processingStatus}
                  disabled={isPending}
                  onChange={(e) => handleStatusChange(item.id!, e.target.value as ProcessingStatus)}
                  className="bg-[var(--color-background-base)] border border-[var(--color-border-subtle)] text-[var(--color-text-primary)] text-xs rounded-lg px-2 py-1.5 focus:outline-none"
                >
                  {Object.values(ProcessingStatus).map(status => (
                    <option key={status} value={status}>{status}</option>
                  ))}
                </select>

                <div className="flex items-center space-x-2">
                  {item.hasSummary ? (
                    <button 
                      onClick={() => onViewSummary(item.id!)}
                      className="p-2 rounded-lg bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)]"
                    >
                       <FileText className="w-5 h-5" strokeWidth={1.5}/>
                    </button>
                  ) : (
                    <button 
                      onClick={() => handleGenerateSummary(item.id!)}
                      disabled={isGenerating || item.processingStatus === 'PENDING'}
                      className="p-2 rounded-lg bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] shadow-lg shadow-[var(--color-action-primary)]/10 disabled:opacity-30"
                    >
                       <Sparkles className={`w-5 h-5 ${isGenerating ? 'animate-pulse' : ''}`} strokeWidth={1.5}/>
                    </button>
                  )}
                </div>
            </div>
          </div>
        ))}
      </div>

      {/* Desktop Table View (hidden on mobile) */}
      <div className="hidden lg:block glass border border-[var(--color-border-subtle)] rounded-[28px] overflow-hidden shadow-2xl animate-in fade-in slide-in-from-bottom-2 duration-1000 w-full">
        <div className="overflow-x-auto w-full scrollbar-elegant">
          <table className="w-full text-left border-collapse min-w-[1000px]">
            <thead>
              <tr className="bg-[var(--color-background-alt)]/50 border-b border-[var(--color-border-subtle)]">
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] w-16">ID</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] min-w-[300px]">{t('contentManagement.table.title')}</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] w-32">{t('contentManagement.table.topic')}</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] w-48">{t('contentManagement.table.source')}</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] w-24 text-center">{t('contentManagement.table.transcript')}</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] w-32">{t('contentManagement.table.status')}</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] tracking-wider min-w-[200px]">{t('contentManagement.table.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--color-border-subtle)]">
              {items.map((item) => (
                <tr key={item.id} className="hover:bg-[var(--color-surface-deep)]/30 transition-colors group">
                  <td className="px-6 py-4 font-mono text-xs text-[var(--color-text-secondary)]">{item.id}</td>
                  <td className="px-6 py-4 max-w-xs xl:max-w-md">
                    <div className="space-y-1">
                      <a 
                        href={item.sourceUrl} 
                        target="_blank" 
                        rel="noopener noreferrer"
                        className="font-bold text-[var(--color-text-primary)] line-clamp-2 leading-tight transition-all hover:text-[var(--color-action-primary)] hover:underline decoration-[var(--color-action-primary)]/30 underline-offset-4"
                        title={item.title}
                      >
                        {item.title}
                      </a>
                      <div className="flex items-center space-x-2 text-[10px] text-[var(--color-text-secondary)]">
                         <span>{item.publishedAt ? format(new Date(item.publishedAt), 'MMM dd, HH:mm') : 'Unknown'}</span>
                         {item.durationSeconds && (
                           <>
                             <span>•</span>
                             <span>{Math.floor(item.durationSeconds / 60)}m</span>
                           </>
                         )}
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span className="text-xs font-semibold px-2 py-1 rounded-xl bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)] border-[var(--color-action-primary)]/10">
                      {item.topicName || 'General'}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                     <div className="flex items-center space-x-2">
                         <div className="text-[var(--color-action-primary)]/80">
                            {item.contentSource?.platform === 'YOUTUBE' ? <Youtube className="w-4 h-4"  strokeWidth={1.5}/> : <Globe className="w-4 h-4"  strokeWidth={1.5}/>}
                         </div>
                         <span className="text-xs font-medium text-[var(--color-text-secondary)] truncate max-w-[120px]">{item.contentSource?.name}</span>
                     </div>
                  </td>
                  <td className="px-6 py-4 text-center">
                     {item.hasTranscript ? (
                       <Check className="w-4 h-4 text-[var(--color-status-success)] mx-auto"  strokeWidth={1.5}/>
                     ) : (
                       <X className="w-4 h-4 text-[var(--color-status-error)]/30 mx-auto"  strokeWidth={1.5}/>
                     )}
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-2 py-1 rounded-full text-[10px] font-bold border ${getStatusColor(item.processingStatus)}`}>
                      {item.processingStatus || 'UNKNOWN'}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                     <div className="flex items-center space-x-3">
                        <select 
                          value={item.processingStatus}
                          disabled={isPending}
                          onChange={(e) => handleStatusChange(item.id!, e.target.value as ProcessingStatus)}
                          className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] text-[var(--color-text-primary)] text-xs rounded-xl px-2 py-1 focus:outline-none focus:ring-1 focus:ring-[var(--color-ring-focus)]/50 cursor-pointer disabled:opacity-50"
                        >
                          {Object.values(ProcessingStatus).map(status => (
                            <option key={status} value={status}>{status}</option>
                          ))}
                        </select>

                        <div className="flex items-center space-x-2">
                          {item.hasSummary ? (
                            <button 
                              onClick={() => onViewSummary(item.id!)}
                              className="p-1.5 rounded-xl bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)] hover:bg-[var(--color-action-primary)]/10 transition-all border border-[var(--color-action-primary)]/20"
                              title={t('contentManagement.table.viewSummary')}
                            >
                               <FileText className="w-4 h-4"  strokeWidth={1.5}/>
                            </button>
                          ) : (
                            <button 
                              onClick={() => handleGenerateSummary(item.id!)}
                              disabled={isGenerating || item.processingStatus === 'PENDING'}
                              className="p-1.5 rounded-xl bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] hover:opacity-90 transition-all border border-[var(--color-action-primary)]/20 shadow-lg shadow-[var(--color-action-primary)]/10 disabled:opacity-30"
                              title={t('contentManagement.table.generateSummary')}
                            >
                               <Sparkles className={`w-4 h-4 ${isGenerating ? 'animate-pulse' : ''}`}  strokeWidth={1.5}/>
                            </button>
                          )}
                        </div>
                     </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
