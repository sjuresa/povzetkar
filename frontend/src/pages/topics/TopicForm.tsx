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
import { useOnboardingStore, type OnboardingStep } from '../../store/onboardingStore';

const topicSchema = z.object({
  name: z.string().min(3, 'Name must be at least 3 characters'),
  enabled: z.boolean(),
  promptTemplate: z.string(),
  sourceIds: z.array(z.number()),
  useDefaultTemplate: z.boolean().optional(),
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
      useDefaultTemplate: !initialData,
    },
  });

  const [hasInitializedDefault, setHasInitializedDefault] = useState(false);

  interface DefaultTemplateResponse {
    template: string;
  }

  function isDefaultTemplateResponse(data: unknown): data is DefaultTemplateResponse {
    return typeof data === 'object' && data !== null && 'template' in data && typeof (data as { template: unknown }).template === 'string';
  }

  const useDefaultTemplate = watch('useDefaultTemplate');

  useEffect(() => {
    const data = defaultTemplateResponse?.data;
    // This effect should only run once on mount to populate the default template
    // if we are creating a NEW topic and the user has not interacted with the field.
    if (data && !hasInitializedDefault && !initialData) {
      if (useDefaultTemplate && isDefaultTemplateResponse(data)) {
        setValue('promptTemplate', data.template, { shouldDirty: false });
      }
      setHasInitializedDefault(true);
    }
  }, [defaultTemplateResponse, initialData, setValue, hasInitializedDefault, useDefaultTemplate]);

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
        // Onboarding triggers: if they add a source to an existing topic, mark Step 2 complete
        if ((data.sourceIds?.length ?? 0) > 0) {
          useOnboardingStore.getState().markStepComplete('source');
        }
      } else {
        const create: TopicCreate = {
          name: data.name,
          enabled: data.enabled,
          promptTemplate: data.promptTemplate,
          sourceIds: data.sourceIds,
        };
        await createTopic({ data: create });
        toast.success(t('topics.form.save_success_create'));
        
        const steps: OnboardingStep[] = ['topic'];
        if ((data.sourceIds?.length ?? 0) > 0) {
          steps.push('source');
        }
        useOnboardingStore.getState().markStepComplete(steps);
      }
      void queryClient.invalidateQueries({ queryKey: ['/api/v1/topics'] });
      onSuccess();
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: { message?: string } } }; message?: string };
      const message = err.response?.data?.error?.message || err.message;
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
      <form onSubmit={(e) => { void handleSubmit(onSubmit)(e); }} className="space-y-10">
        {/* Name Input */}
        <div className="group">
          <label className="block text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] mb-2 px-1">
            {t('topics.form.name_label')}
          </label>
          <div className="relative">
            <input
              {...register('name')}
              className={`w-full px-6 py-4 rounded-xl border bg-transparent text-[var(--color-text-primary)] transition-all outline-none focus:ring-1 focus:ring-[var(--color-ring-focus)] ${
                errors.name ? 'border-[var(--color-status-error)]' : 'border-[var(--color-border-primary)] focus:border-[var(--color-action-primary)]'
              }`}
              placeholder={t('topics.form.name_placeholder')}
            />
            {errors.name && (
              <div className="flex items-center mt-2 text-xs text-[var(--color-status-error)] font-bold uppercase tracking-tight animate-in fade-in slide-in-from-left-2">
                <AlertCircle className="w-3.5 h-3.5 mr-2"  strokeWidth={1.5}/>
                {errors.name.message}
              </div>
            )}
          </div>
        </div>

        {/* Toggle */}
        <div className="bg-[var(--color-bg-secondary)] p-6 rounded-[28px] border border-[var(--color-border-primary)] flex items-center justify-between transition-all hover:border-[var(--color-action-primary)]">
          <div className="flex items-center space-x-6">
            <div className={`p-4 rounded-xl border border-[var(--color-border-primary)] ${watch('enabled') ? 'bg-[var(--color-bg-primary)] text-[var(--color-action-primary)]' : 'bg-[var(--color-bg-primary)] text-[var(--color-text-secondary)]'}`}>
              <Settings className="w-6 h-6"  strokeWidth={1.5}/>
            </div>
            <div>
              <span className="block text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] mb-1">
                {t('topics.form.enabled_label')}
              </span>
              <span className="text-xl font-black uppercase tracking-tighter text-[var(--color-text-primary)]">
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
            <div className="w-14 h-8 bg-[var(--color-surface-deep)] peer-focus:ring-2 peer-focus:ring-[var(--color-ring-focus)] rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[4px] after:left-[4px] after:bg-white after:rounded-full after:h-6 after:w-6 after:transition-all peer-checked:bg-[var(--color-action-primary)] shadow-inner transition-colors"></div>
          </label>
        </div>

        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <label className="block text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] px-1">
              {t('topics.form.sources_label')}
            </label>
            <button
              type="button"
              onClick={() => setIsSearchModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] text-[var(--color-action-primary-text)] text-xs font-black uppercase tracking-tighter rounded-xl transition-all active:scale-95"
            >
              <Plus className="w-4 h-4"  strokeWidth={1.5}/>
              {t('topics.form.add_source_button', 'Add Source')}
            </button>
          </div>
          
          {selectedSources.length === 0 ? (
            <div className="p-10 text-center bg-[var(--color-bg-secondary)] rounded-[28px] border border-[var(--color-border-primary)]">
              <AlertCircle className="w-12 h-12 text-[var(--color-text-secondary)]/30 mx-auto mb-4"  strokeWidth={1.5}/>
              <p className="text-[var(--color-text-secondary)] font-black uppercase tracking-tight">{t('topics.form.no_sources')}</p>
              <p className="text-[10px] text-[var(--color-text-secondary)] opacity-60 uppercase tracking-widest mt-1">{t('topics.form.search_modal.no_results_tip')}</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 p-2">
              {selectedSources.map((source: ContentSourceDTO) => (
                  <div
                    key={source.id}
                    className="flex items-center justify-between p-4 bg-[var(--color-bg-secondary)] rounded-xl border border-[var(--color-border-primary)] transition-all hover:border-[var(--color-action-primary)]"
                  >
                    <div className="flex items-center min-w-0">
                      <div className="p-2 rounded-xl bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] mr-3 flex-shrink-0">
                        <ExternalLink className="w-4 h-4 text-[var(--color-text-secondary)]"  strokeWidth={1.5}/>
                      </div>
                      <div className="truncate">
                        <span className="block font-black text-xs uppercase tracking-tight truncate text-[var(--color-text-primary)]">
                          {source.name || source.sourceIdentifier}
                        </span>
                        <span className="block text-[10px] text-[var(--color-text-secondary)]/60 uppercase font-black tracking-widest">
                          {source.platform}
                        </span>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveSource(source.id!)}
                      className="p-2 text-[var(--color-text-secondary)] hover:text-[var(--color-status-error)] hover:bg-[var(--color-bg-primary)] rounded-xl transition-all shrink-0"
                      title="Remove source"
                    >
                      <X className="w-4 h-4"  strokeWidth={1.5}/>
                    </button>
                  </div>
              ))}
            </div>
          )}
          {errors.sourceIds && (
            <p className="text-sm text-[var(--color-status-error)] font-medium animate-in slide-in-from-left-2">
              {errors.sourceIds.message}
            </p>
          )}
        </div>

        {/* Advanced Toggle */}
        <div className="pt-4">
          <button
            type="button"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] hover:text-[var(--color-action-primary)] transition-colors"
          >
            {showAdvanced ? <ChevronDown className="w-4 h-4"  strokeWidth={1.5}/> : <ChevronRight className="w-4 h-4"  strokeWidth={1.5}/>}
            {t('topics.form.advanced_settings', 'Advanced Settings')}
          </button>
          
          {showAdvanced && (
            <div className="mt-6 space-y-6 p-8 bg-[var(--color-bg-secondary)] rounded-[28px] border border-[var(--color-border-primary)] animate-in slide-in-from-top-4 duration-300">
              {/* Prompt Template */}
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <label className="block text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)]">
                    {t('topics.form.prompt_label')}
                  </label>
                  <span className="flex items-center text-[10px] font-black uppercase tracking-widest text-[var(--color-text-secondary)] bg-[var(--color-bg-primary)] px-3 py-1.5 rounded-xl border border-[var(--color-border-primary)]">
                    <Info className="w-3.5 h-3.5 mr-1.5"  strokeWidth={1.5}/>
                    {t('topics.form.prompt_supports_variables')}
                  </span>
                </div>
                <textarea
                  {...register('promptTemplate')}
                  rows={6}
                  className={`w-full px-6 py-4 rounded-xl border bg-[var(--color-bg-primary)] text-[var(--color-text-primary)] transition-all outline-none focus:ring-1 focus:ring-[var(--color-ring-focus)] focus:border-[var(--color-action-primary)] font-mono text-sm leading-relaxed ${
                    errors.promptTemplate ? 'border-[var(--color-status-error)]' : 'border-[var(--color-border-primary)]'
                  }`}
                  placeholder={t('topics.form.prompt_placeholder')}
                />
                <p className="flex items-start text-xs text-[var(--color-text-secondary)] font-bold uppercase tracking-tight leading-relaxed opacity-60">
                  <Info className="w-3.5 h-3.5 mr-2 mt-0.5 flex-shrink-0"  strokeWidth={1.5}/>
                  {t('topics.form.prompt_tip')}
                </p>
                {errors.promptTemplate && (
                  <p className="text-sm text-[var(--color-status-error)] font-medium">{errors.promptTemplate.message}</p>
                )}
              </div>
            </div>
          )}
        </div>

          {/* Sticky Actions Footer */}
          <div className="sticky bottom-0 bg-[var(--color-bg-primary)] py-8 border-t border-[var(--color-border-primary)] z-10 -mx-6 px-6 flex items-center space-x-4">
            <button
              type="submit"
              disabled={isSubmitting}
              className="flex-1 inline-flex items-center justify-center px-8 py-5 bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] rounded-xl font-black uppercase tracking-widest transition-all hover:bg-[var(--color-action-hover)] active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed text-base"
            >
              {isSubmitting ? (
                <Loader2 className="w-6 h-6 animate-spin mr-3"  strokeWidth={1.5}/>
              ) : (
                <Check className="w-6 h-6 mr-3"  strokeWidth={1.5}/>
              )}
              {initialData ? t('topics.form.update') : t('topics.form.create')}
            </button>
            <button
              type="button"
              onClick={onCancel}
              className="px-8 py-5 rounded-xl border border-[var(--color-border-primary)] bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)] font-black uppercase tracking-widest hover:text-[var(--color-text-primary)] hover:border-[var(--color-action-primary)] transition-all active:scale-95 text-base"
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
