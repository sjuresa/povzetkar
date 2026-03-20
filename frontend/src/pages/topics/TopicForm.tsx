import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import type { TopicCreate, Topic, TopicPatch } from '../../api/generated/model';
import { useTranslation } from 'react-i18next';
import { useGetApiV1Sources } from '../../api/generated/content-sources/content-sources';
import { usePostApiV1Topics, usePatchApiV1TopicsId } from '../../api/generated/topics/topics';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

const topicSchema = z.object({
  name: z.string().min(1, 'Name is required').max(255),
  enabled: z.boolean(),
  promptTemplate: z.string().optional(),
  sourceIds: z.array(z.number()),
});

type TopicFormValues = z.infer<typeof topicSchema>;

interface TopicFormProps {
  initialData?: Topic;
  onSuccess: () => void;
  onCancel: () => void;
}

export const TopicForm: React.FC<TopicFormProps> = ({ initialData, onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const isEditing = !!initialData;
  const { data: sourcesResponse, isLoading: isLoadingSources } = useGetApiV1Sources(undefined);
  const createTopic = usePostApiV1Topics();
  const patchTopic = usePatchApiV1TopicsId();

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<TopicFormValues>({
    resolver: zodResolver(topicSchema),
    defaultValues: {
      name: initialData?.name || '',
      enabled: initialData?.enabled ?? true,
      promptTemplate: initialData?.promptTemplate || undefined,
      sourceIds: (initialData?.contentSourceIds || []) as number[],
    },
  });

  const selectedSourceIds = watch('sourceIds');

  const onSubmit = async (values: TopicFormValues) => {
    try {
      if (isEditing) {
        const data: TopicPatch = {
          name: values.name === initialData!.name ? undefined : values.name,
          enabled: values.enabled === initialData!.enabled ? undefined : values.enabled,
          promptTemplate: values.promptTemplate === initialData!.promptTemplate ? undefined : values.promptTemplate,
          sourceIds: values.sourceIds,
          version: initialData!.version,
        };
        await patchTopic.mutateAsync({
          id: initialData!.id!,
          data: data,
        });
        toast.success(t('topics.form.save_success_update'));
      } else {
        const data: TopicCreate = {
          name: values.name,
          enabled: values.enabled,
          promptTemplate: values.promptTemplate,
          sourceIds: values.sourceIds,
        };
        await createTopic.mutateAsync({
          data: data,
        });
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

  const sourcesData = (sourcesResponse?.data?.items as any[]) || [];

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      <div>
        <label className="block text-sm font-medium text-[var(--color-text-secondary)] mb-1">
          {t('topics.form.name_label')}
        </label>
        <input
          {...register('name')}
          className={`w-full px-4 py-2 rounded-lg border bg-[var(--color-background-base)] text-[var(--color-text-primary)] transition-colors focus:ring-2 focus:ring-blue-600 ${
            errors.name ? 'border-red-500 ring-red-200' : 'border-[var(--color-border-subtle)]'
          }`}
          placeholder={t('topics.form.name_placeholder')}
        />
        {errors.name && <p className="mt-1 text-xs text-red-500">{errors.name.message}</p>}
      </div>

      <div className="flex items-center space-x-3 bg-[var(--color-background-alt)] p-4 rounded-xl border border-[var(--color-border-subtle)]">
        <div className="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2">
            <input
                type="checkbox"
                {...register('enabled')}
                className="sr-only peer"
                id="topic-enabled"
            />
            <div className="w-11 h-6 bg-[var(--color-surface-deep)] peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
        </div>
        <label htmlFor="topic-enabled" className="text-sm font-medium text-[var(--color-text-primary)]">
          {t('topics.form.enabled_label')}
        </label>
      </div>

      <div>
        <label className="block text-sm font-medium text-[var(--color-text-secondary)] mb-1">
          {t('topics.form.prompt_label')}
        </label>
        <textarea
          {...register('promptTemplate')}
          rows={5}
          className="w-full px-4 py-2 rounded-lg border border-[var(--color-border-subtle)] bg-[var(--color-background-base)] text-[var(--color-text-primary)] transition-colors focus:ring-2 focus:ring-blue-600 font-mono text-sm"
          placeholder={t('topics.form.prompt_placeholder')}
        />
        <p className="mt-1 text-xs text-[var(--color-text-secondary)] opacity-70">
          {t('topics.form.prompt_tip')}
        </p>
      </div>

      <div>
        <label className="block text-sm font-medium text-[var(--color-text-secondary)] mb-2">
          {t('topics.form.sources_label')}
        </label>
        {isLoadingSources ? (
          <div className="flex items-center space-x-2 text-sm text-[var(--color-text-secondary)]">
            <Loader2 className="w-4 h-4 animate-spin" />
            <span>{t('topics.form.sources_loading')}</span>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-2 max-h-60 overflow-y-auto pr-2 custom-scrollbar">
            {sourcesData?.map((source: any) => (
              <button
                key={source.id}
                type="button"
                onClick={() => toggleSource(source.id)}
                className={`flex items-center justify-between p-3 rounded-xl border text-left transition-all ${
                  selectedSourceIds.includes(source.id)
                    ? 'border-blue-500 bg-blue-500/10 text-blue-400'
                    : 'border-[var(--color-border-subtle)] bg-[var(--color-background-alt)] text-[var(--color-text-secondary)]'
                }`}
              >
                <div>
                  <p className="text-sm font-medium">{source.name}</p>
                  <p className="text-xs opacity-70 line-clamp-1">{source.sourceIdentifier}</p>
                </div>
                {selectedSourceIds.includes(source.id) && (
                  <div className="w-2 h-2 rounded-full bg-blue-500" />
                )}
              </button>
            ))}
            {(!sourcesData || sourcesData.length === 0) && (
              <p className="text-sm text-[var(--color-text-secondary)] italic">{t('topics.form.no_sources')}</p>
            )}
          </div>
        )}
      </div>

      <div className="flex items-center justify-end space-x-3 pt-6 border-t border-[var(--color-border-subtle)]">
        <button
          type="button"
          onClick={onCancel}
          className="px-6 py-2 rounded-xl text-sm font-medium text-[var(--color-text-secondary)] hover:bg-[var(--color-background-alt)] transition-colors"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          disabled={isSubmitting}
          className="px-8 py-2 rounded-xl bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-all shadow-lg shadow-blue-500/20 disabled:opacity-50 disabled:cursor-not-allowed inline-flex items-center space-x-2"
        >
          {isSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
          <span>{isEditing ? t('topics.form.update') : t('topics.form.create')}</span>
        </button>
      </div>
    </form>
  );
};
