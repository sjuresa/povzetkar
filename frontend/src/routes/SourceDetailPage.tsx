import { useState } from 'react';
import { useParams, Link } from '@tanstack/react-router';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { useGetApiV1SourcesId } from '../api/generated/content-sources/content-sources';
import { 
  useGetApiV1SourcesIdItems, 
  usePostApiV1ContentItemsIdCommandsStatus,
  getGetApiV1SourcesIdItemsQueryKey
} from '../api/generated/content-items/content-items';
import { ProcessingStatus } from '../api/generated/model/processingStatus';
import type { ContentItem } from '../api/generated/model';
import { Pagination } from '../components/ui/Pagination';
import { Skeleton } from '../components/ui/Skeleton';
import {
  ChevronLeft,
  ExternalLink,
  Globe,
  Clock, 
  FileText, 
  AlertCircle
} from 'lucide-react';
import { cn } from '../util/cn';
import { format } from 'date-fns';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { SummaryModal } from '../components/admin/SummaryModal';
import { YoutubeIcon } from '../components/ui/YoutubeIcon';

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
 * Helper to get CSS classes for the status select based on status
 */
const getStatusSelectClasses = (status?: ProcessingStatus) => {
  switch (status) {
    case 'COMPLETED':
      return "text-[var(--color-status-success)] border-[var(--color-status-success)]/20 hover:bg-[var(--color-status-success)]/5";
    case 'FAILED':
      return "text-[var(--color-status-error)] border-[var(--color-status-error)]/20 hover:bg-[var(--color-status-error)]/5";
    default:
      return "text-[var(--color-status-warning)] border-[var(--color-status-warning)]/20 hover:bg-[var(--color-status-warning)]/5";
  }
};

/**
 * SourceDetailPage displays detailed information about a single content source
 * and a paginated list of its items.
 * Part of POV-148 implementation.
 */
/**
 * A source outside the user's topics answers 404 (POV-219) — an expected answer shown as "Source not found",
 * not an error for the boundary and not worth a retry. Other errors keep the global behaviour.
 */
const isNotFound = (error: unknown) => isAxiosError(error) && error.response?.status === 404;
const notFoundIsAnAnswer = {
  throwOnError: (error: unknown) => !isNotFound(error),
  retry: (failureCount: number, error: unknown) => !isNotFound(error) && failureCount < 1,
};

export function SourceDetailPage() {
  const { t } = useTranslation();
  const { sourceId } = useParams({ strict: false });
  const id = typeof sourceId === 'string' ? parseInt(sourceId, 10) : 0;
  
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [summaryItemId, setSummaryItemId] = useState<number | null>(null);

  const { data: sourceResponse, isLoading: isSourceLoading } = useGetApiV1SourcesId(id, { query: notFoundIsAnAnswer });
  const { data: itemsResponse, isLoading: isItemsLoading } = useGetApiV1SourcesIdItems(id, { page, size }, { query: notFoundIsAnAnswer });

  const source = sourceResponse?.data;
  const items = (itemsResponse?.data?.items || []) as ContentItem[];
  const totalCount = itemsResponse?.data?.totalCount || 0;
  const totalPages = Math.ceil(totalCount / size);

  const queryClient = useQueryClient();
  const { mutate: updateStatus, isPending: isUpdatingStatus } = usePostApiV1ContentItemsIdCommandsStatus();

  const handleStatusChange = (itemId: number, newStatus: ProcessingStatus) => {
    updateStatus({ id: itemId, data: { status: newStatus } }, {
      onSuccess: () => {
        toast.success('Status updated');
        void queryClient.invalidateQueries({ queryKey: getGetApiV1SourcesIdItemsQueryKey(id) });
      },
      onError: () => toast.error('Failed to update status'),
    });
  };

  if (isSourceLoading) {
    return (
      <div className="space-y-8 animate-pulse">
        <div className="flex items-center space-x-4">
           <Skeleton className="h-16 w-16 rounded-xl md:rounded-[28px]" />
           <div className="space-y-2">
              <Skeleton className="h-8 w-48" />
              <Skeleton className="h-4 w-32" />
           </div>
        </div>
        <Skeleton className="h-40 w-full rounded-xl md:rounded-[28px]" />
        <div className="space-y-4">
           <Skeleton className="h-8 w-64" />
           <Skeleton className="h-96 w-full rounded-xl md:rounded-[28px]" />
        </div>
      </div>
    );
  }

  if (!source) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-4">
        <div className="w-16 h-16 rounded-full bg-[var(--color-status-error)]/10 flex items-center justify-center text-[var(--color-status-error)]">
          <AlertCircle size={32}  strokeWidth={1.5}/>
        </div>
        <h2 className="text-2xl font-bold text-[var(--color-text-primary)]">{t('sources.detail.not_found')}</h2>
        <Link to="/sources" className="text-[var(--color-action-primary)] hover:underline flex items-center">
          <ChevronLeft size={16} className="mr-1"  strokeWidth={1.5}/> {t('sources.detail.back_to_library')}
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
          className="group flex items-center text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] hover:text-[var(--color-action-primary)] transition-colors w-fit"
        >
          <ChevronLeft size={14} className="mr-1 group-hover:-translate-x-1 transition-transform"  strokeWidth={1.5}/>
          {t('sources.detail.back_to_library')}
        </Link>
        
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="flex items-center space-x-4">
            <div className={cn(
              "w-16 h-16 rounded-xl md:rounded-[28px] flex items-center justify-center shadow-xl",
              source.platform === 'YOUTUBE' ? "bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)]" : "bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)]"
            )}>
              {source.platform === 'YOUTUBE' ? <YoutubeIcon size={32}  strokeWidth={1.5}/> : <Globe size={32}  strokeWidth={1.5}/>}
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
                  className="text-xs text-[var(--color-action-primary)] hover:underline flex items-center"
                >
                  Visit Source <ExternalLink size={12} className="ml-1"  strokeWidth={1.5}/>
                </a>
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <div className="glass border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] px-6 py-3 text-center">
              <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-1">Items Cached</div>
              <div className="text-2xl font-mono font-extrabold text-[var(--color-text-primary)]">{totalCount.toLocaleString()}</div>
            </div>
          </div>
        </div>
      </div>

      {/* Source Info Card */}
      <div className="glass border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] p-8 relative overflow-hidden bg-gradient-to-br from-[var(--color-surface-elevated)] to-transparent">
        <div className="max-w-3xl">
          <h3 className="text-lg font-bold text-[var(--color-text-primary)] mb-4 flex items-center">
            <FileText size={18} className="mr-2 text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
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
                   source.platform === 'YOUTUBE' ? "bg-[var(--color-surface-deep)] text-[var(--color-action-primary)]" : "bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-action-primary)]"
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

        <div className="glass border border-[var(--color-border-subtle)] rounded-xl md:rounded-[28px] overflow-hidden shadow-2xl shadow-black/20">
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
                          <span className="text-sm font-bold text-[var(--color-text-primary)] group-hover:text-[var(--color-action-primary)] transition-colors line-clamp-1">
                            {item.title || 'Untitled Item'}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center">
                          <select 
                            value={item.processingStatus}
                            disabled={isUpdatingStatus}
                            onChange={(e) => handleStatusChange(item.id!, e.target.value as ProcessingStatus)}
                            className={cn(
                              "text-[10px] font-bold px-2 py-1 rounded-xl border bg-transparent cursor-pointer transition-colors focus:outline-none focus:ring-2 focus:ring-[var(--color-ring-focus)]/30",
                              getStatusSelectClasses(item.processingStatus)
                            )}
                          >
                            {Object.values(ProcessingStatus).map(status => (
                              <option key={status} value={status} className="bg-[var(--color-surface-elevated)] text-[var(--color-text-primary)]">
                                {status}
                              </option>
                            ))}
                          </select>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center text-xs text-[var(--color-text-secondary)] font-mono">
                          {formatDuration(item.durationSeconds)}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center text-xs text-[var(--color-text-secondary)] font-mono">
                          <Clock size={12} className="mr-1.5"  strokeWidth={1.5}/>
                          {item.publishedAt ? format(new Date(item.publishedAt), 'MMM d, HH:mm') : 'Unknown'}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end space-x-2">
                          {item.processingStatus === 'COMPLETED' && (
                            <button 
                              onClick={() => setSummaryItemId(item.id as number)}
                              className="inline-flex items-center p-2 text-[var(--color-action-primary)] hover:text-[var(--color-action-hover)] transition-colors"
                              title="View Summary"
                            >
                              <FileText size={16}  strokeWidth={1.5}/>
                            </button>
                          )}
                          <a 
                            href={item.sourceUrl} 
                            target="_blank" 
                            rel="noopener noreferrer"
                            className="inline-flex items-center p-2 text-[var(--color-text-secondary)] hover:text-[var(--color-action-primary)] transition-colors"
                            title="View Original"
                          >
                            <ExternalLink size={16}  strokeWidth={1.5}/>
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

