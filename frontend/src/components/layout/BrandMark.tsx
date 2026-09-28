interface BrandMarkProps {
  /** Hide the "Povzetkar" wordmark on narrow screens (keeps a crowded header row on one line). */
  readonly compactOnMobile?: boolean;
}

/** The rounded "P" tile plus the wordmark, used by the public header and the landing footer. */
export function BrandMark({ compactOnMobile = false }: BrandMarkProps) {
  return (
    <span className="flex items-center gap-2">
      <span className="w-8 h-8 rounded-xl bg-[var(--color-action-primary)] flex items-center justify-center font-bold text-[var(--color-action-primary-text)] group-hover:scale-105 transition-transform">
        P
      </span>
      <span className={`font-bold text-[var(--color-text-primary)] ${compactOnMobile ? 'hidden sm:inline' : ''}`}>
        Povzetkar
      </span>
    </span>
  );
}
