import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { LogIn } from 'lucide-react';
import { PublicHeader } from '../layout/PublicHeader';

interface LandingNavProps {
  readonly isAuthenticated: boolean;
  readonly onLogin: () => void;
}

export function LandingNav({ isAuthenticated, onLogin }: LandingNavProps) {
  const { t } = useTranslation();

  return (
    <PublicHeader>
      {isAuthenticated ? (
        <Link
          to="/dashboard"
          className="px-4 py-2 rounded-xl bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] text-[var(--color-action-primary-text)] text-sm font-semibold transition-colors whitespace-nowrap"
        >
          {t('navigation.dashboard')}
        </Link>
      ) : (
        <button
          onClick={onLogin}
          data-testid="nav-login"
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] text-[var(--color-action-primary-text)] text-sm font-semibold transition-colors whitespace-nowrap"
        >
          <LogIn className="w-4 h-4" strokeWidth={1.5} />
          {t('landing.nav.sign_in')}
        </button>
      )}
    </PublicHeader>
  );
}
