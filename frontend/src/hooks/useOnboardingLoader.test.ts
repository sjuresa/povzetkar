import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useOnboardingLoader, RETRY_DELAYS_MS } from './useOnboardingLoader';
import { useOnboardingStore } from '../store/onboardingStore';

const load = vi.fn();

describe('useOnboardingLoader', () => {
  beforeEach(() => {
    load.mockReset().mockResolvedValue(undefined);
    useOnboardingStore.setState({ loaded: false, load });
  });

  it('loads the guide state for an enabled account', () => {
    renderHook(() => useOnboardingLoader('ENABLED'));

    expect(load).toHaveBeenCalledTimes(1);
  });

  it('does not load for accounts without a guide', () => {
    renderHook(() => useOnboardingLoader('PENDING_APPROVAL'));
    renderHook(() => useOnboardingLoader('REJECTED'));
    renderHook(() => useOnboardingLoader('DISABLED'));
    renderHook(() => useOnboardingLoader(undefined));

    expect(load).not.toHaveBeenCalled();
  });

  describe('when loading fails', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('retries with a growing delay until the state is loaded', async () => {
      load
        .mockImplementationOnce(() => Promise.resolve()) // failure: load() logs and stays unloaded
        .mockImplementationOnce(() => Promise.resolve())
        .mockImplementationOnce(() => {
          useOnboardingStore.setState({ loaded: true });
          return Promise.resolve();
        });

      renderHook(() => useOnboardingLoader('ENABLED'));
      await vi.advanceTimersByTimeAsync(0);
      expect(load).toHaveBeenCalledTimes(1);

      await vi.advanceTimersByTimeAsync(RETRY_DELAYS_MS[0]);
      expect(load).toHaveBeenCalledTimes(2);

      await vi.advanceTimersByTimeAsync(RETRY_DELAYS_MS[1]);
      expect(load).toHaveBeenCalledTimes(3);

      await vi.advanceTimersByTimeAsync(RETRY_DELAYS_MS[2] * 3);
      expect(load).toHaveBeenCalledTimes(3);
      expect(vi.getTimerCount()).toBe(0);
    });

    it('stops retrying on unmount', async () => {
      load.mockImplementation(() => Promise.resolve());

      const { unmount } = renderHook(() => useOnboardingLoader('ENABLED'));
      await vi.advanceTimersByTimeAsync(0);
      unmount();
      await vi.advanceTimersByTimeAsync(RETRY_DELAYS_MS[2] * 3);

      expect(load).toHaveBeenCalledTimes(1);
    });
  });

  it('does not reload once loaded', () => {
    useOnboardingStore.setState({ loaded: true });

    renderHook(() => useOnboardingLoader('ENABLED'));

    expect(load).not.toHaveBeenCalled();
  });
});
