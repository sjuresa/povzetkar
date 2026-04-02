import { create } from 'zustand';

export type JobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type NotificationType = 'success' | 'error' | 'info';


export interface JobStatusChangedEvent {
  jobId: string;
  status: JobStatus;
  jobType: string;
  details?: string;
}

export type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

interface EventState {
  jobs: Record<string, JobStatusChangedEvent>;
  lastEvent: string | null;
  connectionStatus: ConnectionStatus;
  lastNotification: { type: NotificationType; timestamp: number } | null;
  updateJobStatus: (event: JobStatusChangedEvent) => void;
  clearJob: (jobId: string) => void;
  setLastEvent: (type: string) => void;
  setConnectionStatus: (status: ConnectionStatus) => void;
  triggerNotificationSound: (type: NotificationType) => void;
}

export const useEventStore = create<EventState>((set) => ({
  jobs: {},
  lastEvent: null,
  connectionStatus: 'disconnected',
  lastNotification: null,
  updateJobStatus: (event) =>
    set((state) => ({
      jobs: { ...state.jobs, [event.jobId]: event },
    })),
  clearJob: (jobId) =>
    set((state) => {
      const updatedJobs = { ...state.jobs };
      delete updatedJobs[jobId];
      return { jobs: updatedJobs };
    }),
  setLastEvent: (type) => set({ lastEvent: type }),
  setConnectionStatus: (status) => set({ connectionStatus: status }),
  triggerNotificationSound: (type) => set({ lastNotification: { type, timestamp: Date.now() } }),
}));
