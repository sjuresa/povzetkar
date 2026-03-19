import type { HTMLAttributes } from 'react';
import { cn } from '../../util/cn';

/**
 * Standard Skeleton component for loading states.
 * Uses pulse animation and glassmorphic styling consistent with the Dashboard.
 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('animate-pulse rounded-xl bg-slate-800/40 border border-slate-700/30', className)}
      {...props}
    />
  );
}
