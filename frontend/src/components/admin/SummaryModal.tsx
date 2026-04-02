import { useEffect } from 'react';
import { useGetApiV1ContentItemsIdSummary } from '../../api/generated/content-items/content-items';
import ReactMarkdown from 'react-markdown';
import { format } from 'date-fns';
import { Skeleton } from '../ui/Skeleton';
import { 
  X, 
  Youtube, 
  Globe, 
  FileText, 
  AlertCircle,
  Calendar,
  Clock,
  User as UserIcon
} from 'lucide-react';

import { useTranslation } from 'react-i18next';

interface SummaryModalProps {
  itemId: number | null;
  onClose: () => void;
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
    window.addEventListener('keydown', handleEsc);

    return () => {
      document.body.style.overflow = 'unset';
      window.removeEventListener('keydown', handleEsc);
    };
  }, [itemId, onClose]);

  if (!itemId) return null;

  const summary = response?.data;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 md:p-8 animate-in fade-in duration-300">
      <div 
        className="absolute inset-0 bg-black/80 backdrop-blur-sm cursor-zoom-out" 
        onClick={onClose}
      />
      
      <div className="relative w-full max-w-5xl h-full max-h-[90vh] flex flex-col glass border border-[var(--color-border-subtle)] rounded-[40px] shadow-3xl overflow-hidden animate-in zoom-in-95 slide-in-from-bottom-5 duration-500">
        
        {/* Modal Header */}
        <div className="p-6 md:p-8 border-b border-[var(--color-border-subtle)] bg-[var(--color-surface-elevated)]/40 backdrop-blur-xl flex items-center justify-between">
           <div className="flex items-center space-x-4">
              <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 flex items-center justify-center text-indigo-500 ring-4 ring-indigo-500/5">
                {summary?.platform === 'YOUTUBE' ? <Youtube size={24} /> : <Globe size={24} />}
              </div>
               <div>
                <h3 className="text-xl font-bold text-[var(--color-text-primary)] leading-tight line-clamp-1">
                  {summary?.title || t('contentManagement.modal.title')}
                </h3>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1">
                   <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-text-secondary)] font-medium">
                      <UserIcon size={12} className="text-indigo-400" />
                      <span>{summary?.authorName || t('common.loading')}</span>
                   </div>
                   <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-text-secondary)] font-medium">
                      <Calendar size={12} className="text-emerald-400" />
                      <span>{summary?.publishedAt ? format(new Date(summary.publishedAt), 'MMM d, yyyy HH:mm') : t('common.loading')}</span>
                   </div>
                   {summary?.formattedDuration && (
                     <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-text-secondary)] font-medium">
                        <Clock size={12} className="text-amber-400" />
                        <span>{summary.formattedDuration}</span>
                     </div>
                   )}
                   <div className="flex items-center text-[10px] text-[var(--color-text-secondary)] font-mono opacity-40 tracking-wider uppercase ml-1">
                      {t('contentManagement.modal.reference')}: ITEM-{itemId}
                   </div>
                </div>
              </div>
           </div>
              <button 
                onClick={onClose}
                className="p-3 rounded-2xl hover:bg-white/5 text-[var(--color-text-secondary)] hover:text-white transition-all active:scale-90"
              >
                <X size={24} />
              </button>
           </div>

        {/* Modal Content */}
        <div className="flex-1 overflow-y-auto bg-[var(--color-background-base)] p-6 md:p-12 scroll-smooth">
          {isSummaryLoading ? (
            <div className="p-10 space-y-10 animate-pulse">
              <div className="space-y-4">
                <Skeleton className="h-10 w-3/4" />
                <Skeleton className="h-6 w-1/2" />
              </div>
              <Skeleton className="h-48 w-full rounded-3xl" />
            </div>
          ) : summaryError ? (
            <div className="flex flex-col items-center justify-center h-full py-20 text-center space-y-6">
               <div className="w-20 h-20 rounded-full bg-red-500/10 flex items-center justify-center text-red-500 ring-8 ring-red-500/5">
                  <AlertCircle size={40} />
               </div>
               <div className="space-y-2">
                 <p className="text-2xl font-bold text-[var(--color-text-primary)]">{t('contentManagement.modal.notAvailable')}</p>
                 <p className="text-[var(--color-text-secondary)] max-w-md mx-auto">
                    {t('contentManagement.modal.notAvailableSub')}
                 </p>
               </div>
               <button onClick={onClose} className="px-8 py-3 rounded-2xl bg-indigo-500 text-white font-bold">{t('common.cancel')}</button>
            </div>
          ) : summary ? (
            <article className="w-full">
              {summary.executiveSummary && (
                <div className="mb-12 p-10 md:p-14 rounded-[2.5rem] bg-indigo-500/[0.03] border-l-8 border-indigo-500/50 shadow-2xl shadow-indigo-500/5 animate-in fade-in slide-in-from-left-4 duration-1000">
                  <div className={`prose prose-indigo max-w-none text-2xl italic font-serif font-medium text-[var(--color-text-primary)] leading-[1.6] opacity-100 selection:bg-indigo-500/30 ${/^\p{L}/u.test(summary.executiveSummary.trim()) ? 'first-letter:text-5xl first-letter:font-black first-letter:mr-3 first-letter:float-left first-letter:text-indigo-400' : ''}`}>
                    <p>{summary.executiveSummary}</p>
                  </div>
                </div>
              )}
              <section className="prose prose-invert max-w-none animate-in fade-in slide-in-from-bottom-6 duration-700 text-[var(--color-text-primary)] leading-relaxed">
                <ReactMarkdown>{summary.summaryText || ""}</ReactMarkdown>
              </section>
            </article>
          ) : (
            <div className="flex flex-col items-center justify-center h-full py-32 text-[var(--color-text-secondary)] space-y-4">
              <FileText size={48} className="opacity-20" />
              <p className="italic text-lg">No analysis found for this item.</p>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-6 md:px-10 border-t border-[var(--color-border-subtle)] bg-[var(--color-surface-deep)]/40 backdrop-blur-xl flex justify-end items-center">
           <button 
             onClick={onClose}
             className="w-full md:w-auto px-8 py-3 rounded-2xl bg-[var(--color-surface-hover)] hover:bg-[var(--color-border-subtle)] text-[var(--color-text-primary)] font-bold transition-all active:scale-95 border border-[var(--color-border-subtle)]"
           >
             {t('contentManagement.modal.close')}
           </button>
        </div>
      </div>
    </div>
  );
}
