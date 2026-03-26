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
      onSuccess: (updatedItem: any) => {
        toast.success(t('common.success'));
        
        // Optimistically update the cache for any query containing this item
        // This is much faster than invalidating and refetching
        queryClient.setQueriesData({ queryKey: getGetApiV1ContentItemsQueryKey() }, (oldData: any) => {
          if (!oldData?.data?.items) return oldData;
          return {
            ...oldData,
            data: {
              ...oldData.data,
              items: oldData.data.items.map((item: any) => 
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
      onError: (err: any) => {
        const message = err.response?.data?.message || t('common.error');
        toast.error(message);
      }
    });
  };

  const getStatusColor = (status?: ProcessingStatus) => {
    if (!status) return 'bg-slate-500/10 text-slate-500 border-slate-500/20';
    switch (status) {
      case 'COMPLETED': return 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20';
      case 'FAILED': return 'bg-red-500/10 text-red-500 border-red-500/20';
      case 'PENDING': return 'bg-blue-500/10 text-blue-500 border-blue-500/20';
      case 'RETRY_LATER': return 'bg-amber-500/10 text-amber-500 border-amber-500/20';
      case 'SKIPPED_TOO_SHORT': return 'bg-slate-500/10 text-slate-400 border-slate-500/20';
      case 'IGNORED': return 'bg-slate-500/5 text-slate-500 border-slate-500/10';
      default: return 'bg-slate-500/10 text-slate-500 border-slate-500/20';
    }
  };

  return (
    <div className="glass border border-[var(--color-border-subtle)] rounded-3xl overflow-hidden shadow-2xl animate-in fade-in slide-in-from-bottom-2 duration-1000 w-full">
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
                      className="font-bold text-[var(--color-text-primary)] line-clamp-2 leading-tight transition-all hover:text-blue-500 group-hover:text-blue-400 hover:underline decoration-blue-500/30 underline-offset-4"
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
                  <span className="text-xs font-semibold px-2 py-1 rounded-lg bg-blue-500/5 text-blue-400 border border-blue-500/10">
                    {item.topicName || 'General'}
                  </span>
                </td>
                <td className="px-6 py-4">
                   <div className="flex items-center space-x-2">
                       <div className="text-blue-500/80">
                          {item.contentSource?.platform === 'YOUTUBE' ? <Youtube className="w-4 h-4" /> : <Globe className="w-4 h-4" />}
                       </div>
                       <span className="text-xs font-medium text-[var(--color-text-secondary)] truncate max-w-[120px]">{item.contentSource?.name}</span>
                   </div>
                </td>
                <td className="px-6 py-4 text-center">
                   {item.hasTranscript ? (
                     <Check className="w-4 h-4 text-emerald-500 mx-auto" />
                   ) : (
                     <X className="w-4 h-4 text-red-500/30 mx-auto" />
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
                        className="bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] text-[var(--color-text-primary)] text-xs rounded-lg px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-500/50 cursor-pointer disabled:opacity-50"
                      >
                        {Object.values(ProcessingStatus).map(status => (
                          <option key={status} value={status}>{status}</option>
                        ))}
                      </select>

                      <div className="flex items-center space-x-2">
                        {item.hasSummary ? (
                          <button 
                            onClick={() => onViewSummary(item.id!)}
                            className="p-1.5 rounded-lg bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 transition-all border border-blue-500/20"
                            title={t('contentManagement.table.viewSummary')}
                          >
                             <FileText className="w-4 h-4" />
                          </button>
                        ) : (
                          <button 
                            onClick={() => handleGenerateSummary(item.id!)}
                            disabled={isGenerating || item.processingStatus === 'PENDING'}
                            className="p-1.5 rounded-lg bg-amber-500/10 text-amber-500 hover:bg-amber-500/20 transition-all border border-amber-500/20 disabled:opacity-30"
                            title={t('contentManagement.table.generateSummary')}
                          >
                             <Sparkles className={`w-4 h-4 ${isGenerating ? 'animate-pulse' : ''}`} />
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
  );
}
