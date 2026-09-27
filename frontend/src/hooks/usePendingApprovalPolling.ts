import { useEffect } from 'react';
import { getApiV1UsersMe } from '../api/generated/users/users';
import { useAuthStore } from '../store/authStore';
import type { AccountStatus, UserDTO } from '../api/generated/model';

/** sessionStorage flag that survives the approval reload; read by {@link useApprovalNotice}. */
export const APPROVED_FLAG = 'povzetkar_account_approved';
export const POLL_INTERVAL_MS = 60_000;

/**
 * While the account awaits approval, re-checks its status every minute (visible tab only) and
 * immediately when the tab becomes visible again (POV-216). On approval the page reloads once, so the
 * query cache (demo content), the SSE stream and the onboarding guide all start fresh; a rejection or
 * disablement updates the banner in place. For every other status the hook does nothing.
 *
 * Calls the generated client directly rather than `checkAuth`, which logs the UI out on any error;
 * failed checks are ignored (a 401 is handled by the API client's interceptor).
 */
export function usePendingApprovalPolling(status: AccountStatus | undefined) {
  useEffect(() => {
    if (status !== 'PENDING_APPROVAL') return;

    let stopped = false;
    let inFlight = false;

    const check = async () => {
      if (stopped || inFlight || document.visibilityState !== 'visible') return;
      inFlight = true;
      try {
        const user = (await getApiV1UsersMe()).data as UserDTO;
        if (stopped || !user?.accountStatus || user.accountStatus === 'PENDING_APPROVAL') return;
        stop();
        if (user.accountStatus === 'ENABLED') {
          try {
            sessionStorage.setItem(APPROVED_FLAG, '1');
          } catch {
            // storage unavailable — reload without the notice
          }
          globalThis.location.reload();
        } else {
          useAuthStore.setState({ user });
        }
      } catch {
        // transient failure — the next tick retries
      } finally {
        inFlight = false;
      }
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') void check();
    };
    const timer = setInterval(() => void check(), POLL_INTERVAL_MS);
    document.addEventListener('visibilitychange', onVisibilityChange);

    function stop() {
      stopped = true;
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    }

    return stop;
  }, [status]);
}
