import { useEffect } from 'react';
import { useShallow } from 'zustand/react/shallow';
import { useOnboardingStore } from '../store/onboardingStore';
import type { AccountStatus } from '../api/generated/model';

/** Delays between retries of a failed load (e.g. during a deploy restart); the last one repeats. */
export const RETRY_DELAYS_MS = [5_000, 15_000, 60_000];

/**
 * Loads the onboarding guide state for an enabled account; other accounts have no guide (POV-216).
 * A failed load is retried with a growing delay until it succeeds, so the guide and the steps marked
 * meanwhile are not lost for the rest of the session.
 */
export function useOnboardingLoader(status: AccountStatus | undefined) {
  const { loaded, load } = useOnboardingStore(useShallow((state) => ({ loaded: state.loaded, load: state.load })));

  useEffect(() => {
    if (status !== 'ENABLED' || loaded) return;

    let cancelled = false;
    let attempt = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const run = async () => {
      await load();
      if (cancelled || useOnboardingStore.getState().loaded) return;
      const delay = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)];
      attempt += 1;
      timer = setTimeout(() => void run(), delay);
    };
    void run();

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [status, loaded, load]);
}
