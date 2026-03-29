import { useParams } from '@tanstack/react-router';
import { Loader2, Calendar, Hash, ExternalLink, Sun, Moon, Languages, ArrowDown } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { format } from 'date-fns';
import { sl, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';
import { Helmet } from 'react-helmet-async';
import { useThemeStore } from '../store/themeStore';

import { useGetApiV1PublicBriefingsUuid } from '../api/generated/public-briefings/public-briefings';

import rehypeRaw from 'rehype-raw';

export function PublicBriefingPage() {
  const { uuid } = useParams({ from: '/s/$uuid' });
  const { theme, setTheme } = useThemeStore();
  const { t, i18n } = useTranslation();

  const { data: response, isLoading, error } = useGetApiV1PublicBriefingsUuid(uuid);
  const briefing = response?.data;

  // Clean up synthesis duplication: Strip leading H1 title if present
  const cleanSynthesis = briefing?.executiveSummary?.replace(/^#\s+.*\n?/, '').trim();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--color-background-base)]">
        <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" />
      </div>
    );
  }

  if (error || !briefing) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--color-background-base)] px-4 text-center">
        <div>
          <h1 className="text-2xl font-bold text-[var(--color-text-primary)] mb-2">{t('briefingDetail.notFound')}</h1>
          <p className="text-[var(--color-text-secondary)]">{t('briefingDetail.notFoundDesc')}</p>
          <a href="/" className="mt-6 inline-block text-indigo-400 hover:text-indigo-300 transition-colors">
            Povzetkar
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[var(--color-background-base)] text-[var(--color-text-primary)] pb-20 transition-colors">
      <Helmet>
        <title>{briefing.title} | Povzetkar</title>
        <meta name="description" content={briefing.executiveSummary?.slice(0, 160) + '...'} />
        <meta property="og:title" content={briefing.title} />
        <meta property="og:description" content={briefing.executiveSummary?.slice(0, 200) + '...'} />
        <meta property="og:type" content="article" />
      </Helmet>
      {/* Public Header */}
      <header className="sticky top-0 z-10 border-b border-[var(--color-border-subtle)] bg-[var(--color-background-base)]/80 backdrop-blur-md px-4 py-4 sm:px-8">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white">P</div>
              <span className="font-bold text-[var(--color-text-primary)] hidden sm:inline">Povzetkar</span>
            </div>
            
            {/* Theme Toggle Button */}
            <button
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              className="p-2 rounded-xl border border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] transition-all active:scale-95 group"
              title={theme === 'dark' ? t('common.theme_settings.light') : t('common.theme_settings.dark')}
            >
              {theme === 'dark' ? (
                <Sun className="w-4 h-4 text-amber-400 group-hover:rotate-45 transition-transform" />
              ) : (
                <Moon className="w-4 h-4 text-indigo-600 group-hover:-rotate-12 transition-transform" />
              )}
            </button>

            {/* Language Toggle Button */}
            <button
              onClick={() => i18n.changeLanguage(i18n.language === 'sl' ? 'en' : 'sl')}
              className="flex items-center gap-2 px-3 py-2 rounded-xl border border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] transition-all active:scale-95 group"
              title={i18n.language === 'sl' ? 'Switch to English' : 'Preklopi na Slovenščino'}
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

      <main className="max-w-4xl mx-auto px-4 pt-12 sm:px-8">
        <div className="mb-10 animate-in fade-in slide-in-from-bottom-4 duration-700">
          <div className="flex items-center gap-2 text-indigo-500 text-sm font-medium mb-3 uppercase tracking-wider">
             <Hash className="w-4 h-4" />
             <span>{briefing.topicName}</span>
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-[var(--color-text-primary)] tracking-tight leading-tight mb-4">
            {briefing.title}
          </h1>
          <div className="flex items-center gap-4 text-[var(--color-text-secondary)] text-sm">
            <div className="flex items-center gap-1.5">
              <Calendar className="w-4 h-4" />
              <span>
                {format(
                  briefing.briefingDate ? new Date(briefing.briefingDate) : new Date(), 
                  i18n.language === 'sl' ? 'd. MMMM yyyy' : 'MMMM do, yyyy',
                  { locale: i18n.language === 'sl' ? sl : enUS }
                )}
              </span>
            </div>
          </div>
        </div>


        {/* Executive Summary / AI Synthesis */}
        {cleanSynthesis && (
          <div className="mb-12 p-8 rounded-3xl bg-indigo-500/5 border border-indigo-500/10 animate-in fade-in slide-in-from-bottom-2 duration-1000">
            <article className="prose prose-indigo max-w-none prose-p:text-[var(--color-text-primary)] prose-p:leading-relaxed text-lg italic">
              <ReactMarkdown rehypePlugins={[rehypeRaw]}>{cleanSynthesis}</ReactMarkdown>
            </article>
          </div>
        )}

        {/* Source Items - The Reference Grid (MOVED HERE) */}
        {briefing.sourceContentItems && briefing.sourceContentItems.length > 0 && (
          <div className="space-y-8 mb-12 animate-in fade-in slide-in-from-bottom-6 duration-1000 delay-300">
            <h2 className="text-xs font-bold uppercase tracking-[0.3em] text-[var(--color-text-secondary)] mb-6 text-center">
              {t('briefingDetail.sourceItems')}
            </h2>
            <div className="grid gap-6 sm:grid-cols-2">
              {briefing.sourceContentItems.map((item) => (
                <div key={item.id} className="group flex flex-col p-5 rounded-2xl bg-[var(--color-surface-base)] border border-[var(--color-border-subtle)] hover:border-indigo-500/30 hover:shadow-2xl hover:shadow-indigo-500/5 transition-all duration-300">
                  <div className="flex gap-4 mb-4">
                     {item.thumbnailUrl ? (
                         <img src={item.thumbnailUrl} alt="" className="w-20 h-14 rounded-lg object-cover bg-slate-800" />
                     ) : (
                         <div className="w-20 h-14 rounded-lg bg-slate-800 flex items-center justify-center">
                             <Hash className="w-5 h-5 text-slate-600" />
                         </div>
                     )}
                     <div className="flex-1 min-w-0">
                         {item.sourceUrl ? (
                           <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">
                              <h3 className="text-sm font-bold text-[var(--color-text-primary)] line-clamp-2 leading-snug hover:text-indigo-400 transition-colors">
                                  {item.title}
                              </h3>
                           </a>
                         ) : (
                           <h3 className="text-sm font-bold text-[var(--color-text-primary)] line-clamp-2 leading-snug">
                               {item.title}
                           </h3>
                         )}
                         <p className="text-[10px] text-[var(--color-text-secondary)] mt-1 truncate">
                             {item.contentSource?.name}
                         </p>
                     </div>
                  </div>
                  <div className="mt-auto flex items-center justify-between">
                      <a 
                        href={`#item-${item.id}`} 
                        className="inline-flex items-center gap-2 text-[10px] font-bold text-indigo-400 hover:text-indigo-300 transition-colors uppercase tracking-wider"
                      >
                        {t('briefingDetail.seeSummary')}
                        <ArrowDown className="w-3 h-3" />
                      </a>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* CTA Section */}
        <div className="text-center py-12 mb-12 border-y border-[var(--color-border-subtle)] bg-[var(--color-background-base)]/50">
            <p className="text-[var(--color-text-secondary)] text-sm mb-6">{t('briefingDetail.public.generatedBy')}</p>
            <a 
                href="/" 
                className="inline-flex items-center gap-2 px-10 py-5 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold transition-all shadow-2xl shadow-indigo-500/30 active:scale-95 text-lg"
            >
                {t('briefingDetail.public.ctaStart')}
                <ExternalLink className="w-5 h-5" />
            </a>
        </div>

        {/* Full Content / Detailed Report */}
        {briefing.briefingText && (
          <div className="space-y-8 mb-16 px-4">
            <article className="prose prose-indigo max-w-none prose-headings:text-[var(--color-text-primary)] prose-p:text-[var(--color-text-primary)] prose-p:leading-relaxed prose-hr:border-[var(--color-border-subtle)]">
              <ReactMarkdown rehypePlugins={[rehypeRaw]}>{briefing.briefingText}</ReactMarkdown>
            </article>
          </div>
        )}
      </main>
    </div>
  );
}
