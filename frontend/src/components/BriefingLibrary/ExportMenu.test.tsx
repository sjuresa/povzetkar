import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ExportMenu } from '../briefing/ExportMenu';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@testing-library/jest-dom';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { generateBriefingMarkdown } from '../../util/exportTemplates';

vi.mock('../../util/exportTemplates', () => ({
  generateBriefingHtml: vi.fn(() => '<html>test</html>'),
  generateBriefingMarkdown: vi.fn(() => '# Markdown test'),
}));

// Mock the generated hooks to avoid actual API calls
vi.mock('../../api/generated/briefings/briefings', () => ({
  useGetApiV1BriefingsId: () => ({ refetch: vi.fn() }),
  usePostApiV1BriefingsIdShare: () => ({ mutate: vi.fn(), isPending: false }),
  usePostApiV1BriefingsIdUnshare: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock('../../api/generated/content-items/content-items', () => ({
  usePostApiV1ContentItemsIdSummaryShare: () => ({ mutate: vi.fn(), isPending: false }),
  usePostApiV1ContentItemsIdSummaryUnshare: () => ({ mutate: vi.fn(), isPending: false }),
}));

// Mock URL and Blob for export triggers
if (globalThis.window !== undefined) {
  globalThis.window.URL.createObjectURL = vi.fn();
  globalThis.window.URL.revokeObjectURL = vi.fn();
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockBriefingData = {
  id: 123,
  title: 'Test Briefing',
  uuid: null,
  topicName: 'General',
  date: '2026-03-28'
};

const mockSummaryData = {
  id: 456,
  title: 'Test Summary',
  uuid: 'summary-uuid-123',
  topicName: 'Insights',
  date: '2026-03-28',
  metaSummary: 'This is a test summary content'
};

describe('ExportMenu Polymorphic Behavior', () => {
  const queryClient = new QueryClient();

  it('renders correctly for Briefing mode (unshared)', async () => {
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <ExportMenu type="briefing" data={mockBriefingData} />
      </QueryClientProvider>
    );

    const trigger = container.querySelector('[data-testid="unique-export-dropdown-trigger"]');
    if (trigger) fireEvent.click(trigger);
    
    // Check for sharing options
    expect(screen.getAllByText(/briefingDetail.export.enableShare/i)[0]).toBeInTheDocument();
    // Check for standard export options
    expect(screen.getAllByText(/briefingDetail.export.markdown/i)[0]).toBeInTheDocument();
  });

  it('renders correctly for Summary mode (shared)', async () => {
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <ExportMenu type="summary" data={mockSummaryData} />
      </QueryClientProvider>
    );

    const trigger = container.querySelector('[data-testid="unique-export-dropdown-trigger"]');
    if (trigger) fireEvent.click(trigger);
    
    // Summary is shared, should show Copy Link and Disable
    expect(screen.getAllByText(/briefingDetail.export.copyLink/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/briefingDetail.export.disableShare/i)[0]).toBeInTheDocument();
  });

  it('triggers export logic on click', async () => {
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <ExportMenu type="summary" data={mockSummaryData} />
      </QueryClientProvider>
    );

    const trigger = container.querySelector('[data-testid="unique-export-dropdown-trigger"]');
    if (trigger) fireEvent.click(trigger);

    const markdownBtn = screen.getAllByText(/briefingDetail.export.markdown/i)[0].closest('button');
    if (markdownBtn) {
      fireEvent.click(markdownBtn);
    }

    // Verify file creation was attempted (async)
    await vi.waitFor(() => {
        expect(globalThis.window.URL.createObjectURL).toHaveBeenCalled();
    });
  });

  it('correctly extracts summary data for export', async () => {
    const summaryData = {
        ...mockSummaryData,
        metaSummary: 'TL;DR Text',
        summaries: [{ summaryText: 'Full Summary Text' }]
    };

    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <ExportMenu type="summary" data={summaryData} />
      </QueryClientProvider>
    );

    const trigger = container.querySelector('[data-testid="unique-export-dropdown-trigger"]');
    if (trigger) fireEvent.click(trigger);

    const markdownBtn = screen.getAllByText(/briefingDetail.export.markdown/i)[0].closest('button');
    if (markdownBtn) {
        fireEvent.click(markdownBtn);
    }

    await vi.waitFor(() => {
        // Verify that the template generator received the CORRECTED structure
        expect(generateBriefingMarkdown).toHaveBeenCalled();
        const callArgs = vi.mocked(generateBriefingMarkdown).mock.calls[0];
        expect(callArgs[0]).toMatchObject({
            metaSummary: 'TL;DR Text',
            content: 'Full Summary Text'
        });
    });
  });
});
