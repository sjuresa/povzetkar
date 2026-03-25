import type { HTMLAttributes } from 'react';
import { cn } from '../../util/cn';

/**
 * Standard Skeleton component for loading states.
 * Uses pulse animation and semantic styling.
 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('animate-pulse rounded-xl bg-[var(--color-surface-deep)]/40 border border-[var(--color-border-subtle)]/30', className)}
      {...props}
    />
  );
}
