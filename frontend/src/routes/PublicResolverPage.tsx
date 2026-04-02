import { useParams } from '@tanstack/react-router';
import { Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useGetApiV1PublicResolveUuid } from '../api/generated/public-insight-resolver/public-insight-resolver';
import { PublicBriefingView } from './PublicBriefingView';
import { PublicSummaryView } from './PublicSummaryView';
import type { BriefingDetailDTO, PublicSummary } from '../api/generated/model';

export function PublicResolverPage() {
  const { uuid } = useParams({ from: '/s/$uuid' });
  const { t } = useTranslation();

  const { data: response, isLoading, error } = useGetApiV1PublicResolveUuid(uuid);
  const result = response?.data;

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--color-background-base)]">
        <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" />
      </div>
    );
  }

  if (error || !result) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--color-background-base)] px-4 text-center">
        <div className="max-w-md animate-in fade-in zoom-in duration-500">
            <div className="w-20 h-20 bg-indigo-500/10 rounded-3xl flex items-center justify-center mx-auto mb-6">
                <span className="text-3xl font-bold text-indigo-500">?</span>
            </div>
            <h2 className="text-2xl font-bold text-[var(--color-text-primary)] mb-3">
                {t('briefingDetail.notFound')}
            </h2>
            <p className="text-[var(--color-text-secondary)] mb-8 leading-relaxed">
                {t('briefingDetail.notFoundDesc')}
            </p>
            <a 
                href="/" 
                className="inline-flex items-center justify-center px-8 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold transition-all shadow-lg shadow-indigo-500/20 active:scale-95"
            >
                Povzetkar
            </a>
        </div>
      </div>
    );
  }

  if (result.type === 'BRIEFING') {
    return <PublicBriefingView briefing={result.data as BriefingDetailDTO} />;
  }

  if (result.type === 'SUMMARY') {
    return <PublicSummaryView summary={result.data as PublicSummary} />;
  }

  return null;
}
