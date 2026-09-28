import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
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

vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ sourceId: '5' }),
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

const httpError = (status: number) =>
  new AxiosError('failed', String(status), undefined, undefined, { status } as AxiosResponse);

type QueryOptions = { throwOnError: (error: unknown) => boolean; retry: (failureCount: number, error: unknown) => boolean };

describe('SourceDetailPage', () => {
  it('shows "Source not found" for a source outside the user\'s topics instead of failing', () => {
    vi.mocked(useGetApiV1SourcesId).mockReturnValue({ data: undefined, isLoading: false } as unknown as ReturnType<typeof useGetApiV1SourcesId>);
    vi.mocked(useGetApiV1SourcesIdItems).mockReturnValue({ data: undefined, isLoading: false } as unknown as ReturnType<typeof useGetApiV1SourcesIdItems>);

    render(<SourceDetailPage />);

    expect(screen.getByText('sources.detail.not_found')).toBeInTheDocument();
    expect(screen.getByText('sources.detail.back_to_library').closest('a')).toHaveAttribute('href', '/sources');
  });

  it('treats 404 as an answer — no error boundary, no retry — and other errors as before', () => {
    render(<SourceDetailPage />);
    const sourceOptions = vi.mocked(useGetApiV1SourcesId).mock.calls[0][1]?.query as unknown as QueryOptions;
    const itemOptions = vi.mocked(useGetApiV1SourcesIdItems).mock.calls[0][2]?.query as unknown as QueryOptions;

    for (const options of [sourceOptions, itemOptions]) {
      expect(options.throwOnError(httpError(404))).toBe(false);
      expect(options.retry(0, httpError(404))).toBe(false);
      expect(options.throwOnError(httpError(500))).toBe(true);
      expect(options.retry(0, httpError(500))).toBe(true);
      expect(options.retry(1, httpError(500))).toBe(false);
    }
  });
});
