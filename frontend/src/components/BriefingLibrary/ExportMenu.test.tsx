import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ExportMenu } from '../briefing/ExportMenu';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@testing-library/jest-dom';
import { describe, it, expect, afterEach } from 'vitest';

afterEach(() => {
  cleanup();
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

describe('ExportMenu Sharing', () => {
  it('renders correctly when uuid is null vs present', async () => {
    // 1. Render for null uuid
    const { unmount, container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <ExportMenu briefing={mockBriefing} />
      </QueryClientProvider>
    );

    const trigger = container.querySelector('[data-testid="unique-export-dropdown-trigger"]');
    if (trigger) fireEvent.click(trigger);
    
    expect(screen.getAllByText(/briefingDetail.export.enableShare/i)[0]).toBeInTheDocument();
    
    unmount();

    // 2. Render for present uuid
    const { container: container2 } = render(
      <QueryClientProvider client={new QueryClient()}>
        <ExportMenu briefing={mockSharedBriefing} />
      </QueryClientProvider>
    );

    const trigger2 = container2.querySelector('[data-testid="unique-export-dropdown-trigger"]');
    if (trigger2) fireEvent.click(trigger2);
    
    expect(screen.getAllByText(/briefingDetail.export.copyLink/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/briefingDetail.export.disableShare/i)[0]).toBeInTheDocument();
  });
});
