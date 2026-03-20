import { 
  useGetApiV1Topics, 
  usePostApiV1TopicsIdCommandsGenerateBriefing as useTriggerTopic,
  usePostApiV1TopicsIdCommandsGenerateSummaries as useTriggerSummaries,
  deleteApiV1TopicsId,
  getGetApiV1TopicsQueryKey
} from '../../api/generated/topics/topics';
import type { Topic } from '../../api/generated/model';
import { toast } from 'sonner';
import { Play, Pencil, Trash2, Calendar, CheckCircle2, XCircle, Loader2 } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';

interface TopicListProps {
  onEdit: (topic: Topic) => void;
}

export const TopicList: React.FC<TopicListProps> = ({ onEdit }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: topicsResponse, isLoading, error } = useGetApiV1Topics({ page: 0, size: 100 });
  const triggerTopic = useTriggerTopic();
  const triggerSummaries = useTriggerSummaries();

  console.log('TopicList: topicsResponseRaw:', topicsResponse);
  console.log('TopicList: error:', error);

  const handleDelete = async (topic: Topic) => {
    if (!globalThis.confirm(t('topics.delete_confirm', { name: topic.name }))) return;

    try {
      await deleteApiV1TopicsId(topic.id!, { version: topic.version } as any);
      toast.success(t('topics.delete_success'));
      queryClient.invalidateQueries({ queryKey: getGetApiV1TopicsQueryKey({ page: 0, size: 100 }) });
    } catch (error: any) {
      const message = error.response?.data?.error?.message || error.message;
      toast.error(t('topics.delete_failed', { message }));
    }
  };

  const handleTrigger = async (topicId: number) => {
    try {
      await triggerTopic.mutateAsync({ id: topicId });
      toast.success(t('topics.trigger_success'));
    } catch (error: any) {
      const message = error.response?.data?.error?.message || error.message;
      toast.error(t('topics.trigger_failed', { message }));
    }
  };

  const handleTriggerSummaries = async (topicId: number) => {
    try {
      await triggerSummaries.mutateAsync({ id: topicId });
      toast.success(t('topics.trigger_success')); // Or a specific message if available
    } catch (error: any) {
      const message = error.response?.data?.error?.message || error.message;
      toast.error(t('topics.trigger_failed', { message }));
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-20 space-y-4">
        <Loader2 className="w-10 h-10 text-blue-500 animate-spin" />
        <p className="text-[var(--color-text-secondary)] font-medium">{t('common.loading')}</p>
      </div>
    );
  }

  const topicsResponseValue = topicsResponse as any;
  const topics: Topic[] = 
    (Array.isArray(topicsResponseValue) ? topicsResponseValue : undefined) || 
    topicsResponseValue?.items || 
    topicsResponseValue?.data?.items || 
    (Array.isArray(topicsResponseValue?.data) ? topicsResponseValue.data : []) || 
    [];

  if (topics.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center p-20 bg-[var(--color-background-alt)] rounded-3xl border border-dashed border-[var(--color-border-subtle)]">
        <div className="w-16 h-16 rounded-2xl bg-[var(--color-surface-deep)] flex items-center justify-center mb-6">
          <Calendar className="w-8 h-8 text-[var(--color-text-secondary)] opacity-50" />
        </div>
        <h3 className="text-xl font-semibold text-[var(--color-text-primary)] mb-2">{t('topics.empty_title')}</h3>
        <p className="text-[var(--color-text-secondary)] mb-8 max-w-sm text-center font-medium">
          {t('topics.empty_desc')}
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      {topics.map((topic: Topic) => (
        <div 
          key={topic.id}
          className="group relative bg-[var(--color-surface-elevated)] rounded-3xl border border-[var(--color-border-subtle)] p-6 transition-all hover:shadow-2xl hover:shadow-blue-500/10 hover:-translate-y-1"
        >
          <div className="flex items-start justify-between mb-4">
            <div className="flex items-center space-x-3">
              <div className={`p-2 rounded-xl ${topic.enabled ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30' : 'bg-[var(--color-background-alt)] text-[var(--color-text-secondary)]'}`}>
                {topic.enabled ? <CheckCircle2 className="w-5 h-5" /> : <XCircle className="w-5 h-5" />}
              </div>
              <h3 className="text-lg font-bold text-[var(--color-text-primary)] group-hover:text-blue-500 transition-colors truncate max-w-[140px]">
                {topic.name}
              </h3>
            </div>
            
            <div className="flex items-center space-x-1">
              <button 
                onClick={() => onEdit(topic)}
                className="p-2 text-[var(--color-text-secondary)] hover:text-blue-500 transition-colors rounded-lg hover:bg-blue-500/10"
                title={t('common.edit')}
              >
                <Pencil className="w-4 h-4" />
              </button>
              <button 
                onClick={() => handleDelete(topic)}
                className="p-2 text-[var(--color-text-secondary)] hover:text-red-500 transition-colors rounded-lg hover:bg-red-500/10"
                title={t('common.delete')}
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="space-y-3 mb-6">
            <div className="flex items-center space-x-2 text-sm text-[var(--color-text-secondary)]">
              <Calendar className="w-4 h-4 opacity-70" />
              <span>{t('topics.last_run')}: {topic.lastRunDate ? formatDistanceToNow(new Date(topic.lastRunDate), { addSuffix: true }) : t('topics.never')}</span>
            </div>
            <div className="text-xs font-mono bg-[var(--color-surface-deep)] p-2 rounded-lg text-[var(--color-text-secondary)] truncate">
              {topic.promptTemplate || t('topics.default_prompt')}
            </div>
            <div className="text-xs text-[var(--color-text-secondary)] opacity-70 font-medium">
              {t('topics.linked_sources', { count: topic.contentSourceIds?.length || 0 })}
            </div>
          </div>

          <div className="flex flex-col space-y-2">
            <button
              onClick={() => handleTrigger(topic.id as number)}
              disabled={triggerTopic.isPending || triggerSummaries.isPending}
              className="w-full py-3 bg-[var(--color-text-primary)] text-[var(--color-background-base)] rounded-2xl flex items-center justify-center space-x-3 font-semibold transition-all hover:bg-blue-600 hover:text-white hover:scale-[1.02] active:scale-95 disabled:opacity-50"
            >
              {triggerTopic.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4 fill-current" />}
              <span>{t('topics.run_briefing')}</span>
            </button>
            <button
              onClick={() => handleTriggerSummaries(topic.id as number)}
              disabled={triggerTopic.isPending || triggerSummaries.isPending}
              className="w-full py-2 bg-[var(--color-background-alt)] text-[var(--color-text-primary)] rounded-xl flex items-center justify-center space-x-2 text-sm font-medium transition-all hover:bg-blue-50 dark:hover:bg-blue-900/20 border border-[var(--color-border-subtle)] disabled:opacity-50"
            >
              {triggerSummaries.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
              <span>{t('topics.generate_summaries')}</span>
            </button>
          </div>
        </div>
      ))}
    </div>
  );
};
