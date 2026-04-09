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
      <div className="bg-[var(--color-bg-primary)] w-full max-w-xl rounded-[28px] border border-[var(--color-border-primary)] overflow-hidden flex flex-col transition-all duration-300 scale-in">
        {/* Header */}
        <div className="p-6 border-b border-[var(--color-border-primary)] flex items-center justify-between bg-[var(--color-bg-secondary)]">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] flex items-center justify-center">
              <Play className="w-6 h-6 text-[var(--color-action-primary)] fill-current"  strokeWidth={1.5}/>
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
            className="p-2 hover:bg-[var(--color-bg-secondary)] rounded-xl transition-colors border border-transparent hover:border-[var(--color-border-primary)]"
          >
            <X className="w-5 h-5 text-[var(--color-text-secondary)]"  strokeWidth={1.5}/>
          </button>
        </div>

        <div className="p-8 space-y-8">
          {/* Date Range Section */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 text-xs font-black uppercase tracking-widest text-[var(--color-text-secondary)]/60 italic">
              <Calendar className="w-3 h-3"  strokeWidth={1.5}/>
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
                  className="w-full px-5 py-4 bg-transparent border border-[var(--color-border-primary)] rounded-xl focus:ring-1 focus:ring-[var(--color-ring-focus)] focus:border-[var(--color-action-primary)] text-sm text-[var(--color-text-primary)] transition-all font-black uppercase tracking-tighter outline-none"
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
                  className="w-full px-5 py-4 bg-transparent border border-[var(--color-border-primary)] rounded-xl focus:ring-1 focus:ring-[var(--color-ring-focus)] focus:border-[var(--color-action-primary)] text-sm text-[var(--color-text-primary)] transition-all font-black uppercase tracking-tighter outline-none"
                />
              </div>
            </div>
          </section>

          {/* Options Section */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 text-xs font-black uppercase tracking-widest text-[var(--color-text-secondary)]/60 italic">
              <Settings2 className="w-3 h-3"  strokeWidth={1.5}/>
              {t('topics.form.trigger_modal.options_label')}
            </div>
            
            <div className="grid gap-3">
              {/* Option: Process Pending */}
              <label className={`flex items-start gap-4 p-4 rounded-xl md:rounded-[28px] border transition-all group ${
                previewOnly 
                  ? 'bg-[var(--color-bg-secondary)]/30 border-[var(--color-border-primary)]/50 opacity-50 cursor-not-allowed' 
                  : 'border-[var(--color-border-primary)] hover:border-[var(--color-action-primary)] cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={processPending && !previewOnly}
                    onChange={(e) => setProcessPending(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded border-[var(--color-border-primary)] bg-[var(--color-bg-secondary)] accent-[var(--color-action-primary)] focus:ring-[var(--color-ring-focus)] focus:ring-offset-0 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-black uppercase tracking-tighter text-[var(--color-text-primary)]">
                      {t('topics.form.trigger_modal.process_pending_label')}
                    </span>
                    <ShieldCheck className="w-3.5 h-3.5 text-[var(--color-action-primary)] opacity-0 group-hover:opacity-100 transition-opacity"  strokeWidth={1.5}/>
                  </div>
                  <p className="text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] opacity-60 leading-relaxed">
                    {t('topics.form.trigger_modal.process_pending_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Summarize */}
              <label className={`flex items-start gap-4 p-4 rounded-xl md:rounded-[28px] border transition-all group ${
                previewOnly 
                  ? 'bg-[var(--color-bg-secondary)]/30 border-[var(--color-border-primary)]/50 opacity-50 cursor-not-allowed' 
                  : 'border-[var(--color-border-primary)] hover:border-[var(--color-action-primary)] cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={summarize && !previewOnly}
                    onChange={(e) => setSummarize(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded border-[var(--color-border-primary)] bg-[var(--color-bg-secondary)] accent-[var(--color-action-primary)] focus:ring-[var(--color-ring-focus)] focus:ring-offset-0 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-black uppercase tracking-tighter text-[var(--color-text-primary)]">
                      {t('topics.form.trigger_modal.summarize_label')}
                    </span>
                    <FileText className="w-3.5 h-3.5 text-[var(--color-action-primary)] opacity-0 group-hover:opacity-100 transition-opacity"  strokeWidth={1.5}/>
                  </div>
                  <p className="text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] opacity-60 leading-relaxed">
                    {t('topics.form.trigger_modal.summarize_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Briefing */}
              <label className={`flex items-start gap-4 p-4 rounded-xl md:rounded-[28px] border transition-all group ${
                previewOnly 
                  ? 'bg-[var(--color-bg-secondary)]/30 border-[var(--color-border-primary)]/50 opacity-50 cursor-not-allowed' 
                  : 'border-[var(--color-border-primary)] hover:border-[var(--color-action-primary)] cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={createBriefing && !previewOnly}
                    onChange={(e) => setCreateBriefing(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded border-[var(--color-border-primary)] bg-[var(--color-bg-secondary)] accent-[var(--color-action-primary)] focus:ring-[var(--color-ring-focus)] focus:ring-offset-0 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-black uppercase tracking-tighter text-[var(--color-text-primary)]">
                      {t('topics.form.trigger_modal.create_briefing_label')}
                    </span>
                    <Play className="w-3.5 h-3.5 text-[var(--color-action-primary)] opacity-0 group-hover:opacity-100 transition-opacity"  strokeWidth={1.5}/>
                  </div>
                  <p className="text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] opacity-60 leading-relaxed">
                    {t('topics.form.trigger_modal.create_briefing_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Preview Only */}
              <label className="flex items-start gap-4 p-4 rounded-xl md:rounded-[28px] border border-[var(--color-border-primary)] bg-[var(--color-bg-secondary)]/20 hover:border-[var(--color-action-primary)] cursor-pointer transition-all group">
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={previewOnly}
                    onChange={(e) => setPreviewOnly(e.target.checked)}
                    className="w-5 h-5 rounded border-[var(--color-border-primary)] bg-[var(--color-bg-secondary)] accent-[var(--color-action-primary)] focus:ring-[var(--color-ring-focus)] focus:ring-offset-0"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-black uppercase tracking-tighter text-[var(--color-text-primary)]">
                      {t('topics.form.trigger_modal.preview_only_label')}
                    </span>
                    <Info className="w-3.5 h-3.5 text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
                  </div>
                  <p className="text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] opacity-60 leading-relaxed">
                    {t('topics.form.trigger_modal.preview_only_tip')}
                  </p>
                </div>
              </label>
            </div>
          </section>
        </div>

        {/* Footer */}
        <div className="p-6 border-t border-[var(--color-border-primary)] bg-[var(--color-bg-secondary)] flex gap-4">
          <button
            onClick={onClose}
            className="flex-1 py-4 text-xs font-black uppercase tracking-widest text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-all active:scale-95"
          >
            {t('common.cancel')}
          </button>
          <button
            onClick={() => { void handleRun(); }}
            disabled={isTriggering}
            className="flex-[2] flex items-center justify-center gap-2 py-4 bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] disabled:bg-[var(--color-border-primary)] text-[var(--color-action-primary-text)] rounded-xl text-xs font-black uppercase tracking-widest transition-all active:scale-95"
          >
            {isTriggering ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin"  strokeWidth={1.5}/>
                {t('topics.form.trigger_modal.triggering')}
              </>
            ) : (
              <>
                <Play className="w-4 h-4 fill-current"  strokeWidth={1.5}/>
                {t('topics.form.trigger_modal.submit_button')}
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
