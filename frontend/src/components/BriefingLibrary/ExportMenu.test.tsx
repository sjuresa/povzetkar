import { render, screen, fireEvent } from '@testing-library/react';
import { ExportMenu } from '../briefing/ExportMenu';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@testing-library/jest-dom';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

const mockBriefing = {
  id: 123,
  title: 'Test Briefing',
  uuid: null,
  topicName: 'General',
  briefingDate: '2026-03-28'
};

const mockSharedBriefing = {
  id: 124,
  title: 'Shared Briefing',
  uuid: 'test-uuid-123',
  topicName: 'Marketing',
  briefingDate: '2026-03-28'
};

import { describe, it, expect } from 'vitest';

describe('ExportMenu Sharing', () => {
  it('renders "Enable Share Link" when uuid is null', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <ExportMenu briefing={mockBriefing} />
      </QueryClientProvider>
    );

    // Open the menu
    fireEvent.click(screen.getByText(/Export/i));

    expect(screen.getByText(/Enable Share Link/i)).toBeInTheDocument();
    expect(screen.queryByText(/Copy Share Link/i)).not.toBeInTheDocument();
  });

  it('renders share actions when uuid is present', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <ExportMenu briefing={mockSharedBriefing} />
      </QueryClientProvider>
    );

    // Open the menu
    fireEvent.click(screen.getByText(/Export/i));

    expect(screen.getByText(/Copy Share Link/i)).toBeInTheDocument();
    expect(screen.getByText(/Disable Share Link/i)).toBeInTheDocument();
    expect(screen.queryByText(/Enable Share Link/i)).not.toBeInTheDocument();
  });
});
