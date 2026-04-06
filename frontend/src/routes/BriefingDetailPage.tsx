import { useEffect } from 'react';
import { Link, useParams, useNavigate } from '@tanstack/react-router';
import { useGetApiV1BriefingsId } from '../api/generated/briefings/briefings';
import { Skeleton } from '../components/ui/Skeleton';
import ReactMarkdown from 'react-markdown';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { sl, enUS } from 'date-fns/locale';
import {
  ArrowLeft,
  AlertCircle,
  Youtube,
  Globe,
  ArrowDown,
  ExternalLink,
  Calendar,
  Clock,
  Timer,
} from 'lucide-react';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { ExportMenu } from '../components/briefing/ExportMenu';
import type { BriefingDetailDTO, BriefingItemDTO } from '../api/generated/model';
import { useOnboardingStore } from '../store/onboardingStore';

/**
 * BriefingDetailPage renders the full content of a single briefing.
 * Fetches by ID from route params and displays executive summary,
 * full briefing text (markdown), and a list of source content items.
 * Part of POV-161 implementation.
 */
export function BriefingDetailPage() {
  const { t, i18n } = useTranslation();
  const { briefingId } = useParams({ strict: false });
  const navigate = useNavigate();
  const id = Number(briefingId);

  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        void navigate({ to: '/briefings' });
      }
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [navigate]);

  const { data: response, isLoading, error } = useGetApiV1BriefingsId(id, {
    query: { enabled: !!id && !Number.isNaN(id) },
  });

  const briefing = response?.data as BriefingDetailDTO | undefined;

  // Step 4 onboarding: mark 'read' when the user lands on a briefing
  const markStepComplete = useOnboardingStore((s) => s.markStepComplete);
  useEffect(() => {
    if (briefing) {
      markStepComplete('read');
    }
  }, [briefing, markStepComplete]);

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
        <div className="flex flex-col md:flex-row md:items-start justify-between gap-6">
          <h1 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold tracking-tight text-[var(--color-text-primary)] text-glow leading-[1.1] flex-1 min-w-0">
            {t('briefingDetail.title', { 
              topic: briefing.topicName, 
              date: briefing.briefingDate
                ? format(new Date(briefing.briefingDate + 'T00:00:00'), 'PPP', { 
                    locale: i18n.language === 'sl' ? sl : enUS 
                  })
                : '—'
            })}
          </h1>
          {briefing.id != null && (
            <ExportMenu 
              type="briefing"
              data={{
                id: briefing.id,
                uuid: briefing.uuid ?? null,
                title: briefing.title || '',
                topicName: briefing.topicName || '',
                date: briefing.briefingDate || '',
                metaSummary: briefing.metaSummary || '',
                summaries: briefing.summaries || [],
              }}
              variant="outline"
              className="md:self-center"
            />
          )}
        </div>

        <div className="flex flex-wrap items-center gap-4 text-sm mt-2 font-black italic uppercase tracking-[0.2em] opacity-60">
          {briefing.summaries && briefing.summaries.length > 0 && (
            <span className="text-xs text-[var(--color-text-secondary)]">
              {t('briefingDetail.sourcesCount', { count: briefing.summaries.length })}
            </span>
          )}
        </div>
      </div>

      {/* Executive Summary Section */}
      {briefing.metaSummary && (() => {
        const cleanedMeta = briefing.metaSummary.replace(/^#\s+.*\n?/, '').trim();
        const hasAlphaStart = /^\p{L}/u.test(cleanedMeta);
        
        return (
          <div className="glass border-l-8 border-blue-500/50 rounded-[2.5rem] p-12 md:p-20 bg-blue-500/[0.04] shadow-2xl shadow-blue-500/10 transition-all animate-in slide-in-from-left-4 duration-1000">
            <div className={`prose prose-invert prose-2xl max-w-none text-[var(--color-text-primary)] leading-[1.6] italic font-serif font-medium opacity-100 selection:bg-blue-500/30 ${hasAlphaStart ? 'first-letter:text-5xl first-letter:font-black first-letter:mr-3 first-letter:float-left first-letter:text-blue-400' : ''}`}>
              <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>
                {cleanedMeta}
              </ReactMarkdown>
            </div>
          </div>
        );
      })()}

      {/* Source Reference Grid - MOVED UP BEFORE CONTENT */}
      {briefing.summaries && briefing.summaries.length > 0 && (
        <div className="space-y-6">
          <h2 className="text-xs font-bold uppercase tracking-[0.3em] text-[var(--color-text-secondary)] ml-2">
            {t('briefingDetail.sourceItems')}
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {briefing.summaries.map((item: BriefingItemDTO) => (
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
                    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 mt-2">
                      <span className="text-[10px] font-bold text-blue-400/80 uppercase tracking-tight">{item.contentItem?.authorName}</span>
                      <span className="text-[10px] text-[var(--color-text-secondary)] opacity-40">•</span>
                      <span className="text-[10px] text-[var(--color-text-secondary)] opacity-60">
                        {item.contentItem?.publishedAt && format(new Date(item.contentItem.publishedAt), 'MMM d, yyyy')}
                      </span>
                      {item.contentItem?.formattedDuration && (
                        <>
                          <span className="text-[10px] text-[var(--color-text-secondary)] opacity-40">•</span>
                          <span className="text-[10px] text-[var(--color-text-secondary)] opacity-60">{item.contentItem.formattedDuration}</span>
                        </>
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
          <div className="flex items-center justify-center gap-6 mb-16 opacity-30">
            <div className="h-px bg-[var(--color-border-subtle)] flex-1"></div>
            <div className="text-[10px] font-black uppercase tracking-[0.5em] whitespace-nowrap">
              {t('briefingDetail.fullBriefing')}
            </div>
            <div className="h-px bg-[var(--color-border-subtle)] flex-1"></div>
          </div>
          <div className="space-y-16">
            {briefing.summaries.map((item: BriefingItemDTO) => (
              <div 
                key={item.contentItem?.id} 
                id={`item-${item.contentItem?.id}`}
                className="scroll-mt-24 pt-12 border-t border-[var(--color-border-subtle)] first:border-0 first:pt-0"
              >
                {/* Minimalist Source Header */}
                <div className="flex items-start gap-4 mb-8 group/source">
                  <div className="w-12 h-12 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-500 flex-shrink-0 group-hover/source:bg-blue-500/20 transition-colors">
                    {item.contentItem?.contentSource?.platform === 'YOUTUBE' ? (
                      <Youtube className="w-6 h-6 text-red-500" />
                    ) : (
                      <Globe className="w-6 h-6" />
                    )}
                  </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex flex-col md:flex-row md:items-start justify-between gap-4">
                        <div className="flex-1 min-w-0">
                           <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mt-0.5">
                              <span className="text-[10px] font-black uppercase tracking-[0.2em] text-blue-400">
                                 {item.contentItem?.authorName || t('briefingDetail.unknownAuthor')}
                              </span>
                              
                              {(item.contentItem?.publishedAt || item.contentItem?.formattedDuration) && (
                                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                                  {item.contentItem?.publishedAt && (
                                    <div className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-tight text-[var(--color-text-secondary)]">
                                      <Calendar className="w-3 h-3 text-emerald-500/70" />
                                      <span>{format(new Date(item.contentItem.publishedAt), 'MMM d, yyyy')}</span>
                                      <span className="opacity-30 mx-0.5">•</span>
                                      <Clock className="w-3 h-3 text-indigo-500/70" />
                                      <span>{format(new Date(item.contentItem.publishedAt), 'HH:mm')}</span>
                                    </div>
                                  )}
                                  
                                  {item.contentItem?.formattedDuration && (
                                    <div className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-tight text-[var(--color-text-secondary)]">
                                      <span className="opacity-30 mr-1.5">|</span>
                                      <Timer className="w-3 h-3 text-amber-500/70" />
                                      <span className="text-amber-500/90">{item.contentItem.formattedDuration}</span>
                                    </div>
                                  )}
                                </div>
                              )}
                           </div>
                          <a 
                            href={item.contentItem?.sourceUrl || '#'} 
                            target="_blank" 
                            rel="noopener noreferrer" 
                            className="text-2xl font-black text-[var(--color-text-primary)] hover:text-blue-400 theme-transition flex items-center gap-2 group/link leading-tight mt-1"
                          >
                            <span className="truncate">{item.contentItem?.title}</span>
                            <ExternalLink className="w-4 h-4 text-[var(--color-text-secondary)] group-hover/link:text-blue-400 transition-colors" />
                          </a>
                        </div>

                        <div className="bg-[var(--color-surface-deep)]/40 p-4 rounded-3xl border border-[var(--color-border-subtle)] md:w-80">
                           {item.contentItem?.id != null && (
                             <ExportMenu 
                               type="summary"
                               data={{
                                 id: item.contentItem.id,
                                 uuid: item.uuid || null,
                                 title: item.contentItem.title || '',
                                 topicName: briefing.topicName || '',
                                 date: item.contentItem.publishedAt || new Date().toISOString(),
                                 metaSummary: '',
                                 summaries: [{ summaryText: item.summaryText || '' }]
                               }}
                               variant="ghost"
                               className="w-full"
                             />
                           )}
                        </div>
                      </div>
                    </div>
                </div>

                <article className="prose prose-invert max-w-none prose-headings:text-bold prose-headings:text-[var(--color-text-primary)] prose-p:leading-relaxed prose-p:text-[var(--color-text-secondary)]">
                   <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>
                     {item.summaryText || ''}
                   </ReactMarkdown>
                </article>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Footer Branding */}
      <div className="pt-24 pb-12 text-center space-y-4 border-t border-[var(--color-border-subtle)]/20">
        <p className="text-sm font-black text-[var(--color-text-primary)] tracking-[0.3em] uppercase opacity-80">
          Povzetkar AI
        </p>
        <p className="text-[10px] text-[var(--color-text-secondary)] font-medium uppercase tracking-[0.2em] opacity-40">
          {t('common.generated_by')} — AI-Powered Intelligence Aggregator
        </p>
        <p className="text-[10px] text-[var(--color-text-secondary)]/30 uppercase tracking-[0.1em]">
          &copy; {new Date().getFullYear()} sjuresa/povzetkar
        </p>
      </div>
    </div>
  );
}
