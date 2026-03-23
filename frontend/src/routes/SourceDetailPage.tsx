import { useState, useEffect } from 'react';
import { useParams, Link } from '@tanstack/react-router';
import { useGetApiV1SourcesId } from '../api/generated/content-sources/content-sources';
import { useGetApiV1SourcesIdItems, useGetApiV1ContentItemsIdSummary } from '../api/generated/content-items/content-items';
import ReactMarkdown from 'react-markdown';
import type { ContentItem } from '../api/generated/model';
import { Pagination } from '../components/ui/Pagination';
import { Skeleton } from '../components/ui/Skeleton';
import { 
  ChevronLeft, 
  ExternalLink, 
  Youtube, 
  Globe, 
  Clock, 
  FileText, 
  CheckCircle2, 
  AlertCircle, 
  RefreshCw,
  X
} from 'lucide-react';
import { cn } from '../util/cn';
import { format } from 'date-fns';

/**
 * Helper to format duration in seconds to MM:SS or HH:MM:SS
 */
const formatDuration = (seconds?: number) => {
  if (!seconds || seconds <= 0) return '-';
  const hrs = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }
  return `${mins}:${secs.toString().padStart(2, '0')}`;
};

/**
 * SourceDetailPage displays detailed information about a single content source
 * and a paginated list of its items.
 * Part of POV-148 implementation.
 */
export function SourceDetailPage() {
  const { sourceId } = useParams({ strict: false });
  const id = typeof sourceId === 'string' ? parseInt(sourceId, 10) : 0;
  
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [summaryItemId, setSummaryItemId] = useState<number | null>(null);

  const { data: sourceResponse, isLoading: isSourceLoading } = useGetApiV1SourcesId(id);
  const { data: itemsResponse, isLoading: isItemsLoading } = useGetApiV1SourcesIdItems(id, { page, size });

  const source = sourceResponse?.data;
  const items = (itemsResponse?.data?.items || []) as ContentItem[];
  const totalCount = itemsResponse?.data?.totalCount || 0;
  const totalPages = Math.ceil(totalCount / size);

  if (isSourceLoading) {
    return (
      <div className="space-y-8 animate-pulse">
        <div className="flex items-center space-x-4">
           <Skeleton className="h-16 w-16 rounded-2xl" />
           <div className="space-y-2">
              <Skeleton className="h-8 w-48" />
              <Skeleton className="h-4 w-32" />
           </div>
        </div>
        <Skeleton className="h-40 w-full rounded-3xl" />
        <div className="space-y-4">
           <Skeleton className="h-8 w-64" />
           <Skeleton className="h-96 w-full rounded-3xl" />
        </div>
      </div>
    );
  }

  if (!source) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-4">
        <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center text-red-500">
          <AlertCircle size={32} />
        </div>
        <h2 className="text-2xl font-bold text-[var(--color-text-primary)]">Source not found</h2>
        <Link to="/sources" className="text-indigo-500 hover:underline flex items-center">
          <ChevronLeft size={16} className="mr-1" /> Back to Library
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700 pb-12">
      {/* Header & Back Link */}
      <div className="flex flex-col space-y-4">
        <Link 
          to="/sources" 
          className="group flex items-center text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] hover:text-indigo-500 transition-colors w-fit"
        >
          <ChevronLeft size={14} className="mr-1 group-hover:-translate-x-1 transition-transform" />
          Back to Library
        </Link>
        
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="flex items-center space-x-4">
            <div className={cn(
              "w-16 h-16 rounded-2xl flex items-center justify-center shadow-xl",
              source.platform === 'YOUTUBE' ? "bg-red-500/10 text-red-500" : "bg-blue-500/10 text-blue-500"
            )}>
              {source.platform === 'YOUTUBE' ? <Youtube size={32} /> : <Globe size={32} />}
            </div>
            <div>
              <h2 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] text-glow">
                {source.name}
              </h2>
              <div className="flex items-center space-x-3 mt-1">
                <span className="text-xs font-mono text-[var(--color-text-secondary)] opacity-70">
                  {source.sourceIdentifier}
                </span>
                <span className="w-1 h-1 rounded-full bg-[var(--color-border-subtle)]" />
                <a 
                  href={source.sourceUrl} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  className="text-xs text-indigo-500 hover:underline flex items-center"
                >
                  Visit Source <ExternalLink size={12} className="ml-1" />
                </a>
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <div className="glass border border-[var(--color-border-subtle)] rounded-2xl px-6 py-3 text-center">
              <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-1">Items Cached</div>
              <div className="text-2xl font-mono font-extrabold text-[var(--color-text-primary)]">{totalCount.toLocaleString()}</div>
            </div>
          </div>
        </div>
      </div>

      {/* Source Info Card */}
      <div className="glass border border-[var(--color-border-subtle)] rounded-3xl p-8 relative overflow-hidden bg-gradient-to-br from-[var(--color-surface-elevated)] to-transparent">
        <div className="max-w-3xl">
          <h3 className="text-lg font-bold text-[var(--color-text-primary)] mb-4 flex items-center">
            <FileText size={18} className="mr-2 text-indigo-500" />
            Description
          </h3>
          <p className="text-[var(--color-text-secondary)] leading-relaxed italic">
            {source.description || 'No detailed description available for this source.'}
          </p>
          
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 mt-10">
            <div>
              <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-2">Platform</div>
              <div className="flex items-center space-x-2">
                <div className={cn(
                  "px-2 py-1 rounded text-[10px] font-bold",
                  source.platform === 'YOUTUBE' ? "bg-red-500/10 text-red-500" : "bg-blue-500/10 text-blue-500"
                )}>
                  {source.platform}
                </div>
              </div>
            </div>
            <div>
              <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-2">Followers</div>
              <div className="text-sm font-bold text-[var(--color-text-primary)]">
                {source.subscriberCount?.toLocaleString() || '0'}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Items Section */}
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h3 className="text-2xl font-bold text-[var(--color-text-primary)] tracking-tight">Recent Content Items</h3>
        </div>

        <div className="glass border border-[var(--color-border-subtle)] rounded-3xl overflow-hidden shadow-2xl shadow-black/20">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-[var(--color-surface-deep)]/50 border-b border-[var(--color-border-subtle)]">
                  <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Item / Title</th>
                  <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Status</th>
                  <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Duration</th>
                  <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Published</th>
                  <th className="px-6 py-4 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border-subtle)]/30">
                {isItemsLoading && items.length === 0 ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <tr key={i}>
                      <td colSpan={5} className="px-6 py-4"><Skeleton className="h-12 w-full" /></td>
                    </tr>
                  ))
                ) : items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-6 py-12 text-center text-[var(--color-text-secondary)] italic">
                      No items found for this source yet.
                    </td>
                  </tr>
                ) : (
                  items.map((item) => (
                    <tr key={item.id} className="hover:bg-[var(--color-surface-hover)] transition-all group">
                      <td className="px-6 py-4">
                        <div className="flex flex-col">
                          <span className="text-sm font-bold text-[var(--color-text-primary)] group-hover:text-indigo-400 transition-colors line-clamp-1">
                            {item.title || 'Untitled Item'}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center">
                          {item.processingStatus === 'COMPLETED' ? (
                            <div className="flex items-center text-xs font-bold text-emerald-500 bg-emerald-500/10 px-2 py-1 rounded-lg">
                              <CheckCircle2 size={12} className="mr-1.5" />
                              Processed
                            </div>
                          ) : item.processingStatus === 'FAILED' ? (
                            <div className="flex items-center text-xs font-bold text-red-500 bg-red-500/10 px-2 py-1 rounded-lg">
                              <AlertCircle size={12} className="mr-1.5" />
                              Failed
                            </div>
                          ) : (
                            <div className="flex items-center text-xs font-bold text-amber-500 bg-amber-500/10 px-2 py-1 rounded-lg">
                              <RefreshCw size={12} className="mr-1.5 animate-spin" />
                              {item.processingStatus}
                            </div>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center text-xs text-[var(--color-text-secondary)] font-mono">
                          {formatDuration(item.durationSeconds)}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center text-xs text-[var(--color-text-secondary)] font-mono">
                          <Clock size={12} className="mr-1.5" />
                          {item.publishedAt ? format(new Date(item.publishedAt), 'MMM d, HH:mm') : 'Unknown'}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end space-x-2">
                          {item.processingStatus === 'COMPLETED' && (
                            <button 
                              onClick={() => setSummaryItemId(item.id as number)}
                              className="inline-flex items-center p-2 text-indigo-500 hover:text-indigo-400 transition-colors"
                              title="View Summary"
                            >
                              <FileText size={16} />
                            </button>
                          )}
                          <a 
                            href={item.sourceUrl} 
                            target="_blank" 
                            rel="noopener noreferrer"
                            className="inline-flex items-center p-2 text-[var(--color-text-secondary)] hover:text-indigo-500 transition-colors"
                            title="View Original"
                          >
                            <ExternalLink size={16} />
                          </a>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        <Pagination 
          page={page} 
          totalPages={totalPages} 
          onPageChange={setPage} 
          isLoading={isItemsLoading} 
        />
      </div>

      {/* Summary Modal */}
      {summaryItemId && (
        <SummaryModal 
          itemId={summaryItemId} 
          onClose={() => setSummaryItemId(null)} 
        />
      )}
    </div>
  );
}

/**
 * Centered glassmorphic modal for viewing ContentItem summaries.
 * Features Executive Summary highlight and ReactMarkdown rendering.
 */
function SummaryModal({ itemId, onClose }: { itemId: number, onClose: () => void }) {
  const { data: response, isLoading, error } = useGetApiV1ContentItemsIdSummary(itemId);
  const summary = response?.data;

  // Prevent background scrolling while modal is open
  useEffect(() => {
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = 'unset'; };
  }, []);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="absolute inset-0 bg-black/60 backdrop-blur-sm animate-in fade-in duration-300" 
        onClick={onClose} 
      />
      
      {/* Modal Container */}
      <div className="glass relative w-full max-w-4xl max-h-[95vh] overflow-hidden flex flex-col rounded-[2.5rem] border border-[var(--color-border-subtle)] shadow-4xl animate-in zoom-in-95 fade-in duration-300 bg-[var(--color-surface-elevated)]">
        
        {/* Modal Header */}
        <div className="flex items-center justify-between p-6 md:px-10 border-b border-[var(--color-border-subtle)] bg-[var(--color-surface-deep)]/40 backdrop-blur-xl">
          <div className="flex items-center space-x-4">
             <div className={cn(
               "w-12 h-12 rounded-2xl flex items-center justify-center border shadow-inner",
               summary?.platform === 'YOUTUBE' 
                 ? "bg-red-500/10 text-red-500 border-red-500/20" 
                 : "bg-blue-500/10 text-blue-500 border-blue-500/20"
             )}>
               {summary?.platform === 'YOUTUBE' ? <Youtube size={24} /> : <Globe size={24} />}
             </div>
             <div>
               <h3 className="text-xl font-bold text-[var(--color-text-primary)]">AI Content Summary</h3>
               {summary ? (
                 <div className="flex items-center text-[10px] text-[var(--color-text-secondary)] font-mono opacity-60 mt-0.5 tracking-wider">
                   {summary?.platform === 'YOUTUBE' ? (
                     <Youtube size={10} className="mr-1 text-red-500" />
                   ) : (
                     <Globe size={10} className="mr-1 text-blue-500" />
                   )}
                   <span>REFERENCE: ITEM-{itemId}</span>
                 </div>
               ) : null}
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
        <div className="flex-1 overflow-y-auto scrollbar-elegant bg-gradient-to-b from-white to-gray-50 dark:from-[#0a0a0a] dark:to-[#080808] p-6 md:p-8">
          {isLoading ? (
            <div className="p-10 space-y-10 animate-pulse">
              <div className="space-y-4">
                <Skeleton className="h-10 w-3/4" />
                <Skeleton className="h-6 w-1/2" />
              </div>
              <Skeleton className="h-48 w-full rounded-3xl" />
            </div>
          ) : error ? (
            <div className="flex flex-col items-center justify-center py-32 text-center space-y-6">
               <div className="w-20 h-20 rounded-full bg-red-500/10 flex items-center justify-center text-red-500">
                  <AlertCircle size={40} />
               </div>
               <p className="text-2xl font-bold text-gray-900 dark:text-white">Request Interrupted</p>
               <button onClick={onClose} className="px-8 py-3 rounded-2xl bg-indigo-500 text-white font-bold">Go Back</button>
            </div>
          ) : summary ? (
            <article className="w-full">
              <section className="prose prose-slate dark:prose-invert max-w-none animate-in fade-in slide-in-from-bottom-6 duration-700">
                <ReactMarkdown>{summary.summaryText || ""}</ReactMarkdown>
              </section>
            </article>
          ) : (
            <div className="text-center py-32 text-gray-400 italic">
              No analysis found for this item.
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-6 md:px-10 border-t border-[var(--color-border-subtle)] bg-[var(--color-surface-deep)]/40 backdrop-blur-xl flex justify-end">
           <button 
             onClick={onClose}
             className="px-8 py-2.5 rounded-xl bg-[var(--color-surface-hover)] hover:bg-[var(--color-border-subtle)] text-[var(--color-text-primary)] font-bold transition-all active:scale-95"
           >
             Close Reader
           </button>
        </div>
      </div>
    </div>
  );
}
