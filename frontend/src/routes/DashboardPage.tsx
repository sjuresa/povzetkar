export function DashboardPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-3xl font-bold tracking-tight text-slate-100">Dashboard</h2>
      </div>
      
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4">
        <div className="p-6 bg-slate-900/50 rounded-xl border border-slate-800 backdrop-blur-sm">
          <p className="text-sm font-medium text-slate-400">Active Topics</p>
          <p className="text-2xl font-bold text-slate-100">12</p>
        </div>
        <div className="p-6 bg-slate-900/50 rounded-xl border border-slate-800 backdrop-blur-sm">
          <p className="text-sm font-medium text-slate-400">Weekly Summaries</p>
          <p className="text-2xl font-bold text-slate-100">48</p>
        </div>
        <div className="p-6 bg-slate-900/50 rounded-xl border border-slate-800 backdrop-blur-sm">
          <p className="text-sm font-medium text-slate-400">Latest Briefing</p>
          <p className="text-2xl font-bold text-slate-100">Today</p>
        </div>
        <div className="p-6 bg-slate-900/50 rounded-xl border border-slate-800 backdrop-blur-sm">
          <p className="text-sm font-medium text-slate-400">Total Savings</p>
          <p className="text-2xl font-bold text-emerald-400">~6.4h</p>
        </div>
      </div>
      
      <div className="p-8 bg-slate-900/50 rounded-2xl border border-slate-800 backdrop-blur-md min-h-[400px] flex items-center justify-center text-slate-500 italic">
        Select a topic or visit "Your Latest Briefing" to begin.
      </div>
    </div>
  );
}
