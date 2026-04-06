import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { OnboardingChecklist } from './OnboardingChecklist';
import { useOnboardingStore } from '../../store/onboardingStore';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, id }: { children: React.ReactNode; to: string; id?: string }) => (
    <a href={to} id={id}>{children}</a>
  ),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, opts?: Record<string, unknown>) => opts ? `${key}:${JSON.stringify(opts)}` : key }),
}));

vi.mock('../../store/onboardingStore', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../store/onboardingStore')>();
  return { ...actual };
});

function resetStore(overrides: Partial<ReturnType<typeof useOnboardingStore.getState>> = {}) {
  useOnboardingStore.setState({
    steps: { topic: false, source: false, trigger: false, read: false, share: false },
    dismissed: false,
    collapsed: false,
    ...overrides,
  });
}

describe('OnboardingChecklist', () => {
  beforeEach(() => {
    resetStore();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders nothing when dismissed', () => {
    resetStore({ dismissed: true });
    const { container } = render(<OnboardingChecklist />);
    expect(container.firstChild).toBeNull();
  });

  it('renders collapsed pill when collapsed is true', () => {
    resetStore({ collapsed: true });
    render(<OnboardingChecklist />);
    expect(screen.getByRole('button', { name: /onboarding\.expand_label/i })).toBeInTheDocument();
    expect(screen.queryByText('onboarding.title')).not.toBeInTheDocument();
  });

  it('renders expanded checklist when not collapsed', () => {
    render(<OnboardingChecklist />);
    expect(screen.getByText('onboarding.title')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0');
  });

  it('shows active step (first incomplete) with description', () => {
    render(<OnboardingChecklist />);
    expect(screen.getByText('onboarding.step.topic.desc')).toBeInTheDocument();
    // non-active steps don't show desc
    expect(screen.queryByText('onboarding.step.source.desc')).not.toBeInTheDocument();
  });

  it('shows checkmark for completed steps and no CTA', () => {
    resetStore({ steps: { topic: true, source: false, trigger: false, read: false, share: false } });
    render(<OnboardingChecklist />);
    // completed step has no CTA link
    expect(screen.queryByRole('link', { name: 'onboarding.step.topic.cta' })).not.toBeInTheDocument();
    // next active step cta is visible
    expect(screen.getByRole('link', { name: 'onboarding.step.source.cta' })).toBeInTheDocument();
  });

  it('collapse button calls toggleCollapsed', () => {
    const toggleCollapsed = vi.fn();
    useOnboardingStore.setState({ toggleCollapsed });
    render(<OnboardingChecklist />);
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.collapse' }));
    expect(toggleCollapsed).toHaveBeenCalledTimes(1);
  });

  it('shows celebration state immediately at 100% and auto-dismisses after 5 seconds', () => {
    const setDismissed = vi.fn();
    // Inject spy and 100% state BEFORE render
    useOnboardingStore.setState({
      steps: { topic: true, source: true, trigger: true, read: true, share: true },
      dismissed: false,
      setDismissed,
    });

    render(<OnboardingChecklist />);
    // Celebration renders immediately since isComplete is derived
    expect(screen.getByText('onboarding.celebration_title')).toBeInTheDocument();

    act(() => { vi.advanceTimersByTime(5000); });
    expect(setDismissed).toHaveBeenCalledWith(true);
  });
});
