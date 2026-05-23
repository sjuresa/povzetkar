import { cn } from '../../util/cn';

interface PaginationProps {
  readonly page: number;
  readonly totalPages: number;
  readonly onPageChange: (page: number) => void;
  readonly isLoading?: boolean;
  readonly className?: string;
}

/**
 * Shared Pagination component for consistent navigation across tables.
 * Extracted from AuditLogPage.tsx as part of POV-148.
 */
export function Pagination({
  page,
  totalPages,
  onPageChange,
  isLoading,
  className,
}: PaginationProps) {
  return (
    <div className={cn("flex items-center justify-between pt-4", className)}>
      <p className="text-xs text-[var(--color-text-secondary)] italic">
        Showing page <span className="text-[var(--color-text-primary)] font-bold">{page + 1}</span> of <span className="text-[var(--color-text-primary)] font-bold">{totalPages || 1}</span>
      </p>
      
      <div className="flex items-center space-x-2">
        <button
          disabled={page === 0 || isLoading}
          onClick={() => onPageChange(page - 1)}
          className="px-4 py-2 rounded-xl border border-[var(--color-border-subtle)] glass hover:bg-[var(--color-surface-hover)] disabled:opacity-30 transition-all hover:scale-105 active:scale-95 text-xs font-bold uppercase tracking-widest text-[var(--color-text-primary)] cursor-pointer"
        >
          Previous
        </button>
        <div className="w-px h-6 bg-[var(--color-border-subtle)] mx-2"></div>
        <button
          disabled={page >= totalPages - 1 || isLoading}
          onClick={() => onPageChange(page + 1)}
          className="px-4 py-2 rounded-xl border border-[var(--color-border-subtle)] glass hover:bg-[var(--color-surface-hover)] disabled:opacity-30 transition-all hover:scale-105 active:scale-95 text-xs font-bold uppercase tracking-widest text-[var(--color-text-primary)] cursor-pointer"
        >
          Next
        </button>
      </div>
    </div>
  );
}
