import { Link } from '@tanstack/react-router';
import { useAuthStore } from '../store/authStore';
import { useShallow } from 'zustand/react/shallow';

export function LandingPage() {
  const { login, isAuthenticated } = useAuthStore(
    useShallow((state) => ({
      login: state.login,
      isAuthenticated: state.isAuthenticated,
    }))
  );

  return (
    <div className="min-h-screen bg-slate-950 text-slate-50 flex flex-col items-center justify-center p-6 text-center">
      <div className="max-w-3xl space-y-8">
        <h1 className="text-5xl md:text-7xl font-bold tracking-tight bg-gradient-to-r from-blue-400 to-emerald-400 bg-clip-text text-transparent">
          Povzetkar
        </h1>
        <p className="text-xl md:text-2xl text-slate-400 leading-relaxed">
          Your daily AI-powered content briefing. <br />
          Stay informed without the noise.
        </p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-4">
          {isAuthenticated ? (
            <Link
              to="/dashboard"
              className="px-8 py-4 bg-emerald-600 hover:bg-emerald-500 text-white rounded-full font-semibold text-lg transition-all transform hover:scale-105 shadow-lg shadow-emerald-900/40 active:scale-95"
            >
              Go to Dashboard
            </Link>
          ) : (
            <button
              onClick={login}
              className="px-8 py-4 bg-blue-600 hover:bg-blue-500 text-white rounded-full font-semibold text-lg transition-all transform hover:scale-105 shadow-lg shadow-blue-900/40 active:scale-95"
            >
              Start Your Briefing
            </button>
          )}
          <a
            href="#features"
            className="px-8 py-4 bg-slate-900 hover:bg-slate-800 text-slate-300 rounded-full font-semibold text-lg border border-slate-800 transition-all active:scale-95"
          >
            Learn More
          </a>
        </div>
      </div>
      
      {/* Decorative background elements */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none -z-10">
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-blue-900/10 rounded-full blur-3xl animate-pulse"></div>
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-emerald-900/10 rounded-full blur-3xl animate-pulse delay-1000"></div>
      </div>
    </div>
  );
}
