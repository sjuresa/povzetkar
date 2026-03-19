import { useAuthStore } from '../store/authStore';
import { useTranslation } from 'react-i18next';
import { Shield, Mail, User, Fingerprint } from 'lucide-react';

export function ProfilePage() {
  const { user } = useAuthStore();
  const { t } = useTranslation();

  if (!user) return null;

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col space-y-2">
        <h2 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] text-glow">
          {t('profile.title')}
        </h2>
        <p className="text-[var(--color-text-secondary)] text-lg">
          {t('profile.description')}
        </p>
      </div>

      <div className="grid gap-8 md:grid-cols-3">
        {/* Main Identity Card */}
        <div className="md:col-span-2 space-y-6">
          <div className="bg-[var(--color-background-alt)] border border-[var(--color-border-subtle)] rounded-3xl overflow-hidden backdrop-blur-md shadow-lg">
            <div className="h-32 bg-gradient-to-r from-blue-600/10 to-indigo-600/10 flex items-center justify-center border-b border-[var(--color-border-subtle)]">
                <div className="w-20 h-20 rounded-full bg-[var(--color-surface-elevated)] border-4 border-[var(--color-background-base)] flex items-center justify-center text-3xl font-bold text-blue-500 shadow-xl translate-y-8">
                    {user.name?.charAt(0) || 'U'}
                </div>
            </div>
            
            <div className="p-10 pt-14 space-y-8">
              <div className="grid gap-8 sm:grid-cols-2">
                <div className="space-y-1">
                  <label className="text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] flex items-center gap-2">
                    <User className="w-3 h-3" /> {t('profile.name')}
                  </label>
                  <p className="text-xl font-semibold text-[var(--color-text-primary)]">{user.name}</p>
                </div>
                
                <div className="space-y-1">
                  <label className="text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] flex items-center gap-2">
                    <Mail className="w-3 h-3" /> {t('profile.email')}
                  </label>
                  <p className="text-xl font-semibold text-[var(--color-text-primary)]">{user.email}</p>
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] flex items-center gap-2">
                    <Fingerprint className="w-3 h-3" /> {t('profile.provider')}
                  </label>
                  <p className="text-lg font-medium text-[var(--color-text-secondary)] capitalize">
                    {user.authProvider || 'Standard'}
                  </p>
                </div>

                <div className="space-y-1">
                   <label className="text-xs font-bold uppercase tracking-widest text-[var(--color-text-secondary)] flex items-center gap-2">
                    <Shield className="w-3 h-3" /> {t('profile.roles')}
                  </label>
                  <div className="flex flex-wrap gap-2 pt-1">
                    {user.roles?.map(role => (
                      <span key={role.id} className="px-2.5 py-1 rounded-full bg-blue-500/10 border border-blue-500/30 text-blue-600 dark:text-blue-400 text-xs font-bold uppercase tracking-tighter">
                        {role.name}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Permissions Sidebar (Basic) */}
        <div className="space-y-6">
            <div className="bg-[var(--color-background-alt)]/30 border border-[var(--color-border-subtle)]/50 rounded-3xl p-8 backdrop-blur-sm">
                <h3 className="text-lg font-bold text-[var(--color-text-primary)] mb-6 flex items-center gap-2">
                    <Shield className="w-5 h-5 text-blue-500" />
                    {t('profile.permissions')}
                </h3>
                <div className="space-y-3">
                    {user.permissions?.map(permission => (
                        <div key={permission} className="flex items-center gap-3 text-sm text-[var(--color-text-secondary)]">
                             <div className="w-1.5 h-1.5 rounded-full bg-blue-600 dark:bg-blue-400 opacity-60"></div>
                             <span className="font-mono text-xs">{permission}</span>
                        </div>
                    ))}
                    {(!user.permissions || user.permissions.length === 0) && (
                        <p className="text-sm text-[var(--color-text-secondary)] italic">
                          {t('profile.no_permissions')}
                        </p>
                    )}
                </div>
            </div>
        </div>
      </div>
    </div>
  );
}
