import { useParams } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Loader2, Calendar, Hash, ExternalLink, Sun, Moon, Languages } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { format } from 'date-fns';
import { sl, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';
import { Helmet } from 'react-helmet-async';
import { useThemeStore } from '../store/themeStore';

export function PublicBriefingPage() {
  const { uuid } = useParams({ from: '/s/$uuid' });
  const { theme, setTheme } = useThemeStore();
  const { t, i18n } = useTranslation();

  // Note: We use the existing query logic but point it to the public endpoint
  // I will check if briefings.getPublicBriefing exists after generation
  const { data: briefing, isLoading, error } = useQuery({
    queryKey: ['public-briefing', uuid],
    queryFn: () => fetch(`/api/v1/public/briefings/${uuid}`).then(res => {
      if (!res.ok) throw new Error('Briefing not found');
      return res.json().then(data => data.data); // Enveloped response
    }),
  });

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
                  new Date(briefing.briefingDate), 
                  i18n.language === 'sl' ? 'd. MMMM yyyy' : 'MMMM do, yyyy',
                  { locale: i18n.language === 'sl' ? sl : enUS }
                )}
              </span>
            </div>
          </div>
        </div>


        {/* Full Content */}
        <article className="prose max-w-none prose-headings:text-[var(--color-text-primary)] prose-p:text-[var(--color-text-primary)] prose-p:leading-relaxed prose-hr:border-[var(--color-border-subtle)]">
          <ReactMarkdown>{briefing.briefingText}</ReactMarkdown>
        </article>

        <hr className="my-16 border-[var(--color-border-subtle)]" />

        <div className="text-center">
            <p className="text-[var(--color-text-secondary)] text-sm mb-6">{t('briefingDetail.public.generatedBy')}</p>
            <a 
                href="/" 
                className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold transition-all transform hover:scale-105 active:scale-95 shadow-xl shadow-indigo-500/10"
            >
                {t('briefingDetail.public.ctaStart')}
                <ExternalLink className="w-4 h-4" />
            </a>
        </div>
      </main>
    </div>
  );
}
