import { Link, useParams } from '@tanstack/react-router';
import { useGetApiV1BriefingsId } from '../api/generated/briefings/briefings';
import { Skeleton } from '../components/ui/Skeleton';
import ReactMarkdown from 'react-markdown';
import { useTranslation } from 'react-i18next';
import {
  ArrowLeft,
  Calendar,
  FileText,
  ExternalLink,
  Clock,
  AlertCircle,
  Youtube,
  Globe,
} from 'lucide-react';
import { ExportMenu } from '../components/briefing/ExportMenu';
import type { BriefingDetailDTO, ContentItem } from '../api/generated/model';

/**
 * BriefingDetailPage renders the full content of a single briefing.
 * Fetches by ID from route params and displays executive summary,
 * full briefing text (markdown), and a list of source content items.
 * Part of POV-161 implementation.
 */
export function BriefingDetailPage() {
  const { t } = useTranslation();
  const { briefingId } = useParams({ strict: false });
  const id = Number(briefingId);

  const { data: response, isLoading, error } = useGetApiV1BriefingsId(id, {
    query: { enabled: !!id && !isNaN(id) },
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

  const sourceItems = (briefing.sourceContentItems || []) as ContentItem[];

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
                briefingText: briefing.briefingText || '',
                executiveSummary: briefing.executiveSummary || '',
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
                ? new Date(briefing.briefingDate + 'T00:00:00').toLocaleDateString()
                : '—'}
            </span>
          </div>
          <span className="text-xs font-bold uppercase tracking-wider text-blue-500 bg-blue-500/10 px-3 py-1 rounded-lg">
            {briefing.topicName}
          </span>
          {sourceItems.length > 0 && (
            <span className="text-xs text-[var(--color-text-secondary)]">
              {t('briefingDetail.sourcesCount', { count: sourceItems.length })}
            </span>
          )}
        </div>
      </div>

      {/* Executive Summary Card */}
      {briefing.executiveSummary && (
        <div className="glass border border-blue-500/20 rounded-3xl p-6 md:p-8 bg-blue-500/5">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-8 h-8 rounded-xl bg-blue-500/10 flex items-center justify-center text-blue-500">
              <FileText className="w-4 h-4" />
            </div>
            <h2 className="text-sm font-bold uppercase tracking-widest text-blue-400">
              {t('briefingDetail.executiveSummary')}
            </h2>
          </div>
          <div className="prose prose-invert prose-sm max-w-none text-[var(--color-text-secondary)] leading-relaxed">
            <ReactMarkdown>{briefing.executiveSummary}</ReactMarkdown>
          </div>
        </div>
      )}

      {/* Full Briefing Content */}
      {briefing.briefingText && (
        <div className="glass border border-[var(--color-border-subtle)] rounded-3xl p-6 md:p-10 shadow-xl shadow-black/5">
          <h2 className="text-sm font-bold uppercase tracking-widest text-[var(--color-text-secondary)] mb-6">
            {t('briefingDetail.fullBriefing')}
          </h2>
          <article className="prose prose-invert max-w-none">
            <ReactMarkdown>{briefing.briefingText}</ReactMarkdown>
          </article>
        </div>
      )}

      {/* Source Content Items */}
      {sourceItems.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-sm font-bold uppercase tracking-widest text-[var(--color-text-secondary)]">
            {t('briefingDetail.sourceItems')}
          </h2>
          <div className="grid gap-4 md:grid-cols-2">
            {sourceItems.map((item) => (
              <div
                key={item.id}
                className="glass border border-[var(--color-border-subtle)] rounded-2xl p-5 hover:border-blue-500/30 transition-all group"
              >
                <div className="flex items-start gap-4">
                  {/* Thumbnail or platform icon */}
                  {item.thumbnailUrl ? (
                    <img
                      src={item.thumbnailUrl}
                      alt={item.title || ''}
                      className="w-24 h-16 rounded-xl object-cover flex-shrink-0 bg-[var(--color-surface-deep)]"
                    />
                  ) : (
                    <div className="w-12 h-12 rounded-xl bg-[var(--color-surface-deep)] flex items-center justify-center flex-shrink-0 text-[var(--color-text-secondary)]">
                      {item.contentSource?.platform === 'YOUTUBE' ? (
                        <Youtube className="w-5 h-5 text-red-500" />
                      ) : (
                        <Globe className="w-5 h-5 text-blue-500" />
                      )}
                    </div>
                  )}

                  <div className="flex-1 min-w-0">
                    <h3 className="font-medium text-[var(--color-text-primary)] text-sm line-clamp-2 group-hover:text-blue-400 transition-colors">
                      {item.title}
                    </h3>
                    <div className="flex items-center gap-3 mt-2 text-xs text-[var(--color-text-secondary)]">
                      {item.contentSource?.name && (
                        <span className="truncate">{item.contentSource.name}</span>
                      )}
                      {item.durationSeconds != null && item.durationSeconds > 0 && (
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {Math.round(item.durationSeconds / 60)}m
                        </span>
                      )}
                    </div>
                  </div>

                  {item.sourceUrl && (
                    <a
                      href={item.sourceUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="p-2 rounded-xl hover:bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)] hover:text-blue-400 transition-all flex-shrink-0"
                      title={t('briefingDetail.openOriginal')}
                    >
                      <ExternalLink className="w-4 h-4" />
                    </a>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
