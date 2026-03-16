import { create } from 'zustand';

export type JobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface JobStatusChangedEvent {
  jobId: string;
  status: JobStatus;
  jobType: string;
}

export type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

interface EventState {
  jobs: Record<string, JobStatusChangedEvent>;
  lastEvent: string | null;
  connectionStatus: ConnectionStatus;
  updateJobStatus: (event: JobStatusChangedEvent) => void;
  clearJob: (jobId: string) => void;
  setLastEvent: (type: string) => void;
  setConnectionStatus: (status: ConnectionStatus) => void;
}

export const useEventStore = create<EventState>((set) => ({
  jobs: {},
  lastEvent: null,
  connectionStatus: 'disconnected',
  updateJobStatus: (event) =>
    set((state) => ({
      jobs: { ...state.jobs, [event.jobId]: event },
    })),
  clearJob: (jobId) =>
    set((state) => {
      const { [jobId]: _, ...remainingJobs } = state.jobs;
      return { jobs: remainingJobs };
    }),
  setLastEvent: (type) => set({ lastEvent: type }),
  setConnectionStatus: (status) => set({ connectionStatus: status }),
}));
