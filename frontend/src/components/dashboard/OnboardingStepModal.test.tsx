import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { OnboardingStepModal } from './OnboardingStepModal';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, onClick }: { children: React.ReactNode; to: string; onClick?: () => void }) => (
    <a href={to} onClick={onClick}>{children}</a>
  ),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe('OnboardingStepModal', () => {
  it('renders nothing when step is null', () => {
    const { container } = render(<OnboardingStepModal step={null} onClose={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders modal with title, how-to steps, and CTA for topic step', () => {
    render(<OnboardingStepModal step="topic" onClose={vi.fn()} />);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('onboarding.step.topic.title')).toBeInTheDocument();
    expect(screen.getByText('onboarding.step.topic.how1')).toBeInTheDocument();
    expect(screen.getByText('onboarding.step.topic.how3')).toBeInTheDocument();
    // topic has 3 how-to steps, not 4
    expect(screen.queryByText('onboarding.step.topic.how4')).not.toBeInTheDocument();
  });

  it('renders 4 how-to steps for read step and routes CTA to /briefings', () => {
    render(<OnboardingStepModal step="read" onClose={vi.fn()} />);
    expect(screen.getByText('onboarding.step.read.how4')).toBeInTheDocument();
    const ctaLink = screen.getByRole('link', { name: 'onboarding.step.read.cta' });
    expect(ctaLink).toHaveAttribute('href', '/briefings');
  });

  it('calls onClose when close button is clicked', () => {
    const onClose = vi.fn();
    render(<OnboardingStepModal step="source" onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose when ESC key is pressed', () => {
    const onClose = vi.fn();
    render(<OnboardingStepModal step="trigger" onClose={onClose} />);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
