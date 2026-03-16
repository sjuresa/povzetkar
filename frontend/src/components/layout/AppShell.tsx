import type { ReactNode } from 'react';
import { useAuthStore } from '../../store/authStore';
import { useShallow } from 'zustand/react/shallow';
import { Link, useLocation } from '@tanstack/react-router';
import { Toaster } from '../ui/Toaster';
import { useJobEvents } from '../../hooks/useJobEvents';
import { useEventStore } from '../../store/eventStore';

interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  // Initialize SSE event stream for jobs
  useJobEvents();

  const { user, logout, sidebarCollapsed, toggleSidebar } = useAuthStore(
    useShallow((state) => ({
      user: state.user,
      logout: state.logout,
      sidebarCollapsed: state.sidebarCollapsed,
      toggleSidebar: state.toggleSidebar,
    }))
  );

  const connectionStatus = useEventStore((state) => state.connectionStatus);
  const location = useLocation();

  const navItems = [
    { name: 'Dashboard', path: '/dashboard', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
    { name: 'Topic Briefings', path: '/briefings', icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z' },
    { name: 'Topics', path: '/topics', icon: 'M7 7h.01M7 11h.01M7 15h.01M11 7h.01M11 11h.01M11 15h.01M15 7h.01M15 11h.01M15 15h.01' },
    { name: 'Sources', path: '/sources', icon: 'M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10' },
  ];

  return (
    <div className="flex h-screen bg-slate-950 text-slate-100 antialiased overflow-hidden">
      {/* Sidebar */}
      <aside
        className={`${
          sidebarCollapsed ? 'w-20' : 'w-64'
        } transition-all duration-300 ease-in-out bg-slate-900 border-r border-slate-800 flex flex-col z-50`}
      >
        <div className="h-16 flex items-center px-6 border-b border-slate-800">
          <div className="w-8 h-8 rounded-lg bg-blue-600 flex-shrink-0 flex items-center justify-center font-bold text-white shadow-lg shadow-blue-900/40">
            P
          </div>
          {!sidebarCollapsed && (
            <span className="ml-3 font-bold text-xl tracking-tight text-slate-100 animate-in fade-in duration-500">
              Povzetkar
            </span>
          )}
        </div>

        <nav className="flex-1 overflow-y-auto py-6 px-3 space-y-1">
          {navItems.map((item) => {
            const isActive = location.pathname.startsWith(item.path);
            return (
              <Link
                key={item.name}
                to={item.path}
                className={`flex items-center px-3 py-3 rounded-lg transition-colors group relative ${
                  isActive
                    ? 'bg-blue-600/10 text-blue-400'
                    : 'text-slate-400 hover:bg-slate-800 hover:text-slate-100'
                }`}
              >
                <svg
                  className={`w-6 h-6 flex-shrink-0 ${isActive ? 'text-blue-500' : 'text-slate-500 group-hover:text-slate-300'}`}
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={item.icon} />
                </svg>
                {!sidebarCollapsed && (
                  <span className="ml-3 font-medium transition-opacity duration-300">
                    {item.name}
                  </span>
                )}
                {sidebarCollapsed && (
                   <div className="absolute left-full ml-4 px-2 py-1 bg-slate-800 text-white text-xs rounded opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-[100]">
                    {item.name}
                   </div>
                )}
              </Link>
            );
          })}
        </nav>

        <div className="p-4 border-t border-slate-800">
          <button
            onClick={toggleSidebar}
            className="w-full h-10 flex items-center justify-center rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 transition-colors"
          >
            <svg
              className={`w-5 h-5 transition-transform duration-300 ${sidebarCollapsed ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
            </svg>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Header */}
        <header className="h-16 flex items-center justify-between px-8 bg-slate-900/50 border-b border-slate-800 backdrop-blur-md z-40">
          <div className="flex items-center space-x-4">
             {/* Connection Status Indicator */}
             <div className="flex items-center space-x-2 px-3 py-1 rounded-full bg-slate-800/50 border border-slate-700/50" title={`Real-time Stream: ${connectionStatus}`}>
               <span className="relative flex h-2 w-2">
                 {connectionStatus === 'connected' && (
                   <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                 )}
                 <span className={`relative inline-flex rounded-full h-2 w-2 ${
                   connectionStatus === 'connected' ? 'bg-emerald-500' : 
                   connectionStatus === 'connecting' ? 'bg-amber-500 animate-pulse' : 'bg-red-500'
                 }`}></span>
               </span>
               <span className="text-[10px] font-bold uppercase tracking-tighter text-slate-500">Live</span>
             </div>
          </div>

          <div className="flex items-center space-x-6">
            <div className="flex items-center space-x-3 pr-6 border-r border-slate-800">
               <div className="text-right hidden sm:block">
                 <p className="text-sm font-semibold text-slate-100 leading-tight">{user?.name || 'User'}</p>
                 <p className="text-xs text-slate-400 leading-tight">{user?.email}</p>
               </div>
               <div className="w-9 h-9 rounded-full bg-slate-700 border border-slate-600 flex items-center justify-center font-semibold text-slate-300">
                 {user?.name?.charAt(0) || 'U'}
               </div>
            </div>
            <button
              onClick={logout}
              className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white transition-colors"
            >
              Logout
            </button>
          </div>
        </header>

        {/* Scrollable Page Area */}
        <main className="flex-1 overflow-y-auto p-8 relative">
          <div className="max-w-7xl mx-auto">
            {children}
          </div>
          
          {/* Subtle decoration */}
          <div className="fixed bottom-0 right-0 w-1/2 h-1/2 bg-blue-900/5 rounded-full blur-[120px] pointer-events-none -z-10 translate-x-1/4 translate-y-1/4"></div>
        </main>
      </div>

      <Toaster />
    </div>
  );
}
