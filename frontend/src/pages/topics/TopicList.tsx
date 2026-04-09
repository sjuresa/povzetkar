import React from 'react';
import { useTranslation } from 'react-i18next';
import { 
  Edit2, 
  Trash2, 
  Play, 
  Settings,
  AlertCircle,
  Clock,
  CheckCircle2,
  XCircle
} from 'lucide-react';
import { useGetApiV1Topics, deleteApiV1TopicsId } from '../../api/generated/topics/topics';
import type { Topic } from '../../api/generated/model';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { TriggerOrchestrationModal } from '../../components/topics/TriggerOrchestrationModal';

interface TopicListProps {
  onEdit?: (topic: Topic) => void;
  searchQuery?: string;
  showActiveOnly?: boolean;
}

export const TopicList: React.FC<TopicListProps> = ({ 
  onEdit, 
  searchQuery = '', 
  showActiveOnly = false 
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [triggerTopic, setTriggerTopic] = React.useState<Topic | null>(null);

  const { data: topicsResponse, isLoading, error } = useGetApiV1Topics({ page: 0, size: 100 });

  const handleDelete = async (topic: Topic) => {
    if (!window.confirm(t('topics.list.delete_confirm', { name: topic.name }))) {
      return;
    }

    try {
      await deleteApiV1TopicsId(topic.id!, { 
        version: topic.version 
      });
      toast.success(t('topics.list.delete_success'));
      void queryClient.invalidateQueries({ queryKey: ['/api/v1/topics'] });
    } catch (err: unknown) {
      const errorObj = err as { message?: string };
      toast.error(t('topics.list.delete_failed', { message: errorObj.message }));
    }
  };

  const handleRun = (topic: Topic) => {
    setTriggerTopic(topic);
  };

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <div key={i} className="bg-[var(--color-bg-secondary)] rounded-[28px] border border-[var(--color-border-primary)] p-8 animate-pulse">
            <div className="flex items-start justify-between mb-8">
              <div className="w-12 h-12 bg-[var(--color-skeleton)] rounded-xl md:rounded-[28px]" />
              <div className="flex space-x-2">
                <div className="w-10 h-10 bg-[var(--color-skeleton)] rounded-xl" />
                <div className="w-10 h-10 bg-[var(--color-skeleton)] rounded-xl" />
              </div>
            </div>
            <div className="h-8 bg-[var(--color-skeleton)] rounded-xl w-3/4 mb-4" />
            <div className="space-y-2 mb-8">
              <div className="h-4 bg-[var(--color-skeleton)] rounded w-1/2" />
              <div className="h-4 bg-[var(--color-skeleton)] rounded w-2/3" />
            </div>
            <div className="pt-8 border-t border-[var(--color-border-primary)] flex justify-between items-center">
              <div className="h-6 bg-[var(--color-skeleton)] rounded-full w-24" />
              <div className="h-4 bg-[var(--color-skeleton)] rounded w-12" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-[var(--color-bg-secondary)] border border-[var(--color-status-error)] p-8 rounded-[28px] flex items-start space-x-6">
        <AlertCircle className="w-8 h-8 text-[var(--color-status-error)] mt-1"  strokeWidth={1.5}/>
        <div>
          <h3 className="text-2xl text-[var(--color-text-primary)] font-black mb-2 uppercase tracking-tighter">{t('common.error')}</h3>
          <p className="text-[var(--color-text-secondary)] text-lg">{(error as { message?: string })?.message}</p>
        </div>
      </div>
    );
  }

  const topics: Topic[] = topicsResponse?.data?.items ?? [];

  const filteredTopics = topics.filter(topic => {
    const matchesSearch = topic.name?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesStatus = showActiveOnly ? topic.enabled === true : true;
    return matchesSearch && matchesStatus;
  });

  if (topics.length === 0) {
    return (
      <div className="text-center py-32 bg-[var(--color-bg-secondary)] rounded-[28px] border border-[var(--color-border-primary)]">
        <div className="w-24 h-24 bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] rounded-full flex items-center justify-center mx-auto mb-8">
          <Settings className="w-12 h-12 text-[var(--color-text-secondary)]"  strokeWidth={1.5}/>
        </div>
        <h3 className="text-3xl font-black text-[var(--color-text-primary)] mb-4 uppercase tracking-tighter">{t('topics.list.empty_title')}</h3>
        <p className="text-[var(--color-text-secondary)] text-xl max-w-md mx-auto">{t('topics.list.empty_description')}</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
      {filteredTopics.map((topic) => (
        <div 
          key={topic.id}
          className="group relative bg-[var(--color-bg-primary)] rounded-[28px] border border-[var(--color-border-primary)] p-8 transition-all duration-300 hover:border-[var(--color-action-primary)] outline-none focus-within:ring-2 focus-within:ring-[var(--color-ring-focus)] focus-within:ring-offset-4 focus-within:ring-offset-[var(--color-bg-primary)]"
        >
          <div className="flex items-start justify-between mb-8">
            <div className={`p-4 rounded-xl transition-colors duration-300 border border-[var(--color-border-primary)] ${topic.enabled ? 'bg-[var(--color-bg-primary)] text-[var(--color-action-primary)]' : 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]'}`}>
              <Settings className="w-6 h-6"  strokeWidth={1.5}/>
            </div>
            <div className="flex items-center space-x-1 opacity-10 md:opacity-0 group-hover:opacity-100 transition-all duration-300 justify-end shrink-0">
              <button 
                onClick={() => { handleRun(topic); }}
                className="p-2.5 hover:bg-[var(--color-action-primary)] hover:text-[var(--color-action-primary-text)] text-[var(--color-action-primary)] rounded-xl transition-all active:scale-95"
                title={t('topics.list.run_now')}
              >
                <Play className="w-5 h-5 fill-current"  strokeWidth={1.5}/>
              </button>
              <button 
                onClick={() => { onEdit?.(topic); }}
                className="p-2.5 hover:bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] rounded-xl transition-all active:scale-95"
                title={t('common.edit')}
              >
                <Edit2 className="w-5 h-5"  strokeWidth={1.5}/>
              </button>
              <button 
                onClick={() => { void handleDelete(topic); }}
                className="p-2.5 hover:bg-[var(--color-status-error)] hover:text-[var(--color-action-primary-text)] text-[var(--color-status-error)] rounded-xl transition-all active:scale-95"
                title={t('common.delete')}
              >
                <Trash2 className="w-5 h-5"  strokeWidth={1.5}/>
              </button>
            </div>
          </div>

          <div className="mb-8">
            <h3 className="text-2xl font-black text-[var(--color-text-primary)] mb-3 group-hover:text-[var(--color-action-primary)] transition-colors uppercase tracking-tighter">
              {topic.name}
            </h3>
            <div className="flex flex-col space-y-3">
              <div className="flex items-center text-[var(--color-text-secondary)] font-bold text-xs uppercase tracking-tight">
                <Clock className="w-4 h-4 mr-3 text-[var(--color-text-secondary)] opacity-60"  strokeWidth={1.5}/>
                {topic.lastRunDate ? new Date(topic.lastRunDate).toLocaleDateString() : t('topics.list.never_run')}
              </div>
              <div className="flex items-center text-[var(--color-text-secondary)] font-bold text-xs uppercase tracking-tight">
                <CheckCircle2 className="w-4 h-4 mr-3 text-[var(--color-text-secondary)] opacity-60"  strokeWidth={1.5}/>
                {t('topics.list.sources_count', { count: topic.contentSources?.length || 0 })}
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between pt-8 border-t border-[var(--color-border-primary)]">
            <div className="flex items-center">
              {topic.enabled ? (
                <span className="flex items-center text-[10px] font-black uppercase tracking-wider text-[var(--color-action-primary)] bg-[var(--color-bg-primary)] px-4 py-1.5 rounded-full border border-[var(--color-action-primary)]/20 shadow-sm">
                  <CheckCircle2 className="w-3.5 h-3.5 mr-2"  strokeWidth={1.5}/>
                  {t('topics.status.active')}
                </span>
              ) : (
                <span className="flex items-center text-[10px] font-black uppercase tracking-wider text-[var(--color-text-secondary)] bg-[var(--color-bg-secondary)] px-4 py-1.5 rounded-full border border-[var(--color-border-primary)]">
                  <XCircle className="w-3.5 h-3.5 mr-2"  strokeWidth={1.5}/>
                  {t('topics.status.inactive')}
                </span>
              )}
            </div>
            <div className="flex flex-col items-end opacity-40">
              <span className="text-[10px] font-black uppercase text-[var(--color-text-secondary)] tracking-tighter mb-0.5">Version</span>
              <span className="text-xs text-[var(--color-text-primary)] font-mono font-bold">
                {topic.version}
              </span>
            </div>
          </div>
        </div>
      ))}
      {triggerTopic && (
        <TriggerOrchestrationModal
          isOpen={!!triggerTopic}
          onClose={() => setTriggerTopic(null)}
          topic={triggerTopic}
          onSuccess={() => {
            toast.success(t('topics.list.run_success'));
            void queryClient.invalidateQueries({ queryKey: ['/api/v1/topics'] });
          }}
        />
      )}
    </div>
  );
};
