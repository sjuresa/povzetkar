import { useGetApiV1Dashboard } from '../api/generated/dashboard/dashboard';
import { StatCard, StatCardSkeleton } from '../components/dashboard/StatCard';
import type { DashboardDTO } from '../api/generated/model';
import { EmptyState } from '../components/dashboard/EmptyState';
import { RecentBriefingList, BriefingListSkeleton } from '../components/dashboard/RecentBriefingList';
import { useEventStore } from '../store/eventStore';
import { useShallow } from 'zustand/react/shallow';
import { usePostApiV1EventsTestPulse } from '../api/generated/events/events';

export function DashboardPage() {
  const activeJobs = useEventStore(
    useShallow((state) => 
      Object.values(state.jobs).filter(j => j.status === 'RUNNING' || j.status === 'QUEUED')
    )
  );
  const { data: response, isLoading, isError, refetch, isRefetching } = useGetApiV1Dashboard({
    query: {
      refetchOnWindowFocus: true,
      staleTime: 30000, // 30 seconds
    }
  });

  const { mutate: triggerTestPulse, isPending: isTestingPulse } = usePostApiV1EventsTestPulse();

  const data = response as DashboardDTO | undefined;

  if (isLoading) {
    return (
      <div className="space-y-10">
        <div className="flex items-center justify-between">
          <div className="h-9 bg-slate-800 rounded w-48 animate-pulse"></div>
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

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-4">
        <div className="bg-red-500/10 p-4 rounded-full text-red-500 mb-2">
          <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>
        <h3 className="text-xl font-bold text-white">Oops! Something went wrong</h3>
        <p className="text-slate-400">Failed to load dashboard data. Please try again.</p>
        <button 
          onClick={() => {
             
            void refetch();
          }} 
          className="px-6 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-xl transition-colors"
        >
          Retry
        </button>
      </div>
    );
  }

  // Determine if we are in an empty state
  const hasNoData = !data?.stats?.activeTopics && (!data?.recentBriefings || data.recentBriefings.length === 0);

  const latestBriefingDate = data?.recentBriefings?.[0]?.briefingDate;

  return (
    <div className="space-y-10 animate-in fade-in duration-700">
      <div className="flex items-center justify-between">
        <h2 className="text-4xl font-extrabold tracking-tight text-white text-glow">Dashboard</h2>
        <div className="flex items-center space-x-4">
          {activeJobs.length > 0 && (
            <div className="flex items-center space-x-2 bg-blue-500/10 text-blue-400 px-3 py-1.5 rounded-full border border-blue-500/20 animate-pulse">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500"></span>
              </span>
              <span className="text-xs font-medium uppercase tracking-wider">{activeJobs.length} active job(s)</span>
            </div>
          )}
          <button
            onClick={() => triggerTestPulse()}
            disabled={isTestingPulse || activeJobs.length > 0}
            className={`px-4 py-2 rounded-xl glass hover:bg-slate-800 transition-all flex items-center space-x-2 ${isTestingPulse ? 'opacity-50 cursor-not-allowed' : 'hover:scale-105 active:scale-95'}`}
            title="Test real-time feedback"
          >
            <div className={`w-2 h-2 rounded-full ${isTestingPulse ? 'bg-amber-500 animate-pulse' : 'bg-blue-500'}`}></div>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-300">Test Pulse</span>
          </button>
          <button
            onClick={() => {
              void refetch();
            }}
            disabled={isRefetching}
            className={`p-2 rounded-xl glass hover:bg-slate-800 transition-all ${isRefetching ? 'animate-spin opacity-50' : 'hover:scale-110 active:scale-95'}`}
            title="Refresh Dashboard"
          >
            <svg className="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {hasNoData ? (
        <EmptyState />
      ) : (
        <>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            <StatCard 
              label="Active Topics" 
              value={data?.stats?.activeTopics} 
              icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 11h.01M7 15h.01M11 7h.01M11 11h.01M11 15h.01M15 7h.01M15 11h.01M15 15h.01" /></svg>}
              description="Monitoring your sources"
            />
            <StatCard 
              label="Weekly Briefings" 
              value={data?.stats?.briefingsGeneratedLast7Days} 
              icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>}
              description="In the last 7 days"
            />
            <StatCard 
              label="Latest Activity" 
              value={latestBriefingDate ? new Date(latestBriefingDate).toLocaleDateString() : undefined} 
              icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>}
              description="Last briefing generated"
            />
          </div>

          <div className="mt-12 bg-slate-900/30 p-8 rounded-3xl border border-slate-800/50 backdrop-blur-sm">
            <RecentBriefingList briefings={data?.recentBriefings} />
          </div>
        </>
      )}
    </div>
  );
}
