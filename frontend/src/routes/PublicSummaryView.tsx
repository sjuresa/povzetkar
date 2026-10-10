import { 
  ExternalLink, 
  Hash,
  Calendar,
  ExternalLink as LinkIcon
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { Helmet } from 'react-helmet-async';
import { PublicHeader } from '../components/layout/PublicHeader';
import type { PublicSummary } from '../api/generated/model';
import { REHYPE_PLUGINS } from '../utils/markdown';

interface PublicSummaryViewProps {
  summary: PublicSummary;
}

export function PublicSummaryView({ summary }: Readonly<PublicSummaryViewProps>) {
  const { t } = useTranslation();

  return (
    <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] pb-20 transition-colors">
      <Helmet>
        <title>{summary.title} | Povzetkar</title>
        <meta name="description" content={summary.summaryText ? `${summary.summaryText.slice(0, 160)}...` : ''} />
        <meta property="og:title" content={summary.title} />
        <meta property="og:description" content={summary.summaryText ? `${summary.summaryText.slice(0, 200)}...` : ''} />
        <meta property="og:type" content="article" />
      </Helmet>

      <PublicHeader subtitle={t('briefingDetail.public.sharedSummarySubtitle')} containerClassName="max-w-4xl px-4 sm:px-16" />

      <main className="max-w-4xl mx-auto px-4 pt-16 sm:px-16">
        <div className="animate-in fade-in slide-in-from-bottom-4 duration-700">
            {/* Minimalist Source Header */}
            <div className="flex flex-col gap-6 mb-12">
                <div className="flex items-center gap-4">
                    <div className="w-12 h-12 rounded-xl md:rounded-[28px] bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] flex items-center justify-center text-[var(--color-action-primary)]">
                        <Hash className="w-6 h-6"  strokeWidth={1.5}/>
                    </div>
                    <div className="flex flex-col">
                        <span className="text-[10px] font-black uppercase tracking-[0.2em] text-[var(--color-action-primary)]">
                           {summary.authorName || t('briefingDetail.unknownAuthor')}
                        </span>
                        {summary.publishedAt && (
                          <div className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-tight text-[var(--color-text-secondary)]">
                            <Calendar className="w-3 h-3 text-[var(--color-status-success)]/70"  strokeWidth={1.5}/>
                            <span>{format(new Date(summary.publishedAt), 'MMM d, yyyy')}</span>
                          </div>
                        )}
                    </div>
                </div>

                <a 
                  href={summary.sourceUrl || '#'} 
                  target="_blank" 
                  rel="noopener noreferrer" 
                  className="group flex flex-col gap-2"
                >
                    <h1 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold tracking-tight text-[var(--color-text-primary)] group-hover:text-[var(--color-action-primary)] transition-colors leading-[1.1] text-glow">
                        {summary.title}
                    </h1>
                    <div className="flex items-center gap-2 text-sm text-[var(--color-action-primary)] font-bold opacity-80 group-hover:opacity-100 transition-opacity">
                        <LinkIcon className="w-4 h-4"  strokeWidth={1.5}/>
                        <span>{(() => { try { return summary.sourceUrl ? new URL(summary.sourceUrl).hostname : ''; } catch { return ''; } })()}</span>
                    </div>
                </a>
            </div>

            {/* Main Summary Content */}
            <div className="p-8 md:p-12 rounded-xl md:rounded-[28px] bg-[var(--color-action-primary)]/[0.03] border-l-8 border-[var(--color-action-primary)]/50 shadow-2xl shadow-[var(--color-action-primary)]/5 mb-16">
                <article className="prose prose-zinc max-w-none prose-headings:text-[var(--color-text-primary)] prose-p:text-[var(--color-text-primary)] prose-p:leading-relaxed selection:bg-[var(--color-action-primary)]/30">
                    <ReactMarkdown rehypePlugins={REHYPE_PLUGINS}>
                        {summary.summaryText || ''}
                    </ReactMarkdown>
                </article>
            </div>

            {/* CTA Section */}
            <div className="text-center py-16 border-y border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]/20 rounded-xl md:rounded-[28px]">
                <a href="/" className="inline-flex items-center gap-3 px-10 py-5 rounded-xl md:rounded-[28px] bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] text-[var(--color-action-primary-text)] font-black transition-all shadow-xl shadow-[var(--color-action-primary)]/30 active:scale-95 text-lg">
                    {t('briefingDetail.public.ctaStart')}
                    <ExternalLink className="w-5 h-5"  strokeWidth={1.5}/>
                </a>
            </div>
        </div>
      </main>

      <footer className="max-w-screen-2xl mx-auto px-4 sm:px-16 pt-24 pb-12 text-center space-y-4 border-t border-[var(--color-border-subtle)]/30">
        <p className="text-sm font-black text-[var(--color-text-primary)] tracking-[0.3em] uppercase opacity-80">Povzetkar AI</p>
        <p className="text-[10px] text-[var(--color-text-secondary)] font-medium uppercase tracking-[0.2em] opacity-40">
          {t('common.generated_by')} — AI-Powered Intelligence Aggregator
        </p>
        <p className="text-[10px] text-[var(--color-text-secondary)]/30 uppercase tracking-[0.1em]">&copy; {new Date().getFullYear()} sjuresa/povzetkar</p>
      </footer>
    </div>
  );
}
