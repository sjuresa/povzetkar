import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import type { ReactNode } from 'react';
import { PublicHeader } from './PublicHeader';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, ...rest }: { children: ReactNode; to: string }) => <a href={to} {...rest}>{children}</a>,
}));

describe('PublicHeader', () => {
  it('links the logo to the landing page', () => {
    render(<PublicHeader />);

    expect(screen.getByTestId('brand-logo')).toHaveAttribute('href', '/');
  });

  it('offers the language and theme toggles', () => {
    render(<PublicHeader />);

    // Accessible names come from the toggles' own labels (the i18n mock returns keys).
    expect(screen.getByRole('button', { name: /English|Slovenščina/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /common\.theme/ })).toBeInTheDocument();
  });

  it('renders the subtitle and page actions', () => {
    render(
      <PublicHeader subtitle="Shared summary">
        <button>Sign in</button>
      </PublicHeader>
    );

    expect(screen.getByText('Shared summary')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });
});
