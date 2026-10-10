import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { APPROVED_FLAG } from './usePendingApprovalPolling';

/** Shows the "account approved" toast once after the approval reload triggered by usePendingApprovalPolling. */
export function useApprovalNotice() {
  const { t } = useTranslation();

  useEffect(() => {
    let approved = false;
    try {
      approved = sessionStorage.getItem(APPROVED_FLAG) === '1';
      sessionStorage.removeItem(APPROVED_FLAG);
    } catch {
      // storage unavailable — no notice
    }
    if (approved) {
      toast.success(t('account.approved.toast'));
    }
  }, [t]);
}
