import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { DashboardPage } from './DashboardPage';
import { useGetApiV1Dashboard } from '../api/generated/dashboard/dashboard';


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

import { useEventStore } from '../store/eventStore';


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

    vi.mocked(useEventStore).mockReturnValue([]);

    renderDashboard();
    // We expect NOT to see Dashboard title yet while loading
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
  });

  it('renders empty state when there is no data', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false,
      isError: false,
      isRefetching: false,
      data: { stats: { activeTopics: 0 }, recentBriefings: [] } as any,
      refetch: vi.fn(),
    } as any);

    vi.mocked(useEventStore).mockReturnValue([]);

    renderDashboard();
    expect(screen.getByText(/Welcome to/i)).toBeInTheDocument();
    expect(screen.getByText(/Add Your First Topic/i)).toBeInTheDocument();
  });

  it('renders real data when available', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false,
      isError: false,
      isRefetching: false,
      data: {
        stats: { activeTopics: 5, briefingsGeneratedLast7Days: 12 },
        recentBriefings: [
          { id: 1, title: 'AI News', topicName: 'Tech', briefingDate: '2026-03-15' },
        ],
      } as any,
      refetch: vi.fn(),
    } as any);

    vi.mocked(useEventStore).mockReturnValue([]);

    renderDashboard();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
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
      data: { stats: { activeTopics: 5 } } as any,
      refetch,
    } as any);

    vi.mocked(useEventStore).mockReturnValue([]);

    renderDashboard();
    const refreshBtn = screen.getByTitle('Refresh Dashboard');
    fireEvent.click(refreshBtn);
    expect(refetch).toHaveBeenCalled();
  });
});
