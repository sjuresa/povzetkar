import type { ReactNode } from 'react';
import { useState } from 'react';
import { useAuthStore } from '../../store/authStore';
import { useShallow } from 'zustand/react/shallow';
import { Link, useLocation } from '@tanstack/react-router';
import { Toaster } from 'sonner';
import { useJobEvents } from '../../hooks/useJobEvents';
import { useEventStore } from '../../store/eventStore';
import { useTranslation } from 'react-i18next';
import { ThemeToggle } from './ThemeToggle';
import { LanguageSwitcher } from './LanguageSwitcher';
import { useThemeStore } from '../../store/themeStore';
import { SystemMetadata } from '../system/SystemMetadata';
import { useSystemMetadata } from '../../hooks/useSystemMetadata';
import { useAudioFeedback } from '../../hooks/useAudioFeedback';

interface AppShellProps {
  readonly children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  // Initialize SSE event stream for jobs
  useJobEvents();

  // Initialize Auditory Feedback
  useAudioFeedback();

  const [isMetadataOpen, setIsMetadataOpen] = useState(false);

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
  const { t } = useTranslation();
  const { theme } = useThemeStore();

  const { data: metadata } = useSystemMetadata();

  const getStatusColor = () => {
    if (connectionStatus === 'connected') return 'bg-emerald-500';
    if (connectionStatus === 'connecting') return 'bg-amber-500 animate-pulse';
    return 'bg-red-500';
  };

  const navItems = [
    { name: t('navigation.dashboard'), path: '/dashboard', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
    { name: t('navigation.briefings'), path: '/briefings', icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z' },
    { name: t('navigation.topics'), path: '/topics', icon: 'M7 7h.01M7 11h.01M7 15h.01M11 7h.01M11 11h.01M11 15h.01M15 7h.01M15 11h.01M15 15h.01' },
    { name: t('navigation.sources'), path: '/sources', icon: 'M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10' },
  ];

  const adminNavItems = [
    { name: t('navigation.auditLogs'), path: '/admin/audit-log', icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01' },
    { name: t('navigation.contentManagement'), path: '/admin/content', icon: 'M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10' },
    { name: t('navigation.systemJobs'), path: '/admin/jobs', icon: 'M13 10V3L4 14h7v7l9-11h-7z' },
  ];

  const hasAdminPermission = user?.permissions?.some(p => 
    ['ADMIN_AUDIT_LOG_VIEW', 'ADMIN_DASHBOARD_VIEW'].includes(p)
  );

  return (
    <div className="flex h-screen bg-[var(--color-bg-primary)] text-[var(--color-text-primary)] antialiased overflow-hidden">
      {/* Sidebar */}
      <aside
        className={`${
          sidebarCollapsed ? 'w-20' : 'w-64'
        } transition-all duration-300 ease-in-out bg-[var(--color-bg-secondary)] border-r border-[var(--color-border-primary)] flex flex-col z-50`}
      >
        <Link to="/dashboard" className="h-16 flex items-center px-6 border-b border-[var(--color-border-primary)] outline-none group hover:bg-[var(--color-surface-deep)]/30 transition-colors" aria-label="Povzetkar Dashboard">
          <div className="w-8 h-8 rounded-lg bg-[var(--color-accent-primary)] flex-shrink-0 flex items-center justify-center font-bold text-white shadow-lg group-hover:scale-105 transition-transform">
            P
          </div>
          {!sidebarCollapsed && (
            <span className="ml-3 font-bold text-xl tracking-tight text-[var(--color-text-primary)] animate-in fade-in duration-500 group-hover:text-[var(--color-accent-primary)] transition-colors">
              Povzetkar
            </span>
          )}
        </Link>

        <nav className="flex-1 overflow-y-auto py-6 px-3 space-y-1">
          {navItems.map((item) => {
            const isActive = location.pathname.startsWith(item.path);
            return (
              <Link
                key={item.name}
                to={item.path}
                className={`flex items-center px-3 py-3 rounded-lg transition-colors group relative text-interactive ${
                  isActive
                    ? 'bg-[var(--color-accent-primary-soft)] text-[var(--color-accent-primary)]'
                    : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-deep)] hover:text-[var(--color-text-primary)]'
                }`}
              >
                <svg
                  className={`w-6 h-6 flex-shrink-0 ${isActive ? 'text-[var(--color-accent-primary)]' : 'text-[var(--color-text-secondary)] group-hover:text-[var(--color-text-primary)]'}`}
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
                   <div className="absolute left-full ml-4 px-2 py-1 bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] text-[var(--color-text-primary)] text-xs rounded opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-[100] shadow-xl">
                    {item.name}
                   </div>
                )}
              </Link>
            );
          })}

          {hasAdminPermission && (
            <div className="pt-6 mt-6 border-t border-[var(--color-border-subtle)]">
              {!sidebarCollapsed && (
                <p className="px-3 mb-2 text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">
                  {t('navigation.administration')}
                </p>
              )}
              {adminNavItems.map((item) => {
                const isActive = location.pathname.startsWith(item.path);
                return (
                  <Link
                    key={item.name}
                    to={item.path}
                    className={`flex items-center px-3 py-3 rounded-lg transition-colors group relative text-interactive ${
                      isActive
                        ? 'bg-[var(--color-accent-admin-soft)] text-[var(--color-accent-admin)]'
                        : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-deep)] hover:text-[var(--color-text-primary)]'
                    }`}
                  >
                    <svg
                      className={`w-6 h-6 flex-shrink-0 ${isActive ? 'text-[var(--color-accent-admin)]' : 'text-[var(--color-text-secondary)] group-hover:text-[var(--color-text-primary)]'}`}
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
                       <div className="absolute left-full ml-4 px-2 py-1 bg-[var(--color-surface-elevated)] border border-[var(--color-border-primary)] text-[var(--color-text-primary)] text-xs rounded opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-[100] shadow-xl">
                        {item.name}
                       </div>
                    )}
                  </Link>
                );
              })}
            </div>
          )}
        </nav>

        <div className="p-4 border-t border-[var(--color-border-primary)] space-y-2">
          <button
            onClick={() => setIsMetadataOpen(true)}
            className={`w-full h-10 flex items-center px-3 rounded-lg bg-[var(--color-surface-deep)] border border-[var(--color-border-primary)] hover:border-[var(--color-accent-primary)] transition-all group overflow-hidden ${sidebarCollapsed ? 'justify-center' : ''}`}
          >
            <div className="w-2 h-2 rounded-full bg-emerald-500 mr-2 flex-shrink-0 animate-pulse"></div>
            {!sidebarCollapsed && (
              <span className="text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] group-hover:text-[var(--color-accent)] transition-colors">
                {metadata?.version}
              </span>
            )}
          </button>
          <button
            onClick={toggleSidebar}
            className="w-full h-10 flex items-center justify-center rounded-lg bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
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
        <header className="h-16 flex items-center justify-between px-8 bg-[var(--color-bg-secondary)]/50 border-b border-[var(--color-border-primary)] backdrop-blur-md z-40">
          <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2 px-3 py-1 rounded-full bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)]" title={`Real-time Stream: ${connectionStatus}`}>
                <span className="relative flex h-2 w-2">
                  {connectionStatus === 'connected' && (
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                  )}
                  <span className={`relative inline-flex rounded-full h-2 w-2 ${getStatusColor()}`}></span>
                </span>
                <span className="text-[10px] font-bold uppercase tracking-tighter text-[var(--color-text-secondary)]">Live</span>
              </div>
          </div>

          <div className="flex items-center space-x-6">
            <div className="flex items-center space-x-4 pr-6 border-r border-[var(--color-border-primary)]">
               <LanguageSwitcher />
               <ThemeToggle />
            </div>

            <Link 
              to="/me"
              className="flex items-center space-x-3 pr-6 border-r border-[var(--color-border-primary)] group hover:bg-[var(--color-surface-deep)]/50 p-1.5 rounded-xl transition-all"
            >
               <div className="text-right hidden sm:block">
                 <p className="text-sm text-interactive text-[var(--color-text-primary)] leading-tight group-hover:text-[var(--color-accent-primary)] transition-colors">{user?.name || 'User'}</p>
                 <p className="text-xs text-[var(--color-text-secondary)] leading-tight">{user?.email}</p>
               </div>
               <div className="w-9 h-9 rounded-full bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] flex items-center justify-center text-interactive text-[var(--color-text-secondary)] group-hover:border-[var(--color-accent-primary)] group-hover:text-[var(--color-accent-primary)] transition-all">
                 {user?.name?.charAt(0) || 'U'}
               </div>
            </Link>
            <button
               onClick={logout}
               className="px-4 py-2 text-sm text-interactive text-[var(--color-text-secondary)] hover:text-[var(--color-accent-primary)] transition-colors"
            >
               {t('common.logout')}
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

      <SystemMetadata isOpen={isMetadataOpen} onClose={() => setIsMetadataOpen(false)} />
      <Toaster 
        position="top-right" 
        expand={true} 
        richColors 
        closeButton 
        theme={theme as 'light' | 'dark'}
        toastOptions={{
          style: {
            background: 'var(--color-surface-elevated)',
            border: '1px solid var(--color-border-subtle)',
            color: 'var(--color-text-primary)',
          },
        }}
      />
    </div>
  );
}
