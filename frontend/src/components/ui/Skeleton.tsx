import { type HTMLAttributes } from 'react';

/**
 * Generic Skeleton component for polished loading states.
 * Uses standard Tailwind animation.
 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`animate-pulse rounded-xl bg-slate-800/40 border border-slate-700/30 ${className}`}
      {...props}
    />
  );
}
