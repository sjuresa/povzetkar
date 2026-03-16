import type { ReactNode } from 'react';

interface StatCardProps {
  label: string;
  value: string | number | undefined;
  icon: ReactNode;
  description?: string;
}

export function StatCard({ label, value, icon, description }: StatCardProps) {
  return (
    <div className="glass p-6 rounded-2xl relative overflow-hidden group transition-all duration-300 hover:border-slate-700 hover:bg-slate-900/60">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-medium text-slate-400 mb-1">{label}</p>
          <p className="text-3xl font-bold text-slate-100 text-glow tracking-tight">
            {value ?? '--'}
          </p>
          {description && (
            <p className="text-xs text-slate-500 mt-2 font-medium">{description}</p>
          )}
        </div>
        <div className="p-3 bg-blue-600/10 rounded-xl text-blue-500 group-hover:scale-110 transition-transform duration-300">
          {icon}
        </div>
      </div>
      
      {/* Decorative gradient blur */}
      <div className="absolute -right-4 -bottom-4 w-24 h-24 bg-blue-600/5 rounded-full blur-2xl group-hover:bg-blue-600/10 transition-colors"></div>
    </div>
  );
}

export function StatCardSkeleton() {
  return (
    <div className="glass p-6 rounded-2xl animate-pulse">
      <div className="flex items-start justify-between">
        <div className="space-y-3 flex-1">
          <div className="h-4 bg-slate-800 rounded w-24"></div>
          <div className="h-8 bg-slate-800 rounded w-16"></div>
          <div className="h-3 bg-slate-800 rounded w-32"></div>
        </div>
        <div className="w-12 h-12 bg-slate-800 rounded-xl"></div>
      </div>
    </div>
  );
}
