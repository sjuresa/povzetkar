import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AxiosError, type AxiosResponse } from 'axios';
import { SourceDetailPage } from './SourceDetailPage';
import { useGetApiV1SourcesId } from '../api/generated/content-sources/content-sources';
import { useGetApiV1SourcesIdItems } from '../api/generated/content-items/content-items';

vi.mock('../api/generated/content-sources/content-sources', () => ({
  useGetApiV1SourcesId: vi.fn(),
}));

vi.mock('../api/generated/content-items/content-items', () => ({
  useGetApiV1SourcesIdItems: vi.fn(),
  usePostApiV1ContentItemsIdCommandsStatus: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  getGetApiV1SourcesIdItemsQueryKey: vi.fn(),
}));

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

const route = vi.hoisted(() => ({ sourceId: '5' }));
vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ sourceId: route.sourceId }),
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

const httpError = (status: number) =>
  new AxiosError('failed', String(status), undefined, undefined, { status } as AxiosResponse);

type QueryOptions = {
  enabled: boolean;
  throwOnError: (error: unknown) => boolean;
  retry: (failureCount: number, error: unknown) => boolean;
};

function renderedQueryOptions() {
  render(<SourceDetailPage />);
  return [
    vi.mocked(useGetApiV1SourcesId).mock.calls[0][1]?.query as unknown as QueryOptions,
    vi.mocked(useGetApiV1SourcesIdItems).mock.calls[0][2]?.query as unknown as QueryOptions,
  ];
}

describe('SourceDetailPage', () => {
  beforeEach(() => {
    route.sourceId = '5';
    vi.mocked(useGetApiV1SourcesId).mockReturnValue({ data: undefined, isLoading: false } as unknown as ReturnType<typeof useGetApiV1SourcesId>);
    vi.mocked(useGetApiV1SourcesIdItems).mockReturnValue({ data: undefined, isLoading: false } as unknown as ReturnType<typeof useGetApiV1SourcesIdItems>);
  });

  it('fetches a source with a numeric id', () => {
    for (const options of renderedQueryOptions()) {
      expect(options.enabled).toBe(true);
    }
  });

  it('sends no request for a non-numeric id (the generated guard only checks null/undefined)', () => {
    route.sourceId = 'abc';

    for (const options of renderedQueryOptions()) {
      expect(options.enabled).toBe(false);
    }
  });

  it('shows "Source not found" for a source outside the user\'s topics instead of failing', () => {
    render(<SourceDetailPage />);

    expect(screen.getByText('sources.detail.not_found')).toBeInTheDocument();
    expect(screen.getByText('sources.detail.back_to_library').closest('a')).toHaveAttribute('href', '/sources');
  });

  it('treats 404 as an answer — no error boundary, no retry — and other errors as before', () => {
    for (const options of renderedQueryOptions()) {
      expect(options.throwOnError(httpError(404))).toBe(false);
      expect(options.retry(0, httpError(404))).toBe(false);
      expect(options.throwOnError(httpError(500))).toBe(true);
      expect(options.retry(0, httpError(500))).toBe(true);
      expect(options.retry(1, httpError(500))).toBe(false);
    }
  });
});
