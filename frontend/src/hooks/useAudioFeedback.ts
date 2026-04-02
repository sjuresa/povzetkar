import { useEffect, useRef } from 'react';
import { useEventStore } from '../store/eventStore';
import logger from '../util/logger';

/**
 * useAudioFeedback
 * 
 * A decoupled hook that generates auditory feedback using the Web Audio API.
 * Uses OscillatorNode to synthesize sounds dynamically (Zero-asset footprint).
 */
export function useAudioFeedback() {
  const lastNotification = useEventStore((state) => state.lastNotification);
  
  const audioContextRef = useRef<AudioContext | null>(null);

  // Cleanup AudioContext on unmount
  useEffect(() => {
    return () => {
      if (audioContextRef.current) {
        audioContextRef.current.close().catch((e: unknown) => logger.debug('[Audio] Error closing context', { error: e }));
        audioContextRef.current = null;
      }
    };
  }, []);

  // Initialize AudioContext on first notification (to comply with autoplay)
  const initAudio = () => {
    if (!audioContextRef.current) {
      audioContextRef.current = new (globalThis.AudioContext || (globalThis as any).webkitAudioContext)();
    }
    return audioContextRef.current;
  };

  /**
   * Synthesize a notification sound
   */
  const playSound = (type: 'success' | 'error' | 'info') => {
    const ctx = initAudio();
    if (ctx.state === 'suspended') {
      ctx.resume().catch((e: unknown) => logger.debug('[Audio] Could not resume context', { error: e }));
    }

    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.connect(gain);
    gain.connect(ctx.destination);

    // Disconnect nodes after playback to assist garbage collection
    osc.onended = () => {
      osc.disconnect();
      gain.disconnect();
    };

    const now = ctx.currentTime;

    switch (type) {
      case 'success':
        // A pleasant high-pitched ding (sine wave)
        osc.type = 'sine';
        osc.frequency.setValueAtTime(880, now); // A5
        osc.frequency.exponentialRampToValueAtTime(1320, now + 0.1); // Slide up to E6
        gain.gain.setValueAtTime(0.5, now); // Increased from 0.1
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.5); // Fast fade
        osc.start(now);
        osc.stop(now + 0.5);
        break;
        
      case 'error':
        // A low-pitched dual alert
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(440, now); // A4
        osc.frequency.setValueAtTime(330, now + 0.1); // Slide down to E4
        gain.gain.setValueAtTime(0.4, now); // Increased from 0.15
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.4);
        osc.start(now);
        osc.stop(now + 0.4);
        break;

      case 'info':
        // A neutral chime
        osc.type = 'sine';
        osc.frequency.setValueAtTime(660, now); // E5
        gain.gain.setValueAtTime(0.3, now); // Increased from 0.05
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.3);
        osc.start(now);
        osc.stop(now + 0.3);
        break;
    }
  };

  // React to notification signals
  useEffect(() => {
    if (!lastNotification) return;

    try {
      playSound(lastNotification.type);
      logger.debug(`[Audio] Synthesized ${lastNotification.type} notification`);
    } catch (error) {
      // Autoplay policy usually blocks this until first user interaction
      logger.debug(`[Audio] Playback blocked by browser policy (no user interaction yet)`);
    }
  }, [lastNotification]);
}
