import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { DashboardPage } from './DashboardPage';
import { useGetApiV1Dashboard } from '../api/generated/dashboard/dashboard';
import { useEventStore } from '../store/eventStore';
import { useOnboardingStore } from '../store/onboardingStore';

// Mock the API hooks and store
vi.mock('../api/generated/dashboard/dashboard', () => ({
  useGetApiV1Dashboard: vi.fn(),
}));

vi.mock('../api/generated/events/events', () => ({
  usePostApiV1EventsTestPulse: vi.fn(() => ({ 
    mutate: vi.fn(), 
    isPending: false 
  })),
}));

vi.mock('../store/eventStore', () => ({
  useEventStore: vi.fn(),
}));

vi.mock('../store/onboardingStore', () => ({
  useOnboardingStore: vi.fn(),
}));

vi.mock('../components/dashboard/OnboardingChecklist', () => ({
  OnboardingChecklist: () => <div data-testid="onboarding-checklist">Checklist</div>,
}));

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

const mockSyncFromDashboard = vi.fn();

const renderDashboard = () => {
  return render(<DashboardPage />);
};

function setupOnboardingStore(dismissed = false, loaded = true) {
  vi.mocked(useOnboardingStore).mockReturnValue({
    loaded,
    dismissed,
    syncFromDashboard: mockSyncFromDashboard,
    steps: { topic: false, source: false, trigger: false, read: false, share: false },
    collapsed: false,
    markStepComplete: vi.fn(),
    toggleCollapsed: vi.fn(),
    setDismissed: vi.fn(),
  } as any);
}

describe('DashboardPage', () => {
  it('renders loading state initially', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: true,
      isError: false,
      isRefetching: false,
      data: undefined,
      refetch: vi.fn(),
    } as any);

    vi.mocked(useEventStore).mockReturnValue([] as any);
    setupOnboardingStore();

    renderDashboard();
    expect(screen.queryByText('dashboard.title')).not.toBeInTheDocument();
  });

  it('renders empty state when there is no data', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false,
      isError: false,
      isRefetching: false,
      data: { data: { stats: { activeTopics: 0, briefingsGeneratedLast7Days: 0 }, recentBriefings: [] } },
      refetch: vi.fn(),
    } as any);

    vi.mocked(useEventStore).mockReturnValue([] as any);
    setupOnboardingStore();

    renderDashboard();
    expect(screen.getByText(/dashboard.empty.welcome/i)).toBeInTheDocument();
    expect(screen.getByText(/dashboard.empty.subtitle/i)).toBeInTheDocument();
  });

  it('renders real data when available', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false,
      isError: false,
      isRefetching: false,
      data: {
        data: {
          stats: { activeTopics: 5, briefingsGeneratedLast7Days: 12 },
          recentBriefings: [
            { id: 1, title: 'AI News', topicName: 'Tech', briefingDate: '2026-03-15' },
          ],
        }
      },
      refetch: vi.fn(),
    } as any);

    vi.mocked(useEventStore).mockReturnValue([] as any);
    setupOnboardingStore();

    renderDashboard();
    expect(screen.getByText('dashboard.title')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('AI News')).toBeInTheDocument();
  });

  it('renders OnboardingChecklist when dismissed is false', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false, isError: false, isRefetching: false,
      data: { data: { stats: { activeTopics: 0, briefingsGeneratedLast7Days: 0 }, recentBriefings: [] } },
      refetch: vi.fn(),
    } as any);
    vi.mocked(useEventStore).mockReturnValue([] as any);
    setupOnboardingStore(false);

    renderDashboard();
    expect(screen.getByTestId('onboarding-checklist')).toBeInTheDocument();
  });

  it('does not render OnboardingChecklist before the guide state is loaded (e.g. pending account)', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false, isError: false, isRefetching: false,
      data: { data: { stats: { activeTopics: 0, briefingsGeneratedLast7Days: 0 }, recentBriefings: [] } },
      refetch: vi.fn(),
    } as any);
    vi.mocked(useEventStore).mockReturnValue([] as any);
    setupOnboardingStore(false, false);

    renderDashboard();
    expect(screen.queryByTestId('onboarding-checklist')).not.toBeInTheDocument();
  });

  it('syncs the guide from dashboard data only once the guide state is loaded', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false, isError: false, isRefetching: false,
      data: { data: { stats: { activeTopics: 2, briefingsGeneratedLast7Days: 0 }, recentBriefings: [] } },
      refetch: vi.fn(),
    } as any);
    vi.mocked(useEventStore).mockReturnValue([] as any);
    mockSyncFromDashboard.mockClear();
    setupOnboardingStore(false, false);

    const { rerender } = renderDashboard();
    expect(mockSyncFromDashboard).not.toHaveBeenCalled();

    setupOnboardingStore(false, true);
    rerender(<DashboardPage />);
    expect(mockSyncFromDashboard).toHaveBeenCalledWith(2, false);
  });

  it('does not render OnboardingChecklist when dismissed is true', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false, isError: false, isRefetching: false,
      data: { data: { stats: { activeTopics: 0, briefingsGeneratedLast7Days: 0 }, recentBriefings: [] } },
      refetch: vi.fn(),
    } as any);
    vi.mocked(useEventStore).mockReturnValue([] as any);
    setupOnboardingStore(true);

    renderDashboard();
    expect(screen.queryByTestId('onboarding-checklist')).not.toBeInTheDocument();
  });

  it('calls syncFromDashboard with correct args when data loads', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false, isError: false, isRefetching: false,
      data: { data: { stats: { activeTopics: 3, briefingsGeneratedLast7Days: 5 }, recentBriefings: [] } },
      refetch: vi.fn(),
    } as any);
    vi.mocked(useEventStore).mockReturnValue([] as any);
    setupOnboardingStore();

    renderDashboard();
    expect(mockSyncFromDashboard).toHaveBeenCalledWith(3, true);
  });

  it('triggers refetch when refresh button is clicked', () => {
    const refetch = vi.fn();
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false,
      isError: false,
      isRefetching: false,
      data: { data: { stats: { activeTopics: 5, briefingsGeneratedLast7Days: 0 } } },
      refetch,
    } as any);

    vi.mocked(useEventStore).mockReturnValue([] as any);
    setupOnboardingStore();

    renderDashboard();
    const refreshBtn = screen.getByTitle('dashboard.refresh');
    fireEvent.click(refreshBtn);
    expect(refetch).toHaveBeenCalled();
  });
});
