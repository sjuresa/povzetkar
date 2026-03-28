import { useState } from 'react';
import { X, Calendar, Play, Loader2, Info, Settings2, ShieldCheck, FileText } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Topic } from '../../api/generated/model';
import { usePostApiV1OrchestratorRun } from '../../api/generated/orchestrator/orchestrator';

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
      onSuccess?.();
      onClose();
    } catch (error) {
      console.error('Failed to trigger orchestration:', error);
    }
  };

  const isTriggering = triggerOrchestration.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-white dark:bg-[#1a1c1e] w-full max-w-xl rounded-3xl shadow-2xl border border-gray-200 dark:border-gray-800 overflow-hidden flex flex-col">
        {/* Header */}
        <div className="p-6 border-b border-gray-100 dark:border-gray-800 flex items-center justify-between bg-gray-50/50 dark:bg-gray-800/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-900/20 flex items-center justify-center">
              <Play className="w-5 h-5 text-blue-600 dark:text-blue-400 fill-current" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100 italic tracking-tight">
                {t('topics.form.trigger_modal.title')}
              </h2>
              <p className="text-xs text-gray-500 font-medium">
                {t('topics.form.trigger_modal.subtitle', { name: topic.name })}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 hover:bg-gray-200 dark:hover:bg-gray-800 rounded-xl transition-colors"
          >
            <X className="w-5 h-5 text-gray-500" />
          </button>
        </div>

        <div className="p-8 space-y-8">
          {/* Date Range Section */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 text-xs font-black uppercase tracking-widest text-gray-400 italic">
              <Calendar className="w-3 h-3" />
              {t('topics.form.trigger_modal.date_range_label')}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-gray-500 uppercase ml-1">
                  {t('topics.form.trigger_modal.start_date_label')}
                </label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full px-4 py-3 bg-gray-100 dark:bg-gray-900 border-none rounded-2xl focus:ring-2 focus:ring-blue-500/20 text-sm dark:text-gray-100"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-gray-500 uppercase ml-1">
                  {t('topics.form.trigger_modal.end_date_label')}
                </label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="w-full px-4 py-3 bg-gray-100 dark:bg-gray-900 border-none rounded-2xl focus:ring-2 focus:ring-blue-500/20 text-sm dark:text-gray-100"
                />
              </div>
            </div>
          </section>

          {/* Options Section */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 text-xs font-black uppercase tracking-widest text-gray-400 italic">
              <Settings2 className="w-3 h-3" />
              {t('topics.form.trigger_modal.options_label')}
            </div>
            
            <div className="grid gap-3">
              {/* Option: Process Pending */}
              <label className={`flex items-start gap-4 p-4 rounded-2xl border transition-colors group ${
                previewOnly 
                  ? 'bg-gray-50/50 dark:bg-gray-900/20 border-gray-100 dark:border-gray-800 opacity-50 cursor-not-allowed' 
                  : 'border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-900/40 cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={processPending && !previewOnly}
                    onChange={(e) => setProcessPending(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded-lg border-gray-300 dark:border-gray-700 text-blue-600 focus:ring-blue-500/20 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-gray-900 dark:text-gray-100">
                      {t('topics.form.trigger_modal.process_pending_label')}
                    </span>
                    <ShieldCheck className="w-3 h-3 text-green-500 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                  <p className="text-xs text-gray-500 leading-relaxed">
                    {t('topics.form.trigger_modal.process_pending_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Summarize */}
              <label className={`flex items-start gap-4 p-4 rounded-2xl border transition-colors group ${
                previewOnly 
                  ? 'bg-gray-50/50 dark:bg-gray-900/20 border-gray-100 dark:border-gray-800 opacity-50 cursor-not-allowed' 
                  : 'border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-900/40 cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={summarize && !previewOnly}
                    onChange={(e) => setSummarize(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded-lg border-gray-300 dark:border-gray-700 text-blue-600 focus:ring-blue-500/20 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-gray-900 dark:text-gray-100">
                      {t('topics.form.trigger_modal.summarize_label')}
                    </span>
                    <FileText className="w-3 h-3 text-blue-500 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                  <p className="text-xs text-gray-500 leading-relaxed">
                    {t('topics.form.trigger_modal.summarize_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Briefing */}
              <label className={`flex items-start gap-4 p-4 rounded-2xl border transition-colors group ${
                previewOnly 
                  ? 'bg-gray-50/50 dark:bg-gray-900/20 border-gray-100 dark:border-gray-800 opacity-50 cursor-not-allowed' 
                  : 'border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-900/40 cursor-pointer'
              }`}>
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={createBriefing && !previewOnly}
                    onChange={(e) => setCreateBriefing(e.target.checked)}
                    disabled={previewOnly}
                    className="w-5 h-5 rounded-lg border-gray-300 dark:border-gray-700 text-blue-600 focus:ring-blue-500/20 disabled:opacity-50"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-gray-900 dark:text-gray-100">
                      {t('topics.form.trigger_modal.create_briefing_label')}
                    </span>
                    <Play className="w-3 h-3 text-purple-500 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                  <p className="text-xs text-gray-500 leading-relaxed">
                    {t('topics.form.trigger_modal.create_briefing_tip')}
                  </p>
                </div>
              </label>

              {/* Option: Preview Only */}
              <label className="flex items-start gap-4 p-4 rounded-2xl bg-amber-50/50 dark:bg-amber-900/10 border border-amber-100 dark:border-amber-900/30 hover:bg-amber-50 dark:hover:bg-amber-900/20 cursor-pointer transition-colors group">
                <div className="pt-0.5">
                  <input
                    type="checkbox"
                    checked={previewOnly}
                    onChange={(e) => setPreviewOnly(e.target.checked)}
                    className="w-5 h-5 rounded-lg border-amber-300 dark:border-amber-700 text-amber-600 focus:ring-amber-500/20"
                  />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-amber-900 dark:text-amber-100">
                      {t('topics.form.trigger_modal.preview_only_label')}
                    </span>
                    <Info className="w-3 h-3 text-amber-500" />
                  </div>
                  <p className="text-xs text-amber-700/70 dark:text-amber-400/70 leading-relaxed">
                    {t('topics.form.trigger_modal.preview_only_tip')}
                  </p>
                </div>
              </label>
            </div>
          </section>
        </div>

        {/* Footer */}
        <div className="p-6 border-t border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/10 flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 py-4 text-sm font-bold text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-colors"
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
