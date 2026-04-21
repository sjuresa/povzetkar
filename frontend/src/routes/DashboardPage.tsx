import { useEffect } from 'react';
import { useGetApiV1Dashboard } from '../api/generated/dashboard/dashboard';
import { StatCard, StatCardSkeleton } from '../components/dashboard/StatCard';
import type { DashboardDTO } from '../api/generated/model';
import { EmptyState } from '../components/dashboard/EmptyState';
import { RecentBriefingList, BriefingListSkeleton } from '../components/dashboard/RecentBriefingList';
import { useEventStore } from '../store/eventStore';
import { useShallow } from 'zustand/react/shallow';
import { Skeleton } from '../components/ui/Skeleton';
import { useTranslation } from 'react-i18next';
import { OnboardingChecklist } from '../components/dashboard/OnboardingChecklist';
import { useOnboardingStore } from '../store/onboardingStore';

export function DashboardPage() {
  const { t, i18n } = useTranslation();
  const activeJobs = useEventStore(
    useShallow((state) => 
      Object.values(state.jobs).filter(j => j.status === 'RUNNING' || j.status === 'QUEUED')
    )
  );
  const { data: response, isLoading, refetch, isRefetching } = useGetApiV1Dashboard({
    query: {
      refetchOnWindowFocus: true,
      staleTime: 30000, // 30 seconds
    }
  });

  const data = response?.data as DashboardDTO | undefined;

  const { syncFromDashboard, dismissed } = useOnboardingStore(
    useShallow((state) => ({
      syncFromDashboard: state.syncFromDashboard,
      dismissed: state.dismissed,
    }))
  );

  const activeTopics = data?.stats?.activeTopics;
  // hasBriefings means they've done step 3 (Trigger). We check 7 day stats OR if they have ANY recent briefings.
  const hasBriefings = (data?.stats?.briefingsGeneratedLast7Days ?? 0) > 0 || (data?.recentBriefings?.length ?? 0) > 0;
  
  const hasNoData = !activeTopics && !hasBriefings;
  const showWelcome = hasNoData;

  // Hybrid mount sync: auto-complete steps 1+3 when derivable from dashboard data
  useEffect(() => {
    if (activeTopics !== undefined || hasBriefings) {
      syncFromDashboard(activeTopics, hasBriefings);
    }
  }, [activeTopics, hasBriefings, syncFromDashboard]);

  if (isLoading) {
    return (
      <div className="space-y-10">
        <div className="flex items-center justify-between">
          <Skeleton className="h-9 w-48" />
        </div>
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          <StatCardSkeleton />
          <StatCardSkeleton />
          <StatCardSkeleton />
        </div>
        <div className="mt-10">
          <BriefingListSkeleton />
        </div>
      </div>
    );
  }

  const latestBriefingDate = data?.recentBriefings?.[0]?.briefingDate;

  return (
    <div className="space-y-8 animate-in fade-in duration-700">
      {showWelcome && <EmptyState />}
      {!dismissed && <OnboardingChecklist />}

      {!hasNoData && (
        <>
          <div className="flex items-center justify-between mt-2">
            <h2 className="text-4xl text-header text-[var(--color-text-primary)] text-glow">
              {t('dashboard.title')}
            </h2>
            <div className="flex items-center space-x-4">
              {activeJobs.length > 0 && (
                <div className="flex items-center space-x-2 bg-[var(--color-action-primary)]/[0.04] text-[var(--color-action-primary)] px-3 py-1.5 rounded-xl border border-[var(--color-action-primary)]/20 shadow-sm animate-pulse">
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[var(--color-action-primary)] opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-[var(--color-action-primary)]"></span>
                  </span>
                  <span className="text-xs text-interactive uppercase tracking-wider">
                    {activeJobs.length} {t('dashboard.activeJobs')}
                  </span>
                </div>
              )}
              <button
                onClick={() => {
                  void refetch();
                }}
                disabled={isRefetching}
                className={`p-2 rounded-xl border border-[var(--color-border-primary)] glass hover:bg-[var(--color-surface-hover)] transition-all ${isRefetching ? 'animate-spin opacity-50' : 'hover:scale-110 active:scale-95'}`}
                title={t('dashboard.refresh')}
              >
                <svg className="w-6 h-6 text-[var(--color-action-primary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              </button>
            </div>
          </div>

          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            <StatCard 
              label={t('dashboard.stats.activeTopics')} 
              value={data?.stats?.activeTopics} 
              icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 7h.01M7 11h.01M7 15h.01M11 7h.01M11 11h.01M11 15h.01M15 7h.01M15 11h.01M15 15h.01" /></svg>}
              description={t('dashboard.stats.activeTopicsDesc')}
            />
            <StatCard 
              label={t('dashboard.stats.weeklyBriefings')} 
              value={data?.stats?.briefingsGeneratedLast7Days} 
              icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>}
              description={t('dashboard.stats.weeklyBriefingsDesc')}
            />
            <StatCard 
              label={t('dashboard.stats.latestActivity')} 
              value={latestBriefingDate ? new Date(latestBriefingDate).toLocaleDateString(i18n.language) : undefined} 
              icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>}
              description={t('dashboard.stats.latestActivityDesc')}
            />
          </div>

          <div className="mt-12 bg-[var(--color-bg-secondary)]/30 p-8 rounded-xl md:rounded-[28px] border border-[var(--color-border-primary)]/50 backdrop-blur-sm">
            <RecentBriefingList briefings={data?.recentBriefings} />
          </div>
        </>
      )}
    </div>
  );
}
