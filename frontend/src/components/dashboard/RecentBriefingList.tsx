import { Link } from '@tanstack/react-router';
import type { BriefingSummaryDTO } from '../../api/generated/model';

interface RecentBriefingListProps {
  briefings: BriefingSummaryDTO[] | undefined;
}

export function RecentBriefingList({ briefings }: RecentBriefingListProps) {
  if (!briefings || briefings.length === 0) return null;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between px-2">
        <h3 className="text-lg font-bold text-slate-100 italic">Recent Briefings</h3>
        <Link 
          to="/briefings" 
          className="text-sm font-medium text-blue-400 hover:text-blue-300 transition-colors"
        >
          View all
        </Link>
      </div>

      <div className="grid gap-4">
        {briefings.map((briefing) => (
          <Link
            key={briefing.id}
            to="/briefings/$briefingId"
            params={{ briefingId: briefing.id?.toString() || '' }}
            className="glass-elevated p-5 rounded-2xl flex items-center justify-between group transition-all hover:bg-slate-800/80 hover:border-slate-600 border-transparent border"
          >
            <div className="flex items-center space-x-5">
              <div className="w-12 h-12 rounded-xl bg-slate-900 border border-slate-700 flex items-center justify-center text-slate-400 group-hover:text-blue-400 group-hover:bg-blue-600/10 transition-colors">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-100 group-hover:text-white mb-0.5">{briefing.title}</p>
                <div className="flex items-center space-x-2 text-xs text-slate-500">
                  <span className="font-medium text-slate-400">{briefing.topicName}</span>
                  <span>•</span>
                  <span>{briefing.briefingDate?.toString() || 'Recently'}</span>
                </div>
              </div>
            </div>
            
            <div className="pr-2 opacity-0 group-hover:opacity-100 translate-x-2 group-hover:translate-x-0 transition-all text-blue-400">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function BriefingListSkeleton() {
  return (
    <div className="space-y-4 animate-pulse">
       <div className="flex items-center justify-between px-2">
        <div className="h-5 bg-slate-800 rounded w-32"></div>
        <div className="h-4 bg-slate-800 rounded w-16"></div>
      </div>
      {[1, 2, 3].map((i) => (
        <div key={i} className="glass p-5 rounded-2xl flex items-center space-x-5 border border-transparent">
          <div className="w-12 h-12 rounded-xl bg-slate-800"></div>
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-slate-800 rounded w-2/3"></div>
            <div className="h-3 bg-slate-800 rounded w-1/3"></div>
          </div>
        </div>
      ))}
    </div>
  );
}
