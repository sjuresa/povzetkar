import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { SourcesLibraryPage } from './SourcesLibraryPage';
import { useGetApiV1Sources } from '../api/generated/content-sources/content-sources';

vi.mock('../api/generated/content-sources/content-sources', () => ({
  useGetApiV1Sources: vi.fn(),
}));

vi.mock('../hooks/useDebounce', () => ({
  useDebounce: <T,>(value: T) => value,
}));

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

function givenSources(items: { id: number; name: string }[]) {
  vi.mocked(useGetApiV1Sources).mockReturnValue({
    data: { data: { items: items.map((s) => ({ ...s, platform: 'YOUTUBE', sourceIdentifier: `UC${s.id}` })), totalCount: items.length } },
    isLoading: false,
  } as unknown as ReturnType<typeof useGetApiV1Sources>);
}

describe('SourcesLibraryPage', () => {
  it('tells a user without sources to add them in a topic', () => {
    givenSources([]);
    render(<SourcesLibraryPage />);

    expect(screen.getByText('sources.library.empty_title')).toBeInTheDocument();
    expect(screen.getByText('sources.library.empty_link').closest('a')).toHaveAttribute('href', '/topics');
    expect(screen.queryByText('sources.library.no_results')).not.toBeInTheDocument();
  });

  it('shows only "no sources found" for a search without hits', () => {
    givenSources([]);
    render(<SourcesLibraryPage />);

    fireEvent.change(screen.getByPlaceholderText('sources.library.search_placeholder'), { target: { value: 'nothing' } });

    expect(screen.getByText('sources.library.no_results')).toBeInTheDocument();
    expect(screen.queryByText('sources.library.empty_title')).not.toBeInTheDocument();
    expect(screen.queryByText('sources.library.empty_hint')).not.toBeInTheDocument();
  });

  it('lists the sources it gets', () => {
    givenSources([{ id: 1, name: 'My Channel' }]);
    render(<SourcesLibraryPage />);

    expect(screen.getByText('My Channel')).toBeInTheDocument();
    expect(screen.getByText('sources.library.title')).toBeInTheDocument();
  });
});
