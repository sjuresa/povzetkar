import { useState, useEffect } from 'react';
import { X, Calendar, Play, Loader2, Info, Settings2, ShieldCheck, FileText } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useThemeStore } from '../../store/themeStore';
import type { Topic } from '../../api/generated/model';
import { usePostApiV1OrchestratorRun } from '../../api/generated/orchestrator/orchestrator';
import { useOnboardingStore } from '../../store/onboardingStore';

interface TriggerOrchestrationModalProps {
  isOpen: boolean;
  onClose: () => void;
  topic: Topic;
  onSuccess?: () => void;
}

export function TriggerOrchestrationModal({ isOpen, onClose, topic, onSuccess }: TriggerOrchestrationModalProps) {
  const { t } = useTranslation();
  const [startDate, setStartDate] = useState<string>(new Date().toISOString().split('T')[0]);
  const [endDate, setEndDate] = useState<string>(new Date().toISOString().split('T')[0]);
  const [processPending, setProcessPending] = useState(true);
  const [summarize, setSummarize] = useState(true);
  const [createBriefing, setCreateBriefing] = useState(true);
  const [previewOnly, setPreviewOnly] = useState(false);
  const { theme } = useThemeStore();

  const triggerOrchestration = usePostApiV1OrchestratorRun();

  if (!isOpen) return null;

  const handleRun = async () => {
    try {
      await triggerOrchestration.mutateAsync({
        data: {
          startDate,
          endDate,
          processPending,
          summarize,
          createBriefing,
          previewOnly,
          topicId: topic.id
        }
      });
      useOnboardingStore.getState().markStepComplete('trigger');
      onSuccess?.();
      onClose();
    } catch (error) {
      console.error('Failed to trigger orchestration:', error);
    }
  };

  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [onClose]);

  const isTriggering = triggerOrchestration.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-300">
      <div className="bg-[var(--color-surface-elevated)] w-full max-w-xl rounded-[3rem] shadow-3xl border border-[var(--color-border-subtle)] overflow-hidden flex flex-col transition-all duration-500 scale-in shadow-blue-500/10">
        {/* Header */}
        <div className="p-6 border-b border-[var(--color-border-subtle)]/50 flex items-center justify-between bg-[var(--color-background-alt)]/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-900/20 flex items-center justify-center">
              <Play className="w-5 h-5 text-blue-600 dark:text-blue-400 fill-current" />
            </div>
            <div>
            <h2 className="text-xl font-black text-[var(--color-text-primary)] italic tracking-tight">
                {t('topics.form.trigger_modal.title')}
              </h2>
              <p className="text-xs text-[var(--color-text-secondary)] font-medium">
                {t('topics.form.trigger_modal.subtitle', { name: topic.name })}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 hover:bg-[var(--color-surface-hover)] rounded-xl transition-colors"
          >
            <X className="w-5 h-5 text-[var(--color-text-secondary)]" />
          </button>
        </div>

        <div className="p-8 space-y-8">
          {/* Date Range Section */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 text-xs font-black uppercase tracking-widest text-[var(--color-text-secondary)]/60 italic">
              <Calendar className="w-3 h-3" />
              {t('topics.form.trigger_modal.date_range_label')}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase ml-1">
                  {t('topics.form.trigger_modal.start_date_label')}
                </label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  style={{ colorScheme: theme }}
                  className="w-full px-5 py-4 bg-[var(--color-background-base)] border border-[var(--color-border-subtle)] rounded-2xl focus:ring-4 focus:ring-blue-500/20 focus:border-blue-500 text-sm text-[var(--color-text-primary)] transition-all font-bold"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase ml-1">
                  {t('topics.form.trigger_modal.end_date_label')}
                </label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  style={{ colorScheme: theme }}
                  className="w-full px-5 py-4 bg-[var(--color-background-base)] border border-[var(--color-border-subtle)] rounded-2xl focus:ring-4 focus:ring-blue-500/20 focus:border-blue-500 text-sm text-[var(--color-text-primary)] transition-all font-bold"
                />
              </div>
            </div>
          </section>

          {/* Options Section */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 text-xs font-black uppercase tracking-widest text-[var(--color-text-secondary)]/60 italic">
              <Settings2 className="w-3 h-3" />
              {t('topics.form.trigger_modal.options_label')}
            </div>
            
            <div className="grid gap-3">
              {/* Option: Process Pending */}
              <label className={`flex items-start gap-4 p-4 rounded-2xl border transition-colors group ${
                previewOnly 
                  ? 'bg-[var(--color-background-alt)]/30 border-[var(--color-border-subtle)]/50 opacity-50 cursor-not-allowed' 
                  : 'border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={processPending && !previewOnly}
                    onChange={(e) => setProcessPending(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded-lg border-[var(--color-border-subtle)] bg-[var(--color-background-alt)] text-blue-600 focus:ring-blue-500/20 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-[var(--color-text-primary)]">
                      {t('topics.form.trigger_modal.process_pending_label')}
                    </span>
                    <ShieldCheck className="w-3 h-3 text-green-500 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                  <p className="text-xs text-[var(--color-text-secondary)] leading-relaxed">
                    {t('topics.form.trigger_modal.process_pending_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Summarize */}
              <label className={`flex items-start gap-4 p-4 rounded-2xl border transition-colors group ${
                previewOnly 
                  ? 'bg-[var(--color-background-alt)]/30 border-[var(--color-border-subtle)]/50 opacity-50 cursor-not-allowed' 
                  : 'border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={summarize && !previewOnly}
                    onChange={(e) => setSummarize(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded-lg border-[var(--color-border-subtle)] bg-[var(--color-background-alt)] text-blue-600 focus:ring-blue-500/20 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-[var(--color-text-primary)]">
                      {t('topics.form.trigger_modal.summarize_label')}
                    </span>
                    <FileText className="w-3 h-3 text-blue-500 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                  <p className="text-xs text-[var(--color-text-secondary)] leading-relaxed">
                    {t('topics.form.trigger_modal.summarize_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Briefing */}
              <label className={`flex items-start gap-4 p-4 rounded-2xl border transition-colors group ${
                previewOnly 
                  ? 'bg-[var(--color-background-alt)]/30 border-[var(--color-border-subtle)]/50 opacity-50 cursor-not-allowed' 
                  : 'border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={createBriefing && !previewOnly}
                    onChange={(e) => setCreateBriefing(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded-lg border-[var(--color-border-subtle)] bg-[var(--color-background-alt)] text-blue-600 focus:ring-blue-500/20 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-[var(--color-text-primary)]">
                      {t('topics.form.trigger_modal.create_briefing_label')}
                    </span>
                    <Play className="w-3 h-3 text-purple-500 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                  <p className="text-xs text-[var(--color-text-secondary)] leading-relaxed">
                    {t('topics.form.trigger_modal.create_briefing_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Preview Only */}
              <label className="flex items-start gap-4 p-4 rounded-2xl border border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]/20 hover:bg-[var(--color-surface-hover)] cursor-pointer transition-colors group">
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={previewOnly}
                    onChange={(e) => setPreviewOnly(e.target.checked)}
                    className="w-5 h-5 rounded-lg border-[var(--color-border-subtle)] bg-[var(--color-background-alt)] text-blue-600 focus:ring-blue-500/20"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-[var(--color-text-primary)]">
                      {t('topics.form.trigger_modal.preview_only_label')}
                    </span>
                    <Info className="w-3 h-3 text-blue-500" />
                  </div>
                  <p className="text-xs text-[var(--color-text-secondary)] leading-relaxed">
                    {t('topics.form.trigger_modal.preview_only_tip')}
                  </p>
                </div>
              </label>
            </div>
          </section>
        </div>

        {/* Footer */}
        <div className="p-6 border-t border-[var(--color-border-subtle)] bg-[var(--color-background-alt)]/50 flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 py-4 text-sm font-bold text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
          >
            {t('common.cancel')}
          </button>
          <button
            onClick={() => { void handleRun(); }}
            disabled={isTriggering}
            className="flex-[2] flex items-center justify-center gap-2 py-4 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white rounded-2xl text-sm font-bold shadow-lg shadow-blue-500/20 transition-all hover:scale-[1.02] active:scale-[0.98]"
          >
            {isTriggering ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                {t('topics.form.trigger_modal.triggering')}
              </>
            ) : (
              <>
                <Play className="w-4 h-4 fill-current" />
                {t('topics.form.trigger_modal.submit_button')}
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
