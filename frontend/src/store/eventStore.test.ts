import { describe, it, expect, beforeEach } from 'vitest';
import { useEventStore, type JobStatusChangedEvent } from './eventStore';

describe('eventStore', () => {
  beforeEach(() => {
    useEventStore.setState({ jobs: {}, lastEvent: null });
  });

  it('should update job status when a new event arrives', () => {
    const event: JobStatusChangedEvent = {
      jobId: '123',
      status: 'RUNNING',
      jobType: 'GENERATE_TOPIC_BRIEFING',
    };

    useEventStore.getState().updateJobStatus(event);

    const state = useEventStore.getState();
    expect(state.jobs['123']).toEqual(event);
  });

  it('should clear a job from the store', () => {
    const event: JobStatusChangedEvent = {
      jobId: '123',
      status: 'COMPLETED',
      jobType: 'GENERATE_TOPIC_BRIEFING',
    };

    useEventStore.getState().updateJobStatus(event);
    expect(useEventStore.getState().jobs['123']).toBeDefined();

    useEventStore.getState().clearJob('123');
    expect(useEventStore.getState().jobs['123']).toBeUndefined();
  });

  it('should set the last event type', () => {
    useEventStore.getState().setLastEvent('CONNECTED');
    expect(useEventStore.getState().lastEvent).toBe('CONNECTED');
  });
});
