import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { usePendingApprovalPolling, APPROVED_FLAG, POLL_INTERVAL_MS } from './usePendingApprovalPolling';
import { getApiV1UsersMe } from '../api/generated/users/users';
import { useAuthStore } from '../store/authStore';
import type { AccountStatus, UserDTO } from '../api/generated/model';

vi.mock('../api/generated/users/users', () => ({
  getApiV1UsersMe: vi.fn(),
}));

let visibility: DocumentVisibilityState = 'visible';
const reload = vi.fn();

function setVisibility(state: DocumentVisibilityState) {
  visibility = state;
  document.dispatchEvent(new Event('visibilitychange'));
}

function serverReturns(accountStatus: AccountStatus) {
  vi.mocked(getApiV1UsersMe).mockResolvedValue({ data: { id: 1, accountStatus } as UserDTO } as never);
}

describe('usePendingApprovalPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    visibility = 'visible';
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
    vi.stubGlobal('location', { ...globalThis.location, reload });
    sessionStorage.clear();
    useAuthStore.setState({ user: { id: 1, accountStatus: 'PENDING_APPROVAL' } as UserDTO });
    serverReturns('PENDING_APPROVAL');
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('is inert for an enabled account — no timer, no listener, no request', () => {
    const addListener = vi.spyOn(document, 'addEventListener');

    renderHook(() => usePendingApprovalPolling('ENABLED'));
    vi.advanceTimersByTime(POLL_INTERVAL_MS * 3);
    setVisibility('visible');

    expect(vi.getTimerCount()).toBe(0);
    expect(addListener).not.toHaveBeenCalledWith('visibilitychange', expect.anything());
    expect(getApiV1UsersMe).not.toHaveBeenCalled();
  });

  it('checks the status every interval while the tab is visible', async () => {
    renderHook(() => usePendingApprovalPolling('PENDING_APPROVAL'));

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
    expect(getApiV1UsersMe).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
    expect(getApiV1UsersMe).toHaveBeenCalledTimes(2);
  });

  it('skips checks while the tab is hidden and checks immediately when it becomes visible', async () => {
    renderHook(() => usePendingApprovalPolling('PENDING_APPROVAL'));
    setVisibility('hidden');

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2);
    expect(getApiV1UsersMe).not.toHaveBeenCalled();

    setVisibility('visible');
    await vi.advanceTimersByTimeAsync(0);
    expect(getApiV1UsersMe).toHaveBeenCalledTimes(1);
  });

  it('reloads exactly once with the approval flag when the account becomes enabled', async () => {
    serverReturns('ENABLED');
    renderHook(() => usePendingApprovalPolling('PENDING_APPROVAL'));

    setVisibility('visible'); // a visibility check and interval ticks race each other
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3);

    expect(reload).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(APPROVED_FLAG)).toBe('1');
    expect(vi.getTimerCount()).toBe(0);
  });

  it.each(['REJECTED', 'DISABLED'] as const)(
    'reloads once without the approval flag when the account becomes %s',
    async (status) => {
      serverReturns(status);
      renderHook(() => usePendingApprovalPolling('PENDING_APPROVAL'));

      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3);

      expect(reload).toHaveBeenCalledTimes(1);
      expect(sessionStorage.getItem(APPROVED_FLAG)).toBeNull();
      expect(vi.getTimerCount()).toBe(0);
    }
  );

  it('ignores request errors without touching the auth store', async () => {
    vi.mocked(getApiV1UsersMe).mockRejectedValue(new Error('network'));
    const before = useAuthStore.getState().user;
    renderHook(() => usePendingApprovalPolling('PENDING_APPROVAL'));

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2);

    expect(getApiV1UsersMe).toHaveBeenCalledTimes(2);
    expect(useAuthStore.getState().user).toBe(before);
    expect(reload).not.toHaveBeenCalled();
  });

  it('stops on unmount', async () => {
    const { unmount } = renderHook(() => usePendingApprovalPolling('PENDING_APPROVAL'));
    unmount();

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2);
    setVisibility('visible');

    expect(getApiV1UsersMe).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });
});
