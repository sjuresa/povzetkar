import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { DashboardPage } from './DashboardPage';
import { useGetApiV1Dashboard } from '../api/generated/dashboard/dashboard';
import { 
  createRootRoute, 
  createRoute, 
  createRouter, 
  RouterProvider 
} from '@tanstack/react-router';

// Mock the API hook
vi.mock('../api/generated/dashboard/dashboard', () => ({
  useGetApiV1Dashboard: vi.fn(),
}));

// Setup a minimal router for testing links
const rootRoute = createRootRoute();
const indexRoute = createRoute({ getParentRoute: () => rootRoute, path: '/' });
const dashboardRoute = createRoute({ getParentRoute: () => rootRoute, path: '/dashboard', component: DashboardPage });
const topicsRoute = createRoute({ getParentRoute: () => rootRoute, path: '/topics' });
const briefingsRoute = createRoute({ getParentRoute: () => rootRoute, path: '/briefings' });
const briefingDetailRoute = createRoute({ getParentRoute: () => rootRoute, path: '/briefings/$briefingId' });

const routeTree = rootRoute.addChildren([indexRoute, dashboardRoute, topicsRoute, briefingsRoute, briefingDetailRoute]);
const router = createRouter({ routeTree });

const renderWithRouter = (ui: React.ReactElement) => {
  return render(<RouterProvider router={router} defaultComponent={() => ui} />);
};

describe('DashboardPage', () => {
  it('renders loading state initially', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: true,
      isError: false,
      isRefetching: false,
      data: { stats: { activeTopics: 0 }, recentBriefings: [] } as any,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useGetApiV1Dashboard>);

    renderWithRouter(<DashboardPage />);
    // We expect to see skeletons
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
  });

  it('renders empty state when there is no data', () => {
    vi.mocked(useGetApiV1Dashboard).mockReturnValue({
      isLoading: false,
      isError: false,
      isRefetching: false,
      data: { stats: { activeTopics: 0 }, recentBriefings: [] } as any,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useGetApiV1Dashboard>);

    renderWithRouter(<DashboardPage />);
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
    } as unknown as ReturnType<typeof useGetApiV1Dashboard>);

    renderWithRouter(<DashboardPage />);
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
    } as unknown as ReturnType<typeof useGetApiV1Dashboard>);

    renderWithRouter(<DashboardPage />);
    const refreshBtn = screen.getByTitle('Refresh Dashboard');
    fireEvent.click(refreshBtn);
    expect(refetch).toHaveBeenCalled();
  });
});
