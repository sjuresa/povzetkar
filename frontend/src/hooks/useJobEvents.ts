import { useEffect, useRef } from 'react';
import { useEventStore, type JobStatusChangedEvent } from '../store/eventStore';
import { toast } from 'sonner';
import logger from '../util/logger'; // Added import for logger

const API_BASE_URL = (import.meta.env as Record<string, string>).VITE_API_BASE_URL || '';

import { useShallow } from 'zustand/react/shallow';

export function useJobEvents() {
  const { updateJobStatus, setLastEvent, setConnectionStatus } = useEventStore(
    useShallow((state) => ({
      updateJobStatus: state.updateJobStatus,
      setLastEvent: state.setLastEvent,
      setConnectionStatus: state.setConnectionStatus,
    }))
  );
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    // Prevent multiple connections in dev (Strict Mode)
    if (eventSourceRef.current) return;

    const url = `${API_BASE_URL}/api/v1/events/stream`;
    logger.info(`[SSE] Connecting to ${url}...`); // Replaced console.log with logger.info
    setConnectionStatus('connecting');
    
    const es = new EventSource(url, { withCredentials: true });
    eventSourceRef.current = es;

    es.onopen = () => {
      logger.info('[SSE] Connection established'); // Replaced console.log with logger.info
      setLastEvent('CONNECTED');
      setConnectionStatus('connected');
    };

    es.onerror = (e) => {
      logger.error('[SSE] Connection error', { detail: e }); // Replaced console.error with logger.error
      setLastEvent('ERROR');
      setConnectionStatus('disconnected');
    };

    // Listen for specific event types from backend (EventApiResource.java)
    es.addEventListener('JOB_STATUS_CHANGED', (event) => {
      try {
        const payload = JSON.parse(event.data);
        const data: JobStatusChangedEvent = payload.data;
        logger.info('[SSE] Job status changed', { jobData: data }); // Replaced console.log with logger.info
        
        if (!data || !data.jobId) {
          logger.warn('[SSE] Received malformed JOB_STATUS_CHANGED event', { payload }); // Replaced console.warn with logger.warn
          return;
        }

        updateJobStatus(data);
        
        const jobName = data.jobType.toLowerCase().replace(/_/g, ' ');
        const details = data.details ? `: ${data.details}` : '';

        // Show toast for significant changes
        if (data.status === 'RUNNING') {
          toast.info(`Job Started: ${jobName}`, {
            description: `Processing information${details}...`,
          });
        } else if (data.status === 'COMPLETED') {
          toast.success(`Job Completed: ${jobName}`, {
            description: `Successfully processed${details}.`,
            duration: 5000,
          });
        } else if (data.status === 'FAILED') {
          toast.error(`Job Failed: ${jobName}`, {
            description: `An error occurred while processing${details}.`,
            duration: 7000,
          });
        }
      } catch (err) {
        logger.error('[SSE] Failed to parse JOB_STATUS_CHANGED event', { error: err as Error }); // Replaced console.error with logger.error
      }
    });

    es.addEventListener('PULSE', () => {
      setLastEvent('PULSE');
    });

    return () => {
      logger.info('[SSE] Closing connection'); // Replaced console.log with logger.info
      es.close();
      eventSourceRef.current = null;
      setConnectionStatus('disconnected');
    };
  }, [updateJobStatus, setLastEvent, setConnectionStatus]); // Removed toast from dependency array

  return { isConnected: !!eventSourceRef.current };
}
