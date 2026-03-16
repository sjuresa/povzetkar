import { useEffect, useRef } from 'react';
import { useEventStore, type JobStatusChangedEvent } from '../store/eventStore';
import { useToast } from './use-toast';

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
  const { toast } = useToast();
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    // Prevent multiple connections in dev (Strict Mode)
    if (eventSourceRef.current) return;

    const url = `${API_BASE_URL}/api/v1/events/stream`;
    console.log(`[SSE] Connecting to ${url}...`);
    setConnectionStatus('connecting');
    
    const es = new EventSource(url, { withCredentials: true });
    eventSourceRef.current = es;

    es.onopen = () => {
      console.log('[SSE] Connection established');
      setLastEvent('CONNECTED');
      setConnectionStatus('connected');
    };

    es.onerror = (e) => {
      console.error('[SSE] Connection error:', e);
      setLastEvent('ERROR');
      setConnectionStatus('disconnected');
    };

    // Listen for specific event types from backend (EventApiResource.java)
    es.addEventListener('JOB_STATUS_CHANGED', (event) => {
      try {
        const payload = JSON.parse(event.data);
        const data: JobStatusChangedEvent = payload.data;
        console.log('[SSE] Job status changed:', data);
        
        if (!data || !data.jobId) {
          console.warn('[SSE] Received malformed JOB_STATUS_CHANGED event', payload);
          return;
        }

        updateJobStatus(data);
        
        // Show toast for significant changes
        if (data.status === 'RUNNING') {
          toast({
            title: 'Job Started',
            description: `Processing ${data.jobType.toLowerCase().replace(/_/g, ' ')}...`,
            type: 'info',
          });
        } else if (data.status === 'COMPLETED') {
          toast({
            title: 'Job Completed',
            description: 'Your request has been processed successfully.',
            type: 'success',
          });
        } else if (data.status === 'FAILED') {
          toast({
            title: 'Job Failed',
            description: 'An error occurred during processing.',
            type: 'destructive',
          });
        }
      } catch (err) {
        console.error('[SSE] Failed to parse job event:', err);
      }
    });

    es.addEventListener('PULSE', () => {
      setLastEvent('PULSE');
    });

    return () => {
      console.log('[SSE] Closing connection');
      es.close();
      eventSourceRef.current = null;
      setConnectionStatus('disconnected');
    };
  }, [updateJobStatus, setLastEvent, toast]);

  return { isConnected: !!eventSourceRef.current };
}
