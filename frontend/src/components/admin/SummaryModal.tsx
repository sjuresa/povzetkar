import { useEffect } from 'react';
import { useGetApiV1ContentItemsIdSummary } from '../../api/generated/content-items/content-items';
import ReactMarkdown from 'react-markdown';
import { format } from 'date-fns';
import { Skeleton } from '../ui/Skeleton';
import {
  X,
  Globe,
  FileText,
  AlertCircle,
  Calendar,
  Clock,
  User as UserIcon
} from 'lucide-react';
import { YoutubeIcon } from '../ui/YoutubeIcon';

import { useTranslation } from 'react-i18next';
import { ExportMenu } from '../briefing/ExportMenu';

interface SummaryModalProps {
  readonly itemId: number | null;
  readonly onClose: () => void;
}

export function SummaryModal({ itemId, onClose }: SummaryModalProps) {
  const { t } = useTranslation();
  const { data: response, isLoading: isSummaryLoading, error: summaryError } = useGetApiV1ContentItemsIdSummary(
    itemId!,
    { query: { enabled: !!itemId } }
  );

  useEffect(() => {
    if (!itemId) return;

    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };

    document.body.style.overflow = 'hidden';
    globalThis.addEventListener('keydown', handleEsc);

    return () => {
      document.body.style.overflow = 'unset';
      globalThis.removeEventListener('keydown', handleEsc);
    };
  }, [itemId, onClose]);

  if (!itemId) return null;

  const summary = response?.data;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 md:p-8 animate-in fade-in duration-300">
      <button
        type="button"
        className="absolute inset-0 bg-black/80 backdrop-blur-sm cursor-zoom-out"
        onClick={onClose}
        aria-label={t('common.close')}
      />
      
      <div className="relative w-full max-w-5xl h-full max-h-[90vh] flex flex-col glass border border-[var(--color-border-subtle)] rounded-[40px] shadow-3xl overflow-hidden animate-in zoom-in-95 slide-in-from-bottom-5 duration-500">
        
        {/* Modal Header */}
        <div className="p-6 md:p-8 border-b border-[var(--color-border-subtle)] bg-[var(--color-surface-elevated)]/40 backdrop-blur-xl flex items-center justify-between">
           <div className="flex items-center space-x-4">
              <div className="w-12 h-12 rounded-xl md:rounded-[28px] bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] flex items-center justify-center text-[var(--color-action-primary)] ring-4 ring-[var(--color-ring-focus)]/5">
                {summary?.platform === 'YOUTUBE' ? <YoutubeIcon size={24}  strokeWidth={1.5}/> : <Globe size={24}  strokeWidth={1.5}/>}
              </div>
               <div>
                <h3 className="text-xl font-bold text-[var(--color-text-primary)] leading-tight line-clamp-1">
                  {summary?.title || t('contentManagement.modal.title')}
                </h3>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1">
                   <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-text-secondary)] font-medium">
                      <UserIcon size={12} className="text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
                      <span>{summary?.authorName || t('common.loading')}</span>
                   </div>
                   <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-text-secondary)] font-medium">
                      <Calendar size={12} className="text-[var(--color-status-success)]"  strokeWidth={1.5}/>
                      <span>{summary?.publishedAt ? format(new Date(summary.publishedAt), 'MMM d, yyyy HH:mm') : t('common.loading')}</span>
                   </div>
                   {summary?.formattedDuration && (
                     <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-text-secondary)] font-medium">
                        <Clock size={12} className="text-[var(--color-status-warning)]"  strokeWidth={1.5}/>
                        <span>{summary.formattedDuration}</span>
                     </div>
                   )}
                   <div className="flex items-center text-[10px] text-[var(--color-text-secondary)] font-mono opacity-40 tracking-wider uppercase ml-1">
                      {t('contentManagement.modal.reference')}: ITEM-{itemId}
                   </div>
                </div>
              </div>
           </div>
              <div className="flex items-center gap-2">
                {summary && (
                  <ExportMenu 
                    type="summary"
                    data={{
                      id: itemId,
                      uuid: summary.uuid || null,
                      title: summary.title || t('contentManagement.modal.title'),
                      topicName: t('common.summary'),
                      date: summary.publishedAt || new Date().toISOString(),
                      metaSummary: summary.executiveSummary || '',
                      summaries: [{ summaryText: summary.summaryText || '' }]
                    }}
                    variant="ghost"
                    align="right"
                  />
                )}
                <button 
                  onClick={onClose}
                  className="p-3 rounded-xl md:rounded-[28px] hover:bg-white/5 text-[var(--color-text-secondary)] hover:text-white transition-all active:scale-90"
                >
                  <X size={24}  strokeWidth={1.5}/>
                </button>
              </div>
           </div>

        {/* Modal Content */}
        <div className="flex-1 overflow-y-auto bg-[var(--color-background-base)] p-6 md:p-12 scroll-smooth">
          {isSummaryLoading && (
            <div className="p-10 space-y-10 animate-pulse">
              <div className="space-y-4">
                <Skeleton className="h-10 w-3/4" />
                <Skeleton className="h-6 w-1/2" />
              </div>
              <Skeleton className="h-48 w-full rounded-xl md:rounded-[28px]" />
            </div>
          )}
          {!isSummaryLoading && summaryError && (
            <div className="flex flex-col items-center justify-center h-full py-20 text-center space-y-6">
               <div className="w-20 h-20 rounded-full bg-[var(--color-status-error)]/10 flex items-center justify-center text-[var(--color-status-error)] ring-8 ring-[var(--color-status-error)]/5">
                  <AlertCircle size={40}  strokeWidth={1.5}/>
               </div>
               <div className="space-y-2">
                 <p className="text-2xl font-bold text-[var(--color-text-primary)]">{t('contentManagement.modal.notAvailable')}</p>
                 <p className="text-[var(--color-text-secondary)] max-w-md mx-auto">
                    {t('contentManagement.modal.notAvailableSub')}
                 </p>
               </div>
               <button onClick={onClose} className="px-8 py-3 rounded-xl md:rounded-[28px] bg-[var(--color-action-primary)] text-white font-bold">{t('common.cancel')}</button>
            </div>
          )}
          {!isSummaryLoading && !summaryError && summary && (
            <article className="w-full">
              {summary.executiveSummary && (
                <div className="mb-12 p-10 md:p-14 rounded-xl md:rounded-[28px] bg-[var(--color-action-primary)]/[0.03] border-l-8 border-[var(--color-action-primary)]/50 shadow-2xl shadow-[var(--color-action-primary)]/5 animate-in fade-in slide-in-from-left-4 duration-1000">
                  <div className={`prose prose-zinc max-w-none text-2xl italic font-serif font-medium text-[var(--color-text-primary)] leading-[1.6] opacity-100 selection:bg-[var(--color-action-primary)]/30 ${/^\p{L}/u.test(summary.executiveSummary.trim()) ? 'first-letter:text-5xl first-letter:font-black first-letter:mr-3 first-letter:float-left first-letter:text-[var(--color-action-primary)]' : ''}`}>
                    <p>{summary.executiveSummary}</p>
                  </div>
                </div>
              )}
              <section className="prose prose-zinc max-w-none animate-in fade-in slide-in-from-bottom-6 duration-700 text-[var(--color-text-primary)] leading-relaxed">
                <ReactMarkdown>{summary.summaryText || ""}</ReactMarkdown>
              </section>
            </article>
          )}
          {!isSummaryLoading && !summaryError && !summary && (
            <div className="flex flex-col items-center justify-center h-full py-32 text-[var(--color-text-secondary)] space-y-4">
              <FileText size={48} className="opacity-20"  strokeWidth={1.5}/>
              <p className="italic text-lg">No analysis found for this item.</p>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-6 md:px-10 border-t border-[var(--color-border-subtle)] bg-[var(--color-surface-deep)]/40 backdrop-blur-xl flex flex-col md:flex-row justify-between items-center gap-6">
            <div className="w-full md:w-auto" />
           <button 
             onClick={onClose}
             className="w-full md:w-auto px-8 py-3 rounded-xl md:rounded-[28px] bg-[var(--color-surface-hover)] hover:bg-[var(--color-border-subtle)] text-[var(--color-text-primary)] font-bold transition-all active:scale-95 border border-[var(--color-border-subtle)]"
           >
             {t('contentManagement.modal.close')}
           </button>
        </div>
      </div>
    </div>
  );
}
