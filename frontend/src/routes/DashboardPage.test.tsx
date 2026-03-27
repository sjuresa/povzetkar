import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { DashboardPage } from './DashboardPage';
import { useGetApiV1Dashboard } from '../api/generated/dashboard/dashboard';
import { useEventStore } from '../store/eventStore';

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

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

const renderDashboard = () => {
  return render(<DashboardPage />);
};

describe('DashboardPage', () => {
  it('renders loading state initially', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: true,
      isError: false,
      isRefetching: false,
      data: undefined,
      refetch: vi.fn(),
    } as any);

    vi.mocked(useEventStore).mockReturnValue({} as any);

    renderDashboard();
    expect(screen.queryByText('dashboard.title')).not.toBeInTheDocument();
  });

  it('renders empty state when there is no data', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false,
      isError: false,
      isRefetching: false,
      data: { data: { stats: { activeTopics: 0 }, recentBriefings: [] } },
      refetch: vi.fn(),
    } as any);

    vi.mocked(useEventStore).mockReturnValue({} as any);

    renderDashboard();
    expect(screen.getByText(/dashboard.empty.welcome/i)).toBeInTheDocument();
    expect(screen.getByText(/dashboard.empty.action/i)).toBeInTheDocument();
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

    vi.mocked(useEventStore).mockReturnValue({} as any);

    renderDashboard();
    expect(screen.getByText('dashboard.title')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('AI News')).toBeInTheDocument();
  });

  it('triggers refetch when refresh button is clicked', () => {
    const refetch = vi.fn();
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false,
      isError: false,
      isRefetching: false,
      data: { data: { stats: { activeTopics: 5 } } },
      refetch,
    } as any);

    vi.mocked(useEventStore).mockReturnValue({} as any);

    renderDashboard();
    const refreshBtn = screen.getByTitle('dashboard.refresh');
    fireEvent.click(refreshBtn);
    expect(refetch).toHaveBeenCalled();
  });
});
