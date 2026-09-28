import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { SystemMetadata } from './SystemMetadata';

vi.mock('../../hooks/useSystemMetadata', () => ({
  useSystemMetadata: () => ({ data: { version: '0.20.0', status: 'ACTIVE_EVOLUTION' }, isLoading: false }),
}));

describe('SystemMetadata', () => {
  it('offers only the changelog and roadmap tabs', () => {
    render(<SystemMetadata isOpen onClose={vi.fn()} initialTab="roadmap" />);

    const tabs = screen.getAllByRole('tab');
    expect(tabs.map((tab) => tab.id)).toEqual(['tab-changelog', 'tab-roadmap']);
    expect(screen.getByRole('tab', { selected: true })).toHaveAttribute('id', 'tab-roadmap');
    expect(document.getElementById('tab-wisdom')).toBeNull();
  });
});
