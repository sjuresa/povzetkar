import { useState } from 'react';
import { usePostApiV1Sources } from '../../api/generated/content-sources/content-sources';
import type { ContentSourceDTO } from '../../api/generated/model';
import { Info, Loader2, Check, AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../util/cn';

interface QuickSourceIntakeProps {
  onSuccess: (source: ContentSourceDTO) => void;
  onCancel: () => void;
}

export function QuickSourceIntake({ onSuccess, onCancel }: QuickSourceIntakeProps) {
  const { t } = useTranslation();
  const [platform, setPlatform] = useState<'YOUTUBE' | 'BLOG'>('YOUTUBE');
  const [identifier, setIdentifier] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { mutate: createSource, isPending } = usePostApiV1Sources();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!identifier.trim()) {
      setError(t('topics.form.quick_add.error_identifier_required'));
      return;
    }

    createSource(
      {
        data: {
          platform,
          sourceIdentifier: identifier.trim(),
          name: '', // Optional, name will be enriched
        },
      },
      {
        onSuccess: (response) => {
          // Note: customInstance in client.ts already unwraps the API envelope
          // and returns the inner data directly (the ContentSourceDTO).
          onSuccess(response as unknown as ContentSourceDTO);
        },
        onError: (err: unknown) => {
          const axiosError = err as { response?: { data?: { message?: string } } };
          setError(axiosError?.response?.data?.message || t('topics.form.quick_add.error_failed', 'Failed to add source'));
        },
      }
    );
  };

  return (
    <div className="space-y-4 p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg border border-gray-200 dark:border-gray-800">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">{t('topics.form.quick_add.title')}</h3>
        <button
          onClick={onCancel}
          className="text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
        >
          {t('topics.form.quick_add.cancel_button')}
        </button>
      </div>

      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('topics.form.quick_add.platform_label')}
          </label>
          <div className="flex gap-2">
            {(['YOUTUBE', 'BLOG'] as const).map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPlatform(p)}
                className={cn(
                  "flex-1 px-3 py-1.5 text-xs font-medium rounded-md border transition-colors",
                  platform === p
                    ? "bg-blue-600 border-blue-600 text-white"
                    : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
                )}
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
            {t('topics.form.quick_add.identifier_label')}
          </label>
          <input
            type="text"
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            placeholder={platform === 'YOUTUBE' ? t('topics.form.quick_add.identifier_placeholder_youtube') : t('topics.form.quick_add.identifier_placeholder_blog')}
            className="w-full px-3 py-2 text-sm bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-700 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:text-gray-100"
          />
        </div>

        {platform === 'YOUTUBE' && (
          <div className="flex gap-2 p-2 bg-blue-50 dark:bg-blue-900/20 rounded border border-blue-100 dark:border-blue-800">
            <Info className="w-4 h-4 text-blue-600 dark:text-blue-400 shrink-0 mt-0.5" />
            <div className="text-[11px] text-blue-800 dark:text-blue-300">
              <p className="font-semibold mb-0.5">{t('topics.form.quick_add.youtube_guidance_title')}</p>
              <ul className="list-disc list-inside space-y-0.5 opacity-90">
                <li>{t('topics.form.quick_add.youtube_guidance_id')}</li>
                <li>{t('topics.form.quick_add.youtube_guidance_find')}</li>
              </ul>
            </div>
          </div>
        )}

        {error && (
          <div className="flex items-center gap-2 text-red-600 dark:text-red-400 text-xs">
            <AlertCircle className="w-3.5 h-3.5" />
            <span>{error}</span>
          </div>
        )}

        <button
          type="submit"
          disabled={isPending || !identifier.trim()}
          className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white text-sm font-medium rounded-md transition-colors"
        >
          {isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
          {t('topics.form.quick_add.submit_button')}
        </button>
      </form>
    </div>
  );
}
