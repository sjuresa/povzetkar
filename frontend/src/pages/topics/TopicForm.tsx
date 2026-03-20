import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import type { TopicCreate, Topic, TopicPatch, ContentSourcePaginatedResponse, ContentSourceDTO } from '../../api/generated/model';
import { useTranslation } from 'react-i18next';
import { useGetApiV1Sources } from '../../api/generated/content-sources/content-sources';
import { usePostApiV1Topics, patchApiV1TopicsId } from '../../api/generated/topics/topics';
import { toast } from 'sonner';
import { Loader2, Check, ExternalLink, AlertCircle, Info, Settings } from 'lucide-react';

const topicSchema = z.object({
  name: z.string().min(3, 'Name must be at least 3 characters'),
  enabled: z.boolean(),
  promptTemplate: z.string().min(10, 'Prompt must be at least 10 characters'),
  sourceIds: z.array(z.number()).min(1, 'Select at least one source'),
});

type TopicFormData = z.infer<typeof topicSchema>;

interface TopicFormProps {
  initialData?: Topic;
  onSuccess: () => void;
  onCancel: () => void;
}

export const TopicForm: React.FC<TopicFormProps> = ({ initialData, onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const { data: sourcesResponse, isLoading: isLoadingSources } = useGetApiV1Sources({ page: 0, size: 100 });
  const { mutateAsync: createTopic, isPending: isCreating } = usePostApiV1Topics();

  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
    setValue,
  } = useForm<TopicFormData>({
    resolver: zodResolver(topicSchema),
    defaultValues: {
      name: initialData?.name || '',
      enabled: initialData?.enabled ?? true,
      promptTemplate: initialData?.promptTemplate || 'Summarize the key points from the following content, focusing on {{topic}}.',
      sourceIds: initialData?.contentSourceIds || [],
    },
  });

  const selectedSourceIds = watch('sourceIds');
  const isSubmitting = isCreating;

  const onSubmit = async (data: TopicFormData) => {
    try {
      if (initialData?.id) {
        const patch: TopicPatch = {
          name: data.name,
          enabled: data.enabled,
          promptTemplate: data.promptTemplate,
          sourceIds: data.sourceIds,
        };
        await patchApiV1TopicsId(initialData.id, patch, {
          headers: { 'If-Match': `"${initialData.version}"` } as HeadersInit
        });
        toast.success(t('topics.form.save_success_update'));
      } else {
        const create: TopicCreate = {
          name: data.name,
          enabled: data.enabled,
          promptTemplate: data.promptTemplate,
          sourceIds: data.sourceIds,
        };
        await createTopic({ data: create });
        toast.success(t('topics.form.save_success_create'));
      }
      onSuccess();
    } catch (error: any) {
      const message = error.response?.data?.error?.message || error.message;
      toast.error(t('topics.form.save_failed', { message }));
    }
  };

  const toggleSource = (sourceId: number) => {
    if (selectedSourceIds.includes(sourceId)) {
      setValue('sourceIds', selectedSourceIds.filter(id => id !== sourceId));
    } else {
      setValue('sourceIds', [...selectedSourceIds, sourceId]);
    }
  };

  /**
   * Sources extraction simplified as per CR feedback.
   * Although useGetApiV1Sources type signature suggests it returns the whole response object, 
   * customInstance at runtime unwraps the inner DTO from the envelope.
   */
  const sourcesData = (sourcesResponse as unknown as ContentSourcePaginatedResponse)?.items ?? [];

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-10">
      {/* Name Input */}
      <div className="space-y-3">
        <label className="block text-lg font-bold text-[var(--color-text-primary)]">
          {t('topics.form.name_label')}
        </label>
        <div className="relative group">
          <input
            {...register('name')}
            className={`w-full px-6 py-4 rounded-2xl border bg-[var(--color-background-base)] text-[var(--color-text-primary)] transition-all focus:ring-4 focus:ring-blue-500/20 group-hover:shadow-lg ${
              errors.name ? 'border-red-500 ring-red-200' : 'border-[var(--color-border-subtle)] focus:border-blue-500'
            }`}
            placeholder={t('topics.form.name_placeholder')}
          />
          {errors.name && (
            <div className="flex items-center mt-2 text-sm text-red-500 font-medium animate-in slide-in-from-left-2 transition-all">
              <AlertCircle className="w-4 h-4 mr-2" />
              {errors.name.message}
            </div>
          )}
        </div>
      </div>

      {/* Toggle */}
      <div className="bg-[var(--color-background-alt)] p-6 rounded-3xl border border-[var(--color-border-subtle)] flex items-center justify-between transition-all hover:bg-[var(--color-surface-hover)]">
        <div className="flex items-center space-x-4">
          <div className={`p-3 rounded-xl ${watch('enabled') ? 'bg-green-100 dark:bg-green-900/30 text-green-600' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'}`}>
            <Settings className="w-6 h-6" />
          </div>
          <div>
            <span className="block text-lg font-bold text-[var(--color-text-primary)]">
              {t('topics.form.enabled_label')}
            </span>
            <span className="text-sm text-[var(--color-text-secondary)] font-medium">
              {watch('enabled') ? 'Topic monitoring is active' : 'Monitoring paused'}
            </span>
          </div>
        </div>
        <label className="relative inline-flex items-center cursor-pointer group">
          <input
            type="checkbox"
            {...register('enabled')}
            className="sr-only peer"
            id="topic-enabled-toggle"
          />
          <div className="w-14 h-8 bg-[var(--color-surface-deep)] peer-focus:ring-4 peer-focus:ring-blue-500/20 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[4px] after:left-[4px] after:bg-white after:rounded-full after:h-6 after:w-6 after:transition-all peer-checked:bg-blue-600 shadow-inner group-hover:scale-105 transition-transform"></div>
        </label>
      </div>

      {/* Prompt Template */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <label className="block text-lg font-bold text-[var(--color-text-primary)]">
            {t('topics.form.prompt_label')}
          </label>
          <span className="flex items-center text-xs font-bold text-blue-600 bg-blue-50 dark:bg-blue-900/20 px-3 py-1 rounded-lg">
            <Info className="w-3.5 h-3.5 mr-1.5" />
            Supports Variables
          </span>
        </div>
        <textarea
          {...register('promptTemplate')}
          rows={6}
          className={`w-full px-6 py-4 rounded-3xl border border-[var(--color-border-subtle)] bg-[var(--color-background-base)] text-[var(--color-text-primary)] transition-all focus:ring-4 focus:ring-blue-500/20 focus:border-blue-500 font-mono text-sm leading-relaxed ${
            errors.promptTemplate ? 'border-red-500' : ''
          }`}
          placeholder={t('topics.form.prompt_placeholder')}
        />
        <p className="flex items-start text-sm text-[var(--color-text-secondary)] font-medium leading-relaxed opacity-80">
          <Info className="w-4 h-4 mr-2 mt-0.5 flex-shrink-0 text-blue-500" />
          {t('topics.form.prompt_tip')}
        </p>
        {errors.promptTemplate && (
          <p className="text-sm text-red-500 font-medium">{errors.promptTemplate.message}</p>
        )}
      </div>

      {/* Sources Selection */}
      <div className="space-y-4">
        <label className="block text-lg font-bold text-[var(--color-text-primary)]">
          {t('topics.form.sources_label')}
        </label>
        
        {isLoadingSources ? (
          <div className="flex items-center justify-center p-12 bg-[var(--color-background-alt)] rounded-3xl animate-pulse">
            <Loader2 className="w-8 h-8 text-blue-600 animate-spin mr-3" />
            <span className="text-[var(--color-text-secondary)] font-bold">{t('topics.form.sources_loading')}</span>
          </div>
        ) : sourcesData.length === 0 ? (
          <div className="p-10 text-center bg-[var(--color-background-alt)] rounded-3xl border border-dashed border-[var(--color-border-subtle)]">
            <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
            <p className="text-[var(--color-text-secondary)] font-bold">{t('topics.form.no_sources')}</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-h-[320px] overflow-y-auto pr-2 custom-scrollbar p-2">
            {sourcesData.map((source: ContentSourceDTO) => (
              <button
                key={source.id}
                type="button"
                onClick={() => toggleSource(source.id!)}
                className={`flex items-center justify-between p-4 rounded-2xl border transition-all text-left group ${
                  selectedSourceIds.includes(source.id!)
                    ? 'bg-blue-600 text-white border-blue-600 shadow-lg shadow-blue-500/20 scale-[1.02]'
                    : 'bg-[var(--color-background-base)] text-[var(--color-text-primary)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] hover:-translate-y-0.5'
                }`}
              >
                <div className="flex items-center min-w-0">
                  <div className={`p-2 rounded-lg mr-3 flex-shrink-0 ${
                    selectedSourceIds.includes(source.id!) ? 'bg-white/20' : 'bg-gray-100 dark:bg-gray-800'
                  }`}>
                    <ExternalLink className="w-4 h-4" />
                  </div>
                  <span className="font-bold truncate text-sm">{source.name}</span>
                </div>
                {selectedSourceIds.includes(source.id!) && (
                  <Check className="w-5 h-5 flex-shrink-0 animate-in zoom-in" />
                )}
              </button>
            ))}
          </div>
        )}
        {errors.sourceIds && (
          <p className="text-sm text-red-500 font-medium animate-in slide-in-from-left-2">
            {errors.sourceIds.message}
          </p>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center space-x-4 pt-6">
        <button
          type="submit"
          disabled={isSubmitting}
          className="flex-1 inline-flex items-center justify-center px-8 py-5 bg-blue-600 text-white rounded-[24px] font-bold shadow-xl shadow-blue-500/30 hover:bg-blue-700 hover:scale-[1.02] active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed text-lg"
        >
          {isSubmitting ? (
            <Loader2 className="w-6 h-6 animate-spin mr-3" />
          ) : (
            <Check className="w-6 h-6 mr-3" />
          )}
          {initialData ? t('topics.form.update') : t('topics.form.create')}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="px-8 py-5 rounded-[24px] border border-[var(--color-border-subtle)] bg-[var(--color-background-base)] text-[var(--color-text-primary)] font-bold hover:bg-[var(--color-background-alt)] transition-all active:scale-95 text-lg"
        >
          {t('common.cancel')}
        </button>
      </div>
    </form>
  );
};
