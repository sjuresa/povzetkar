import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { toast } from 'sonner';
import { useApprovalNotice } from './useApprovalNotice';
import { APPROVED_FLAG } from './usePendingApprovalPolling';

vi.mock('sonner', () => ({ toast: { success: vi.fn() } }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));

describe('useApprovalNotice', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('shows the approval toast once and clears the flag', () => {
    sessionStorage.setItem(APPROVED_FLAG, '1');

    renderHook(() => useApprovalNotice());
    renderHook(() => useApprovalNotice());

    expect(toast.success).toHaveBeenCalledTimes(1);
    expect(toast.success).toHaveBeenCalledWith('account.approved.toast');
    expect(sessionStorage.getItem(APPROVED_FLAG)).toBeNull();
  });

  it('shows nothing without the flag', () => {
    renderHook(() => useApprovalNotice());

    expect(toast.success).not.toHaveBeenCalled();
  });
});
