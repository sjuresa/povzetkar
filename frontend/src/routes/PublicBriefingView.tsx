import { 
  ExternalLink, 
  Sun, 
  Moon, 
  Languages, 
  ArrowDown, 
  Youtube, 
  Globe, 
  Hash,
  Calendar
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { format } from 'date-fns';
import { sl, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';
import { Helmet } from 'react-helmet-async';
import { useThemeStore } from '../store/themeStore';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import type { BriefingDetailDTO } from '../api/generated/model';

interface PublicBriefingViewProps {
  briefing: BriefingDetailDTO;
}

export function PublicBriefingView({ briefing }: PublicBriefingViewProps) {
  const { theme, setTheme } = useThemeStore();
  const { t, i18n } = useTranslation();

  return (
    <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] pb-20 transition-colors">
      <Helmet>
        <title>{briefing.title} | Povzetkar</title>
        <meta name="description" content={briefing.metaSummary?.slice(0, 160) + '...'} />
        <meta property="og:title" content={briefing.title} />
        <meta property="og:description" content={briefing.metaSummary?.slice(0, 200) + '...'} />
        <meta property="og:type" content="article" />
      </Helmet>

      {/* Shared Header (Common to all public views) */}
      <header className="sticky top-0 z-10 border-b border-[var(--color-border-subtle)] bg-[var(--color-background-base)]/80 backdrop-blur-md px-4 py-4 sm:px-16">
        <div className="max-w-screen-2xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-4">
            <a href="/" className="flex items-center gap-2 group">
              <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white group-hover:scale-105 transition-transform">P</div>
              <span className="font-bold text-[var(--color-text-primary)] hidden sm:inline">Povzetkar</span>
            </a>
            
            <button
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              className="p-2 rounded-xl border border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] transition-all active:scale-95 group"
            >
              {theme === 'dark' ? (
                <Sun className="w-4 h-4 text-amber-400 group-hover:rotate-45 transition-transform" />
              ) : (
                <Moon className="w-4 h-4 text-indigo-600 group-hover:-rotate-12 transition-transform" />
              )}
            </button>

            <button
              onClick={() => void i18n.changeLanguage(i18n.language === 'sl' ? 'en' : 'sl')}
              className="flex items-center gap-2 px-3 py-2 rounded-xl border border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] transition-all active:scale-95 group"
            >
              <Languages className="w-4 h-4 text-indigo-500 group-hover:scale-110 transition-transform" />
              <span className="text-[10px] font-bold uppercase tracking-tight text-[var(--color-text-primary)]">
                {i18n.language === 'sl' ? 'SL' : 'EN'}
              </span>
            </button>
          </div>
          <p className="text-sm text-[var(--color-text-secondary)] italic hidden xs:block">{t('briefingDetail.public.sharedSubtitle')}</p>
        </div>
      </header>

      <main className="max-w-screen-2xl mx-auto px-4 pt-12 sm:px-16">
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
        </div>

        <div className="flex flex-wrap items-center gap-4 text-sm mt-3 font-black italic uppercase tracking-[0.2em] opacity-60 mb-10">
          {briefing.summaries && briefing.summaries.length > 0 && (
            <span className="text-xs text-[var(--color-text-secondary)]">
              {t('briefingDetail.sourcesCount', { count: briefing.summaries.length })}
            </span>
          )}
        </div>

        {/* Executive Summary */}
        {briefing.metaSummary && (() => {
          const cleanedText = briefing.metaSummary.replace(/^#\s+.*\n?/, '').trim();
          const hasAlphaFirst = /^\p{L}/u.test(cleanedText);
          
          return (
            <div className="mb-16 p-10 md:p-16 rounded-[2.5rem] bg-indigo-500/[0.03] border-l-8 border-indigo-500/50 shadow-2xl shadow-indigo-500/5 animate-in fade-in slide-in-from-bottom-2 duration-1000">
              <article className={`prose prose-indigo max-w-none prose-p:text-[var(--color-text-primary)] prose-p:leading-[1.6] text-2xl italic font-serif font-medium opacity-100 selection:bg-indigo-500/30 ${hasAlphaFirst ? 'first-letter:text-5xl first-letter:font-black first-letter:mr-3 first-letter:float-left first-letter:text-indigo-400' : ''}`}>
                <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>
                  {cleanedText}
                </ReactMarkdown>
              </article>
            </div>
          );
        })()}

        {/* Reference Grid */}
        {briefing.summaries && briefing.summaries.length > 0 && (
          <div className="space-y-8 mb-12 animate-in fade-in slide-in-from-bottom-6 duration-1000 delay-300">
            <h2 className="text-xs font-bold uppercase tracking-[0.3em] text-[var(--color-text-secondary)] mb-6 text-center">
              {t('briefingDetail.sourceItems')}
            </h2>
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {briefing.summaries.map((item) => (
                <div key={item.contentItem?.id} className="group flex flex-col p-5 rounded-2xl bg-[var(--color-surface-base)] border border-[var(--color-border-subtle)] hover:border-indigo-500/30 hover:shadow-2xl hover:shadow-indigo-500/5 transition-all duration-300">
                  <div className="flex flex-col gap-4 mb-4">
                     {item.contentItem?.thumbnailUrl ? (
                         <img 
                           src={item.contentItem.thumbnailUrl} 
                           alt={item.contentItem.title || 'Source thumbnail'} 
                           className="w-full aspect-video rounded-lg object-cover bg-slate-800" 
                         />
                     ) : (
                         <div className="w-full aspect-video rounded-lg bg-slate-800 flex items-center justify-center">
                             <Hash className="w-8 h-8 text-slate-600" />
                         </div>
                     )}
                     <div className="flex-1 min-w-0">
                         {item.contentItem?.sourceUrl ? (
                           <a href={item.contentItem.sourceUrl} target="_blank" rel="noopener noreferrer">
                              <h3 className="text-sm font-bold text-[var(--color-text-primary)] line-clamp-2 leading-snug hover:text-indigo-400 transition-colors">
                                  {item.contentItem.title}
                              </h3>
                           </a>
                         ) : (
                           <h3 className="text-sm font-bold text-[var(--color-text-primary)] line-clamp-2 leading-snug">
                               {item.contentItem?.title}
                           </h3>
                         )}
                         <div className="flex flex-wrap items-center gap-x-2 gap-y-1 mt-2">
                             <span className="text-[10px] font-black text-indigo-400 uppercase tracking-tight">{item.contentItem?.authorName}</span>
                             <span className="text-[10px] text-[var(--color-text-secondary)] opacity-40">•</span>
                             <span className="text-[10px] text-[var(--color-text-secondary)] font-bold">
                               {item.contentItem?.publishedAt && format(new Date(item.contentItem.publishedAt), 'MMM d, yyyy')}
                             </span>
                         </div>
                     </div>
                  </div>
                  <div className="mt-auto flex items-center justify-between">
                      <a href={`#item-${item.contentItem?.id}`} className="inline-flex items-center gap-2 text-[10px] font-bold text-indigo-400 hover:text-indigo-300 transition-colors uppercase tracking-wider">
                        {t('briefingDetail.seeSummary')}
                        <ArrowDown className="w-3 h-3" />
                      </a>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="text-center py-20 mb-20 border-y border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]/20 rounded-[3rem]">
            <a href="/" className="inline-flex items-center gap-3 px-12 py-6 rounded-3xl bg-indigo-600 hover:bg-indigo-500 text-white font-black transition-all shadow-2xl shadow-indigo-500/40 active:scale-95 text-xl tracking-tight">
                {t('briefingDetail.public.ctaStart')}
                <ExternalLink className="w-6 h-6" />
            </a>
        </div>

        {/* Detailed Sections */}
        {briefing.summaries && briefing.summaries.length > 0 && (
          <div className="space-y-16 mb-16">
            {briefing.summaries.map((item) => (
              <div key={item.contentItem?.id} id={`item-${item.contentItem?.id}`} className="scroll-mt-24 pt-12 border-t border-[var(--color-border-subtle)] first:border-0 first:pt-0">
                <div className="flex items-start gap-4 mb-8 group/source">
                  <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 flex items-center justify-center text-indigo-500 flex-shrink-0 group-hover/source:bg-indigo-500/20 transition-colors">
                    {item.contentItem?.contentSource?.platform === 'YOUTUBE' ? <Youtube className="w-6 h-6 text-red-500" /> : <Globe className="w-6 h-6" />}
                  </div>
                  <div className="flex-1 min-w-0">
                     <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mt-0.5">
                        <span className="text-[10px] font-black uppercase tracking-[0.2em] text-indigo-400">
                           {item.contentItem?.authorName || t('briefingDetail.unknownAuthor')}
                        </span>
                        {item.contentItem?.publishedAt && (
                          <div className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-tight text-[var(--color-text-secondary)]">
                            <Calendar className="w-3 h-3 text-emerald-500/70" />
                            <span>{format(new Date(item.contentItem.publishedAt), 'MMM d, yyyy')}</span>
                          </div>
                        )}
                     </div>
                    <a href={item.contentItem?.sourceUrl || '#'} target="_blank" rel="noopener noreferrer" className="text-2xl font-black text-[var(--color-text-primary)] hover:text-indigo-400 theme-transition flex items-center gap-2 group/link leading-tight mt-1">
                      <span className="truncate">{item.contentItem?.title}</span>
                      <ExternalLink className="w-4 h-4 text-[var(--color-text-secondary)] group-hover/link:text-indigo-400 transition-colors" />
                    </a>
                  </div>
                </div>
                <article className="prose prose-indigo max-w-none prose-headings:text-[var(--color-text-primary)] prose-p:text-[var(--color-text-primary)] prose-p:leading-relaxed prose-hr:border-[var(--color-border-subtle)]">
                   <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>
                     {item.summaryText || ''}
                   </ReactMarkdown>
                </article>
              </div>
            ))}
          </div>
        )}
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
