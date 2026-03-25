import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import type { TopicCreate, Topic, TopicPatch, ContentSourceDTO } from '../../api/generated/model';
import { useTranslation } from 'react-i18next';
import { usePostApiV1Topics, patchApiV1TopicsId, useGetApiV1TopicsDefaultPromptTemplate } from '../../api/generated/topics/topics';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2, Check, ExternalLink, AlertCircle, Info, Settings, Plus, X, ChevronDown, ChevronRight } from 'lucide-react';
import { SourceSearchModal } from '../../components/topics/SourceSearchModal';

const topicSchema = z.object({
  name: z.string().min(3, 'Name must be at least 3 characters'),
  enabled: z.boolean(),
  promptTemplate: z.string(),
  sourceIds: z.array(z.number()),
});

type TopicFormData = z.infer<typeof topicSchema>;

interface TopicFormProps {
  initialData?: Topic;
  onSuccess: () => void;
  onCancel: () => void;
}

export const TopicForm: React.FC<TopicFormProps> = ({ initialData, onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);
  const [selectedSources, setSelectedSources] = useState<ContentSourceDTO[]>(initialData?.contentSources || []);

  const { mutateAsync: createTopic, isPending: isCreating } = usePostApiV1Topics();

  const { data: defaultTemplateResponse } = useGetApiV1TopicsDefaultPromptTemplate({
    query: {
      enabled: !initialData?.promptTemplate,
    }
  });

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
      promptTemplate: initialData?.promptTemplate || '',
      sourceIds: initialData?.contentSources ? initialData.contentSources.map(s => s.id!) : [],
    },
  });

  const [hasInitializedDefault, setHasInitializedDefault] = useState(false);

  interface DefaultTemplateResponse {
    template: string;
  }

  function isDefaultTemplateResponse(data: unknown): data is DefaultTemplateResponse {
    return typeof data === 'object' && data !== null && 'template' in data && typeof (data as any).template === 'string';
  }

  useEffect(() => {
    if (defaultTemplateResponse?.status === 200 && !hasInitializedDefault) {
      const data = defaultTemplateResponse.data;
      if (isDefaultTemplateResponse(data)) {
        const template = data.template;
        const currentVal = watch('promptTemplate');
        if (template && !currentVal && !initialData?.promptTemplate) {
          setValue('promptTemplate', template);
        }
      }
      setHasInitializedDefault(true);
    }
  }, [defaultTemplateResponse, initialData, setValue, hasInitializedDefault, watch]);

  const sourceIds = watch('sourceIds');
  const isSubmitting = isCreating;

  const onSubmit = async (data: TopicFormData) => {
    try {
      if (initialData?.id) {
        const patch: TopicPatch = {
          name: data.name,
          enabled: data.enabled,
          promptTemplate: data.promptTemplate,
          sourceIds: data.sourceIds,
          version: initialData.version,
        };
        await patchApiV1TopicsId(initialData.id, patch);
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
      void queryClient.invalidateQueries({ queryKey: ['/api/v1/topics'] });
      onSuccess();
    } catch (error: any) {
      const message = error.response?.data?.error?.message || error.message;
      toast.error(t('topics.form.save_failed', { message }));
    }
  };

  const handleAddSource = (source: ContentSourceDTO) => {
    if (!sourceIds.includes(source.id!)) {
      const newIds = [...sourceIds, source.id!];
      setValue('sourceIds', newIds);
      setSelectedSources([...selectedSources, source]);
    }
    setIsSearchModalOpen(false);
  };

  const handleRemoveSource = (id: number) => {
    const newIds = sourceIds.filter(sid => sid !== id);
    setValue('sourceIds', newIds);
    setSelectedSources(selectedSources.filter(s => s.id !== id));
  };

  return (
    <div className="relative">
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
                {watch('enabled') ? t('topics.status.active') : t('topics.status.inactive')}
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

        {/* Sources Selection */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <label className="block text-lg font-bold text-[var(--color-text-primary)]">
              {t('topics.form.sources_label')}
            </label>
            <button
              type="button"
              onClick={() => setIsSearchModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-bold rounded-xl transition-all hover:scale-105 active:scale-95 shadow-lg shadow-blue-500/20"
            >
              <Plus className="w-4 h-4" />
              {t('topics.form.add_source_button', 'Add Source')}
            </button>
          </div>
          
          {selectedSources.length === 0 ? (
            <div className="p-10 text-center bg-[var(--color-background-alt)] rounded-3xl border border-dashed border-[var(--color-border-subtle)]">
              <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
              <p className="text-[var(--color-text-secondary)] font-bold">{t('topics.form.no_sources')}</p>
              <p className="text-sm text-gray-400 mt-1">{t('topics.form.search_modal.no_results_tip')}</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 p-2">
              {selectedSources.map((source: ContentSourceDTO) => (
                <div
                  key={source.id}
                  className="flex items-center justify-between p-4 bg-[var(--color-background-base)] rounded-2xl border border-[var(--color-border-subtle)] shadow-sm hover:shadow-md transition-all group"
                >
                  <div className="flex items-center min-w-0">
                    <div className="p-2 rounded-lg bg-[var(--color-background-alt)] mr-3 flex-shrink-0">
                      <ExternalLink className="w-4 h-4 text-blue-500" />
                    </div>
                    <div className="truncate">
                      <span className="block font-bold truncate text-sm text-[var(--color-text-primary)]">
                        {source.name || source.sourceIdentifier}
                      </span>
                      <span className="block text-[10px] text-[var(--color-text-secondary)] uppercase font-medium">
                        {source.platform}
                      </span>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemoveSource(source.id!)}
                    className="p-2 text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-xl transition-colors shrink-0"
                    title="Remove source"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
          {errors.sourceIds && (
            <p className="text-sm text-red-500 font-medium animate-in slide-in-from-left-2">
              {errors.sourceIds.message}
            </p>
          )}
        </div>

        {/* Advanced Toggle */}
        <div className="pt-4">
          <button
            type="button"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="flex items-center gap-2 text-sm font-bold text-[var(--color-text-secondary)] hover:text-blue-500 transition-colors"
          >
            {showAdvanced ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
            {t('topics.form.advanced_settings', 'Advanced Settings')}
          </button>
          
          {showAdvanced && (
            <div className="mt-6 space-y-6 p-6 bg-[var(--color-background-alt)] rounded-3xl border border-[var(--color-border-subtle)] animate-in slide-in-from-top-4 duration-300">
              {/* Prompt Template */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <label className="block text-lg font-bold text-[var(--color-text-primary)]">
                    {t('topics.form.prompt_label')}
                  </label>
                  <span className="flex items-center text-xs font-bold text-blue-600 bg-blue-50 dark:bg-blue-900/20 px-3 py-1 rounded-lg">
                    <Info className="w-3.5 h-3.5 mr-1.5" />
                    {t('topics.form.prompt_supports_variables')}
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
            </div>
          )}
        </div>

          {/* Sticky Actions Footer */}
          <div className="sticky bottom-0 bg-[var(--color-background-base)] py-6 border-t border-[var(--color-border-subtle)] z-10 -mx-2 px-2 shadow-[0_-10px_20px_rgba(0,0,0,0.05)] flex items-center space-x-4">
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

      <SourceSearchModal
        isOpen={isSearchModalOpen}
        onClose={() => setIsSearchModalOpen(false)}
        onSelect={handleAddSource}
        excludeIds={sourceIds}
      />
    </div>
  );
};
