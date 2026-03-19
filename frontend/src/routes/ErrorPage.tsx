/**
 * Professional-grade Error Page for use with TanStack Router's defaultErrorComponent
 * or as a fallback for the global ErrorBoundary.
 * 
 * NOTE: Use standard <a> instead of <Link> to avoid requiring Router context
 * when rendered as a fallback.
 */
export function ErrorPage() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-50 flex flex-col items-center justify-center p-6 text-center animate-in fade-in duration-1000">
      <div className="max-w-[70ch] space-y-12">
        {/* Visual indicator of failure */}
        <div className="flex justify-center flex-col items-center">
          <div className="w-24 h-24 rounded-full bg-red-500/10 flex items-center justify-center text-red-500 border border-red-500/20 mb-6 group transition-all duration-300 hover:scale-110 shadow-lg shadow-red-900/10">
            <svg className="w-12 h-12" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <h1 className="text-[clamp(1.5rem,5vw,3rem)] font-extrabold tracking-tight text-white mb-4 drop-shadow-sm">
            A glitch in the matrix
          </h1>
          <p className="text-[clamp(1rem,2.5vw,1.25rem)] text-slate-400 leading-relaxed max-w-[45ch] mx-auto">
            Something went wrong while loading this page. Our team has been notified.
          </p>
        </div>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-6 pt-4">
          <a
            href="/dashboard"
            className="px-10 py-5 bg-blue-600 hover:bg-blue-500 text-white rounded-full font-bold text-lg transition-all transform hover:scale-105 shadow-[0_0_30px_rgba(37,99,235,0.4)] active:scale-95 flex items-center gap-2"
          >
            <span>Go to Dashboard</span>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" /></svg>
          </a>
          <button
            onClick={() => window.location.reload()}
            className="px-8 py-4 bg-slate-900 hover:bg-slate-800 text-slate-300 rounded-full font-semibold text-lg border border-slate-800 transition-all active:scale-95"
          >
            Try Refreshing
          </button>
        </div>
      </div>

      {/* Decorative background elements consistent with Landing Page */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none -z-10">
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-red-900/5 rounded-full blur-[120px] animate-pulse"></div>
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-blue-900/5 rounded-full blur-[120px] animate-pulse delay-1000"></div>
      </div>
    </div>
  );
}
