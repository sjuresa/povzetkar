import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LandingPage } from './LandingPage';
import { useAuthStore } from '../store/authStore';

vi.mock('../store/authStore', () => ({
  useAuthStore: vi.fn(),
}));

vi.mock('../hooks/useSystemMetadata', () => ({
  useSystemMetadata: () => ({ data: { version: '0.20.0', releaseDate: '2026-09-28' } }),
}));

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, className }: { children: React.ReactNode; to: string; className?: string }) => (
    <a href={to} className={className}>{children}</a>
  ),
}));

// Helmet needs a provider; render its children directly — React 19 hoists <title>/<meta> into document.head.
vi.mock('react-helmet-async', () => ({
  Helmet: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const mockLogin = vi.fn();

function renderAs(isAuthenticated: boolean) {
  // LandingPage selects { login, isAuthenticated } through useShallow; the selector runs on this state.
  vi.mocked(useAuthStore).mockImplementation(((selector: (state: unknown) => unknown) =>
    selector({ login: mockLogin, isAuthenticated })) as unknown as typeof useAuthStore);
  return render(<LandingPage />);
}

describe('LandingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lets an anonymous visitor sign in from the nav, the hero and the closing call to action', () => {
    renderAs(false);

    fireEvent.click(screen.getByTestId('nav-login'));
    fireEvent.click(screen.getByTestId('hero-cta'));
    fireEvent.click(screen.getByTestId('final-cta'));

    expect(mockLogin).toHaveBeenCalledTimes(3);
    expect(screen.getByText('landing.hero.cta_note')).toBeInTheDocument();
    expect(document.querySelector('a[href="/dashboard"]')).toBeNull();
  });

  it('shows dashboard links instead of sign-in to a signed-in user', () => {
    renderAs(true);

    expect(screen.queryByTestId('nav-login')).toBeNull();
    expect(screen.queryByTestId('hero-cta')).toBeNull();
    expect(screen.queryByTestId('final-cta')).toBeNull();
    expect(document.querySelectorAll('a[href="/dashboard"]')).toHaveLength(3);
  });

  it('labels the sample briefing as an example', () => {
    renderAs(false);

    const sample = screen.getByTestId('sample-briefing');
    expect(sample).toHaveTextContent('landing.sample.badge');
    expect(sample).toHaveAttribute('aria-label', 'landing.sample.aria');
  });

  it('shows three benefits under the hero', () => {
    renderAs(false);

    expect(screen.getAllByTestId('benefit')).toHaveLength(3);
    expect(screen.getByText('landing.benefits.b1_title')).toBeInTheDocument();
  });

  it('marks every roadmap item as planned', () => {
    renderAs(false);

    expect(screen.getAllByTestId('planned-badge')).toHaveLength(3);
    expect(screen.getByText('landing.roadmap.item1_title')).toBeInTheDocument();
    expect(screen.getByText('landing.roadmap.item3_title')).toBeInTheDocument();
  });

  it('renders the FAQ as native disclosure widgets', () => {
    const { container } = renderAs(false);

    expect(container.querySelectorAll('details')).toHaveLength(3);
    expect(screen.getByText('landing.faq.q2')).toBeInTheDocument();
  });

  it('has exactly one h1 and no unimplemented-feature status claims', () => {
    const { container } = renderAs(false);

    expect(container.querySelectorAll('h1')).toHaveLength(1);
    expect(container).not.toHaveTextContent(/\bactive\b/i);
    expect(container).not.toHaveTextContent(/wisdom|reality check/i);
  });

  it('shows the version in the footer but no links to the project changelog or roadmap', () => {
    renderAs(false);

    expect(screen.getByText('landing.footer.version_info')).toBeInTheDocument();
    expect(screen.queryByText('system.tabs.roadmap')).toBeNull();
    expect(screen.queryByText('system.tabs.changelog')).toBeNull();
  });

  it('localizes the document title and updates the static meta tags in place', () => {
    // index.html ships English defaults; the page must update them, not add a second set.
    const description = Object.assign(document.createElement('meta'), { name: 'description', content: 'EN description' });
    const ogTitle = Object.assign(document.createElement('meta'), { content: 'EN title' });
    ogTitle.setAttribute('property', 'og:title');
    const ogDescription = Object.assign(document.createElement('meta'), { content: 'EN description' });
    ogDescription.setAttribute('property', 'og:description');
    document.head.append(description, ogTitle, ogDescription);

    const { unmount } = renderAs(false);

    expect(document.head.querySelector('title')).toHaveTextContent('landing.meta.title');
    expect(document.head.querySelectorAll('meta[name="description"]')).toHaveLength(1);
    expect(description.content).toBe('landing.meta.description');
    expect(ogTitle.content).toBe('landing.meta.title');
    expect(ogDescription.content).toBe('landing.meta.description');

    unmount();
    expect(description.content).toBe('EN description');
    expect(ogTitle.content).toBe('EN title');
    [description, ogTitle, ogDescription].forEach((tag) => tag.remove());
  });
});
