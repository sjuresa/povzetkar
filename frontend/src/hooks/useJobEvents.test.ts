import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useJobEvents } from './useJobEvents';
import { useEventStore } from '../store/eventStore';
import { toast } from 'sonner';

// Mock dependencies
vi.mock('../store/eventStore', () => ({
  useEventStore: vi.fn(),
}));

vi.mock('sonner', () => ({
  toast: {
    info: vi.fn(),
    success: vi.fn(),
    error: vi.fn(),
  },
}));

// Better EventSource Mock
let mockEventSourceInstance: any = null;

class MockEventSource {
  onopen: (() => void) | null = null;
  onerror: ((e: any) => void) | null = null;
  listeners: Record<string, ((e: any) => void)[]> = {};

  constructor() {
    mockEventSourceInstance = this;
  }

  addEventListener(type: string, listener: (e: any) => void) {
    if (!this.listeners[type]) this.listeners[type] = [];
    this.listeners[type].push(listener);
  }

  emit(type: string, data: any) {
    this.listeners[type]?.forEach((l) => l({ data: JSON.stringify(data) }));
  }

  close = vi.fn();
}

global.EventSource = MockEventSource as any;

describe('useJobEvents', () => {
  const updateJobStatus = vi.fn();
  const setLastEvent = vi.fn();
  const setConnectionStatus = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockEventSourceInstance = null;
    (useEventStore as any).mockReturnValue({ 
      updateJobStatus, 
      setLastEvent, 
      setConnectionStatus,
      lastEvent: null 
    });
  });

  it('should connect to SSE and handle JOB_STATUS_CHANGED event', () => {
    renderHook(() => useJobEvents());

    expect(mockEventSourceInstance).not.toBeNull();
    expect(setConnectionStatus).toHaveBeenCalledWith('connecting');
    
    // Simulate connection open
    mockEventSourceInstance.onopen();
    expect(setConnectionStatus).toHaveBeenCalledWith('connected');
    expect(setLastEvent).toHaveBeenCalledWith('CONNECTED');

    // Simulate JOB_STATUS_CHANGED event - wrapped in SSE DTO
    const jobData = { jobId: '123', status: 'RUNNING', jobType: 'GENERATE_TOPIC_BRIEFING' };
    const eventPayload = { type: 'JOB_STATUS_CHANGED', data: jobData };
    mockEventSourceInstance.emit('JOB_STATUS_CHANGED', eventPayload);

    expect(updateJobStatus).toHaveBeenCalledWith(jobData);
    expect(toast.info).toHaveBeenCalledWith('Job Started: generate topic briefing', expect.objectContaining({
      description: expect.stringContaining('Processing information'),
    }));
  });

  it('should handle PULSE event', () => {
    renderHook(() => useJobEvents());
    
    // Pulse is also wrapped
    mockEventSourceInstance.emit('PULSE', { type: 'PULSE', data: {} });
    expect(setLastEvent).toHaveBeenCalledWith('PULSE');
  });

  it('should close connection on unmount', () => {
    const { unmount } = renderHook(() => useJobEvents());
    const closeSpy = mockEventSourceInstance.close;
    unmount();
    expect(closeSpy).toHaveBeenCalled();
    expect(setConnectionStatus).toHaveBeenCalledWith('disconnected');
  });

  it('should NOT reconnect when unrelated store state changes', () => {
    const { rerender } = renderHook(() => useJobEvents());
    const initialInstance = mockEventSourceInstance;
    
    // Simulate unrelated store update
    rerender();
    
    expect(mockEventSourceInstance).toBe(initialInstance);
  });
});
