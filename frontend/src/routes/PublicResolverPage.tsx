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
        <Loader2 className="w-8 h-8 text-[var(--color-action-primary)] animate-spin"  strokeWidth={1.5}/>
      </div>
    );
  }

  if (error || !result) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--color-background-base)] px-4 text-center">
        <div className="max-w-md animate-in fade-in zoom-in duration-500">
            <div className="w-20 h-20 bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] rounded-xl md:rounded-[28px] flex items-center justify-center mx-auto mb-6">
                <span className="text-3xl font-bold text-[var(--color-action-primary)]">?</span>
            </div>
            <h2 className="text-2xl font-bold text-[var(--color-text-primary)] mb-3">
                {t('briefingDetail.notFound')}
            </h2>
            <p className="text-[var(--color-text-secondary)] mb-8 leading-relaxed">
                {t('briefingDetail.notFoundDesc')}
            </p>
            <a 
                href="/" 
                className="inline-flex items-center justify-center px-8 py-3 rounded-xl bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] text-[var(--color-action-primary-text)] font-bold transition-all shadow-lg shadow-[var(--color-action-primary)]/20 active:scale-95"
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
