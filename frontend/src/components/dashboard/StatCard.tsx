import type { ReactNode } from 'react';

interface StatCardProps {
  label: string;
  value: string | number | undefined;
  icon: ReactNode;
  description?: string;
}

export function StatCard({ label, value, icon, description }: Readonly<StatCardProps>) {
  return (
    <div className="glass p-6 rounded-3xl relative overflow-hidden group transition-all duration-300 hover:border-[var(--color-accent-primary)]/30 hover:bg-[var(--color-surface-hover)] shadow-sm">
      <div className="flex items-start justify-between">
        <div className="z-10 relative">
          <p className="text-sm font-medium text-[var(--color-text-secondary)] mb-1">{label}</p>
          <p className="text-3xl font-bold text-[var(--color-text-primary)] text-glow tracking-tight">
            {value ?? '--'}
          </p>
          {description && (
            <p className="text-xs text-[var(--color-text-secondary)] mt-2 font-medium opacity-70">{description}</p>
          )}
        </div>
        <div className="p-3 bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] shadow-lg rounded-2xl text-[var(--color-accent-primary)] group-hover:scale-110 group-hover:bg-[var(--color-accent-primary-soft)] transition-all duration-300 flex items-center justify-center">
          <div className="filter drop-shadow-[0_0_8px_var(--color-accent-primary-soft)]">
            {icon}
          </div>
        </div>
      </div>
      
      {/* Decorative gradient blur */}
      <div className="absolute -right-4 -bottom-4 w-24 h-24 bg-[var(--color-accent-primary-soft)] rounded-full blur-2xl group-hover:bg-[var(--color-accent-primary)]/10 transition-colors"></div>
    </div>
  );
}

export function StatCardSkeleton() {
  return (
    <div className="glass p-6 rounded-2xl animate-pulse">
      <div className="flex items-start justify-between">
        <div className="space-y-3 flex-1">
          <div className="h-4 bg-[var(--color-surface-deep)] rounded w-24"></div>
          <div className="h-8 bg-[var(--color-surface-deep)] rounded w-16"></div>
          <div className="h-3 bg-[var(--color-surface-deep)] rounded w-32"></div>
        </div>
        <div className="w-12 h-12 bg-[var(--color-surface-deep)] rounded-xl"></div>
      </div>
    </div>
  );
}
