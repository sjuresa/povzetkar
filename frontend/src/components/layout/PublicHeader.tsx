import type { ReactNode } from 'react';
import { Link } from '@tanstack/react-router';
import { BrandMark } from './BrandMark';
import { LanguageSwitcher } from './LanguageSwitcher';
import { ThemeToggle } from './ThemeToggle';

interface PublicHeaderProps {
  /** Short context line, e.g. "Shared summary"; hidden on narrow screens. */
  readonly subtitle?: string;
  /** Width and horizontal padding of the content row — pass the page's own container classes so the header lines up. */
  readonly containerClassName?: string;
  /** Page actions rendered after the toggles, e.g. the landing page's sign-in button. */
  readonly children?: ReactNode;
}

/** The header of every page outside the app shell: landing page and public shared views. */
export function PublicHeader({ subtitle, containerClassName = 'max-w-6xl px-4 sm:px-8', children }: PublicHeaderProps) {
  return (
    <header className="sticky top-0 z-40 border-b border-[var(--color-border-subtle)] bg-[var(--color-background-base)]/80 backdrop-blur-md py-3">
      <div className={`${containerClassName} mx-auto flex items-center justify-between gap-3`}>
        <Link to="/" className="group shrink-0" aria-label="Povzetkar" data-testid="brand-logo">
          <BrandMark compactOnMobile />
        </Link>
        <div className="flex items-center gap-2 sm:gap-3 min-w-0">
          {subtitle && (
            <p className="text-sm text-[var(--color-text-secondary)] hidden sm:block truncate">{subtitle}</p>
          )}
          <LanguageSwitcher />
          <ThemeToggle />
          {children}
        </div>
      </div>
    </header>
  );
}
