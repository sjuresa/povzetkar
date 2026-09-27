import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AccountStatusBanner } from './AccountStatusBanner';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

describe('AccountStatusBanner', () => {
  it.each(['PENDING_APPROVAL', 'REJECTED', 'DISABLED'] as const)('%s banner has no buttons', (status) => {
    render(<AccountStatusBanner status={status} />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('pending banner explains the wait and links to the demo content', () => {
    render(<AccountStatusBanner status="PENDING_APPROVAL" />);

    expect(screen.getByText('account.pending.title')).toBeInTheDocument();
    expect(screen.getByText('account.pending.demo.title')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'account.pending.demo.topicsLink' })).toHaveAttribute('href', '/topics');
    expect(screen.getByRole('link', { name: 'account.pending.demo.briefingsLink' })).toHaveAttribute('href', '/briefings');
    expect(screen.getByText(/account\.pending\.demo\.topics$/)).toBeInTheDocument();
    expect(screen.getByText(/account\.pending\.demo\.briefings$/)).toBeInTheDocument();
    expect(screen.getByText('account.pending.demo.outro')).toBeInTheDocument();
  });

  it.each(['REJECTED', 'DISABLED'] as const)('%s banner shows its message without the demo hint', (status) => {
    const key = status === 'REJECTED' ? 'rejected' : 'disabled';
    render(<AccountStatusBanner status={status} />);

    expect(screen.getByText(`account.${key}.title`)).toBeInTheDocument();
    expect(screen.getByText(`account.${key}.message`)).toBeInTheDocument();
    expect(screen.queryByText('account.pending.demo.title')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
