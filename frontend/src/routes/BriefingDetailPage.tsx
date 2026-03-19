import { Link } from '@tanstack/react-router';

export function BriefingDetailPage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="w-20 h-20 rounded-2xl bg-blue-600/10 flex items-center justify-center mb-4">
        <svg
          className="w-10 h-10 text-blue-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
          />
        </svg>
      </div>
      
      <div className="space-y-2">
        <h1 className="text-4xl font-bold tracking-tight text-white text-glow">
          Briefing Details
        </h1>
        <p className="text-slate-400 max-w-md mx-auto">
          We are currently polishing the AI visualization for detailed briefings. Check back soon for the full experience.
        </p>
      </div>

      <div className="flex items-center space-x-4">
        <Link
          to="/dashboard"
          className="px-6 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white transition-all border border-slate-700"
        >
          Back to Dashboard
        </Link>
      </div>
      
      {/* Decorative background element */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 bg-blue-600/5 rounded-full blur-[80px] -z-10"></div>
    </div>
  );
}
