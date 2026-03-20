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
  XCircle,
  Loader2
} from 'lucide-react';
import { useGetApiV1Topics, deleteApiV1TopicsId, postApiV1TopicsIdCommandsGenerateBriefing } from '../../api/generated/topics/topics';
import type { Topic, TopicPaginatedResponse } from '../../api/generated/model';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';

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

  const { data: topicsResponse, isLoading, error } = useGetApiV1Topics({ page: 0, size: 100 });

  const handleDelete = async (topiglobalThisic) => {
    if (!window.confirm(t('topics.list.delete_confirm', { name: topic.name }))) {
      return;
    }

    try {
      await deleteApiV1TopicsId(topic.id!, { 
        headers: { 'If-Match': `"${topic.version}"` } as HeadersInit 
      });
      toast.success(t('topics.list.delete_success'));
      void queryClient.invalidateQueries({ queryKey: ['getApiV1Topics'] });
    } catch (err: any) {
      toast.error(t('topics.list.delete_failed', { message: err.message }));
    }
  };

  const handleRun = async (topic: Topic) => {
    try {
      await postApiV1TopicsIdCommandsGenerateBriefing(topic.id!);
      toast.success(t('topics.list.run_success'));
    } catch (err: any) {
      toast.error(t('topics.list.run_failed', { message: err.message }));
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <Loader2 className="w-10 h-10 text-blue-600 animate-spin mb-4" />
        <p className="text-[var(--color-text-secondary)] animate-pulse font-bold">{t('common.loading')}</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-8 rounded-3xl flex items-start space-x-6">
        <AlertCircle className="w-8 h-8 text-red-600 mt-1" />
        <div>
          <h3 className="text-2xl text-red-900 dark:text-red-400 font-bold mb-2">{t('common.error')}</h3>
          <p className="text-red-700 dark:text-red-300 text-lg">{(error as any).message}</p>
        </div>
      </div>
    );
  }

  /**
   * Topics extraction simplified for cleaner logic and type safety.
   */
  const topics: Topic[] = (topicsResponse as unknown as TopicPaginatedResponse)?.items ?? [];

  const filteredTopics = topics.filter(topic => {
    const matchesSearch = topic.name?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesStatus = showActiveOnly ? topic.enabled === true : true;
    return matchesSearch && matchesStatus;
  });

  if (topics.length === 0) {
    return (
      <div className="text-center py-32 bg-[var(--color-background-alt)] rounded-[48px] border-2 border-dashed border-[var(--color-border-subtle)]">
        <div className="w-24 h-24 bg-blue-100 dark:bg-blue-900/30 rounded-full flex items-center justify-center mx-auto mb-8">
          <Settings className="w-12 h-12 text-blue-600" />
        </div>
        <h3 className="text-3xl font-bold text-[var(--color-text-primary)] mb-4">{t('topics.list.empty_title')}</h3>
        <p className="text-[var(--color-text-secondary)] text-xl max-w-md mx-auto">{t('topics.list.empty_description')}</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
      {filteredTopics.map((topic) => (
        <div 
          key={topic.id}
          className="group relative bg-[var(--color-surface-elevated)] rounded-[32px] border border-[var(--color-border-subtle)] p-8 transition-all duration-500 hover:shadow-2xl hover:shadow-blue-500/10 hover:-translate-y-2 hover:border-blue-500/30"
        >
          <div className="flex items-start justify-between mb-8">
            <div className={`p-4 rounded-2xl transition-colors duration-500 ${topic.enabled ? 'bg-green-100 dark:bg-green-900/30 text-green-600' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'}`}>
              <Settings className="w-6 h-6" />
            </div>
            <div className="flex items-center space-x-1 opacity-10 md:opacity-0 group-hover:opacity-100 transition-all duration-500 transform translate-y-1 group-hover:translate-y-0">
              <button 
                onClick={() => handleRun(topic)}
                className="p-3 hover:bg-blue-50 dark:hover:bg-blue-900/30 text-blue-600 rounded-xl transition-all hover:scale-110 active:scale-90"
                title={t('topics.list.run_now')}
              >
                <Play className="w-5 h-5 fill-current" />
              </button>
              <button 
                onClick={() => onEdit?.(topic)}
                className="p-3 hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-400 rounded-xl transition-all hover:scale-110 active:scale-90"
                title={t('common.edit')}
              >
                <Edit2 className="w-5 h-5" />
              </button>
              <button 
                onClick={() => handleDelete(topic)}
                className="p-3 hover:bg-red-50 dark:hover:bg-red-900/30 text-red-600 rounded-xl transition-all hover:scale-110 active:scale-90"
                title={t('common.delete')}
              >
                <Trash2 className="w-5 h-5" />
              </button>
            </div>
          </div>

          <div className="mb-8">
            <h3 className="text-2xl font-black text-[var(--color-text-primary)] mb-3 group-hover:text-blue-600 transition-colors">
              {topic.name}
            </h3>
            <div className="flex flex-col space-y-3">
              <div className="flex items-center text-[var(--color-text-secondary)] font-bold text-sm">
                <Clock className="w-4 h-4 mr-3 text-blue-500 opacity-60" />
                {topic.lastRunDate ? new Date(topic.lastRunDate).toLocaleDateString() : t('topics.list.never_run')}
              </div>
              <div className="flex items-center text-[var(--color-text-secondary)] font-bold text-sm">
                <CheckCircle2 className="w-4 h-4 mr-3 text-green-500" />
                {t('topics.list.sources_count', { count: topic.contentSourceIds?.length || 0 })}
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between pt-8 border-t border-[var(--color-border-subtle)]">
            <div className="flex items-center">
              {topic.enabled ? (
                <span className="flex items-center text-xs font-black uppercase tracking-wider text-green-600 bg-green-50 dark:bg-green-900/20 px-4 py-1.5 rounded-full border border-green-200/50 shadow-sm">
                  <CheckCircle2 className="w-3.5 h-3.5 mr-2" />
                  {t('topics.status.active')}
                </span>
              ) : (
                <span className="flex items-center text-xs font-black uppercase tracking-wider text-gray-500 bg-gray-50 dark:bg-gray-900/20 px-4 py-1.5 rounded-full border border-gray-200/50">
                  <XCircle className="w-3.5 h-3.5 mr-2" />
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
    </div>
  );
};
