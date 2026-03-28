import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useSystemMetadata } from './useSystemMetadata';
import client from '../api/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

// Mock the API client
vi.mock('../api/client', () => ({
  default: {
    get: vi.fn(),
  },
}));

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

describe('useSystemMetadata', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch system metadata from the correct path', async () => {
    const mockData = { version: '0.14.0', environment: 'test', status: 'ACTIVE', releaseDate: '2026-03-28' };
    vi.mocked(client.get).mockResolvedValue({ data: { data: mockData, meta: {} } });

    const { result } = renderHook(() => useSystemMetadata(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(client.get).toHaveBeenCalledWith('/api/v1/system/metadata');
    expect(result.current.data).toEqual(mockData);
  });

  it('should throw error when data is missing from response', async () => {
    // Simulate a case where the inner data property is missing or null despite a 2xx response
    vi.mocked(client.get).mockResolvedValue({ data: { data: null, meta: {} } });

    const { result } = renderHook(() => useSystemMetadata(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('System metadata not found');
  });

  it('should handle API errors', async () => {
    vi.mocked(client.get).mockRejectedValue(new Error('Network Error'));

    const { result } = renderHook(() => useSystemMetadata(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('Network Error');
  });
});
