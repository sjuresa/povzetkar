import { useGetApiV1Dashboard } from '../api/generated/dashboard/dashboard';
import { StatCard, StatCardSkeleton } from '../components/dashboard/StatCard';
import { EmptyState } from '../components/dashboard/EmptyState';
import { RecentBriefingList, BriefingListSkeleton } from '../components/dashboard/RecentBriefingList';
import type { DashboardDTO } from '../api/generated/model';

export function DashboardPage() {
  const { data: rawResponse, isLoading, isError, refetch, isRefetching } = useGetApiV1Dashboard({
    query: {
      refetchOnWindowFocus: true,
      staleTime: 30000, // 30 seconds
    }
  });

  // Handle the unwrapped payload from customInstance
  const data = rawResponse as unknown as DashboardDTO;

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
          onClick={() => void refetch()} 
          className="px-6 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-xl transition-colors"
        >
          Retry
        </button>
      </div>
    );
  }

  // Determine if we are in an empty state
  const hasNoData = !data?.stats?.activeTopics && (!data?.recentBriefings || data.recentBriefings.length === 0);

  if (hasNoData) {
    return <EmptyState />;
  }

  return (
    <div className="space-y-10 animate-in fade-in duration-700">
      <div className="flex items-center justify-between">
        <h2 className="text-4xl font-extrabold tracking-tight text-white text-glow">Dashboard</h2>
        <button
          onClick={() => void refetch()}
          disabled={isRefetching}
          className={`p-2 rounded-xl glass hover:bg-slate-800 transition-all ${isRefetching ? 'animate-spin opacity-50' : 'hover:scale-110 active:scale-95'}`}
          title="Refresh Dashboard"
        >
          <svg className="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <StatCard 
          label="Active Topics" 
          value={data.stats?.activeTopics} 
          icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 11h.01M7 15h.01M11 7h.01M11 11h.01M11 15h.01M15 7h.01M15 11h.01M15 15h.01" /></svg>}
          description="Monitoring your sources"
        />
        <StatCard 
          label="Weekly Briefings" 
          value={data.stats?.briefingsGeneratedLast7Days} 
          icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>}
          description="In the last 7 days"
        />
        <StatCard 
          label="Latest Activity" 
          value={data.recentBriefings?.[0]?.briefingDate?.toString()} 
          icon={<svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>}
          description="Last briefing generated"
        />
      </div>

      <div className="mt-12 bg-slate-900/30 p-8 rounded-3xl border border-slate-800/50 backdrop-blur-sm">
        <RecentBriefingList briefings={data.recentBriefings} />
      </div>
    </div>
  );
}
