import { Link, useParams } from '@tanstack/react-router';
import { useGetApiV1BriefingsId } from '../api/generated/briefings/briefings';
import { Skeleton } from '../components/ui/Skeleton';
import ReactMarkdown from 'react-markdown';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { sl, enUS } from 'date-fns/locale';
import {
  ArrowLeft,
  Calendar,
  AlertCircle,
  Youtube,
  Globe,
  ArrowDown,
} from 'lucide-react';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { ExportMenu } from '../components/briefing/ExportMenu';
import type { BriefingDetailDTO } from '../api/generated/model';

/**
 * BriefingDetailPage renders the full content of a single briefing.
 * Fetches by ID from route params and displays executive summary,
 * full briefing text (markdown), and a list of source content items.
 * Part of POV-161 implementation.
 */
export function BriefingDetailPage() {
  const { t, i18n } = useTranslation();
  const { briefingId } = useParams({ strict: false });
  const id = Number(briefingId);

  const { data: response, isLoading, error } = useGetApiV1BriefingsId(id, {
    query: { enabled: !!id && !Number.isNaN(id) },
  });

  const briefing = response?.data as BriefingDetailDTO | undefined;

  if (isLoading) {
    return (
      <div className="space-y-8 animate-in fade-in duration-500">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-64 w-full rounded-3xl" />
        <Skeleton className="h-96 w-full rounded-3xl" />
      </div>
    );
  }

  if (error || !briefing) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] text-center space-y-6 animate-in fade-in duration-500">
        <div className="w-20 h-20 rounded-full bg-red-500/10 flex items-center justify-center text-red-500 ring-8 ring-red-500/5">
          <AlertCircle size={40} />
        </div>
        <h2 className="text-2xl font-bold text-[var(--color-text-primary)]">
          {t('briefingDetail.notFound')}
        </h2>
        <p className="text-[var(--color-text-secondary)] max-w-md">
          {t('briefingDetail.notFoundDesc')}
        </p>
        <Link
          to="/briefings"
          className="px-6 py-3 rounded-2xl glass hover:bg-[var(--color-surface-hover)] text-[var(--color-text-primary)] border border-[var(--color-border-subtle)] font-bold transition-all active:scale-95"
        >
          {t('briefingDetail.backToLibrary')}
        </Link>
      </div>
    );
  }


  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      {/* Back Navigation */}
      <Link
        to="/briefings"
        className="inline-flex items-center gap-2 text-sm text-[var(--color-text-secondary)] hover:text-blue-400 transition-colors group"
      >
        <ArrowLeft className="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
        {t('briefingDetail.backToLibrary')}
      </Link>

      {/* Header */}
      <div className="space-y-4">
        <div className="flex flex-col md:flex-row md:items-center gap-4">
          <h1 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] text-glow flex-1">
            {briefing.title}
          </h1>
          {briefing.id != null && (
            <ExportMenu 
              briefing={{
                id: briefing.id,
                uuid: briefing.uuid ?? null,
                title: briefing.title || '',
                topicName: briefing.topicName || '',
                briefingDate: briefing.briefingDate || '',
                metaSummary: briefing.metaSummary || '',
                summaries: briefing.summaries || [],
              }}
              variant="outline"
              className="md:self-center"
            />
          )}
        </div>

        <div className="flex flex-wrap items-center gap-4 text-sm">
          <div className="flex items-center gap-2 text-[var(--color-text-secondary)]">
            <Calendar className="w-4 h-4" />
            <span>
              {briefing.briefingDate
                ? format(new Date(briefing.briefingDate + 'T00:00:00'), 'PPP', { 
                    locale: i18n.language === 'sl' ? sl : enUS 
                  })
                : '—'}
            </span>
          </div>
          <span className="text-xs font-bold uppercase tracking-wider text-blue-500 bg-blue-500/10 px-3 py-1 rounded-lg">
            {briefing.topicName}
          </span>
          {briefing.summaries && briefing.summaries.length > 0 && (
            <span className="text-xs text-[var(--color-text-secondary)]">
              {t('briefingDetail.sourcesCount', { count: briefing.summaries.length })}
            </span>
          )}
        </div>
      </div>

      {/* Executive Summary Section */}
      {briefing.metaSummary && (
        <div className="glass border border-blue-500/20 rounded-3xl p-6 md:p-10 bg-blue-500/5 shadow-xl shadow-blue-500/5 transition-all">
          <div className="prose prose-invert prose-lg max-w-none text-[var(--color-text-secondary)] leading-relaxed italic">
            <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>
              {briefing.metaSummary.replace(/^#\s+.*\n?/, '').trim()}
            </ReactMarkdown>
          </div>
        </div>
      )}

      {/* Source Reference Grid - MOVED UP BEFORE CONTENT */}
      {briefing.summaries && briefing.summaries.length > 0 && (
        <div className="space-y-6">
          <h2 className="text-xs font-bold uppercase tracking-[0.3em] text-[var(--color-text-secondary)] ml-2">
            {t('briefingDetail.sourceItems')}
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {briefing.summaries.map((item) => (
              <div
                key={item.contentItem?.id}
                className="glass border border-[var(--color-border-subtle)] rounded-2xl p-5 hover:border-blue-500/30 transition-all group flex flex-col"
              >
                <div className="flex flex-col gap-4 mb-4">
                  {item.contentItem?.thumbnailUrl ? (
                    <img
                      src={item.contentItem.thumbnailUrl}
                      alt={item.contentItem?.title || 'Thumbnail'}
                      className="w-full aspect-video rounded-xl object-cover bg-[var(--color-surface-deep)]"
                    />
                  ) : (
                    <div className="w-full aspect-video rounded-xl bg-[var(--color-surface-deep)] flex items-center justify-center text-[var(--color-text-secondary)]">
                      {item.contentItem?.contentSource?.platform === 'YOUTUBE' ? (
                        <Youtube className="w-8 h-8 text-red-500" />
                      ) : (
                        <Globe className="w-8 h-8 text-blue-500" />
                      )}
                    </div>
                  )}

                  <div className="flex-1 min-w-0">
                    <a href={item.contentItem?.sourceUrl || '#'} target="_blank" rel="noopener noreferrer">
                      <h3 className="font-bold text-[var(--color-text-primary)] text-sm line-clamp-2 group-hover:text-blue-400 transition-colors leading-snug">
                        {item.contentItem?.title}
                      </h3>
                    </a>
                    <div className="flex items-center gap-3 mt-2 text-[10px] text-[var(--color-text-secondary)] uppercase tracking-tight">
                      {item.contentItem?.contentSource?.name && (
                        <span className="truncate">{item.contentItem.contentSource.name}</span>
                      )}
                    </div>
                  </div>
                </div>
                
                <div className="mt-auto flex items-center justify-between">
                   <a
                      href={`#item-${item.contentItem?.id}`}
                      className="inline-flex items-center gap-1.5 text-[10px] font-bold text-blue-400/80 hover:text-blue-400 transition-colors uppercase tracking-widest"
                    >
                      <ArrowDown className="w-3 h-3" />
                      {t('briefingDetail.seeSummary')}
                    </a>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Full Detailed Content */}
      {briefing.summaries && briefing.summaries.length > 0 && (
        <div className="glass border border-[var(--color-border-subtle)] rounded-3xl p-6 md:p-10 shadow-xl shadow-black/5">
          <h2 className="text-xs font-bold uppercase tracking-[0.3em] text-[var(--color-text-secondary)] mb-10 text-center">
            {t('briefingDetail.fullBriefing')}
          </h2>
          <div className="space-y-16">
            {briefing.summaries.map((item) => (
              <div 
                key={item.contentItem?.id} 
                id={`item-${item.contentItem?.id}`}
                className="scroll-mt-24 pt-8 border-t border-[var(--color-border-subtle)] first:border-0 first:pt-0"
              >
                <article className="prose prose-invert max-w-none prose-headings:text-blue-400 prose-headings:font-extrabold prose-p:leading-relaxed">
                   <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>
                     {item.summaryText || ''}
                   </ReactMarkdown>
                </article>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
