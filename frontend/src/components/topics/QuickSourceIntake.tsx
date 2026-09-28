import { useState, useEffect } from 'react';
import { useGetApiV1SourcesDetailsSourceTypeIdIdentifier } from '../../api/generated/content-sources/content-sources';
import type { ContentSourceCreate } from '../../api/generated/model';
import { Info, Loader2, Check, AlertCircle, AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../util/cn';
import { useDebounce } from '../../hooks/useDebounce';

interface QuickSourceIntakeProps {
  /** Receives the entry; the source is found or created when the topic is saved (POV-219). */
  onSuccess: (draft: ContentSourceCreate) => void;
  onCancel: () => void;
}

interface ApiErrorResponse {
  response?: {
    status?: number;
  };
}

/** Platforms offered in Quick Add; BLOG is shown but not supported yet (no ingestion). */
const SUPPORTED_PLATFORMS = new Set(['YOUTUBE']);

const SOURCE_TYPE_YOUTUBE = 1; // From SourceTypeEnum.YOUTUBE
const SOURCE_TYPE_BLOG = 2;    // From SourceTypeEnum.BLOG

export function QuickSourceIntake({ onSuccess, onCancel }: QuickSourceIntakeProps) {
  const { t } = useTranslation();
  const [platform, setPlatform] = useState<'YOUTUBE' | 'BLOG'>('YOUTUBE');
  const [identifier, setIdentifier] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [validationStatus, setValidationStatus] = useState<'idle' | 'validating' | 'valid' | 'invalid' | 'warning'>('idle');
  const [validatedName, setValidatedName] = useState<string | null>(null);

  const debouncedIdentifier = useDebounce(identifier, 500);

  // Real-time validation query
  const { 
    data: detailsResponse, 
    isFetching: isValidating,
    isSuccess,
    isError,
    error: validationApiError
  } = useGetApiV1SourcesDetailsSourceTypeIdIdentifier(
    platform === 'YOUTUBE' ? SOURCE_TYPE_YOUTUBE : SOURCE_TYPE_BLOG,
    // one path segment: a channel URL contains slashes (the generated client does not encode path params)
    encodeURIComponent(debouncedIdentifier.trim()),
    {
      query: {
        enabled: platform === 'YOUTUBE' && debouncedIdentifier.trim().length > 3,
        retry: false,
        throwOnError: false, // Prevents 404s from crashing the Error Boundary
      }
    }
  );

  useEffect(() => {
    if (platform !== 'YOUTUBE') {
      setValidationStatus('idle');
      setValidatedName(null);
      return;
    }

    // Too short to check (the query is disabled): never keep the result of an earlier identifier
    if (debouncedIdentifier.trim().length <= 3) {
      setValidationStatus('idle');
      setValidatedName(null);
      return;
    }

    if (isValidating) {
      setValidationStatus('validating');
    } else if (isSuccess && detailsResponse) {
      const finalName = detailsResponse?.data?.name;
      
      if (finalName) {
        setValidationStatus('valid');
        setValidatedName(finalName);
      } else {
        setValidationStatus('idle');
      }
    } else if (isError) {
      const apiError = (validationApiError as unknown) as ApiErrorResponse;
      if (apiError?.response?.status === 404) {
        setValidationStatus('invalid');
      } else {
        setValidationStatus('warning');
      }
      setValidatedName(null);
    }
  }, [debouncedIdentifier, isValidating, detailsResponse, isSuccess, isError, validationApiError, platform]);

  // Only a channel the platform confirmed can be added: the backend rejects unknown channels on save
  // ...and only for the identifier that was checked: while typing, the status still belongs to the previous one
  const canAdd = validationStatus === 'valid' && identifier.trim().length > 0 && debouncedIdentifier === identifier;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!identifier.trim()) {
      setError(t('topics.form.quick_add.error_identifier_required'));
      return;
    }
    if (!canAdd) {
      return;
    }

    // Send the channel ID the check resolved: the form can spot a channel it already lists, and saving needs no lookup
    onSuccess({
      platform,
      sourceIdentifier: detailsResponse?.data?.channelId ?? identifier.trim(),
      name: validatedName ?? undefined,
    });
  };

  return (
    <div className="space-y-4 p-4 bg-[var(--color-background-alt)] rounded-xl border border-[var(--color-border-subtle)] shadow-sm transition-all duration-300">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">{t('topics.form.quick_add.title', 'Quick Add Source')}</h3>
        <button
          onClick={onCancel}
          className="text-xs text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
        >
          {t('topics.form.quick_add.cancel_button', 'Cancel')}
        </button>
      </div>

      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-[var(--color-text-secondary)] mb-1">
            {t('topics.form.quick_add.platform_label', 'Source Platform')}
          </label>
          <div className="flex gap-2">
            {(['YOUTUBE', 'BLOG'] as const).map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPlatform(p)}
                disabled={!SUPPORTED_PLATFORMS.has(p)}
                className={cn(
                  "flex-1 px-3 py-1.5 text-xs font-medium rounded-md border transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed",
                  platform === p
                    ? "bg-[var(--color-action-primary)] border-[var(--color-action-primary)] text-[var(--color-action-primary-text)] shadow-sm"
                    : "bg-[var(--color-surface-elevated)] border-[var(--color-border-subtle)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] disabled:hover:bg-[var(--color-surface-elevated)]"
                )}
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        <div className="relative">
          <label 
            htmlFor="source-identifier"
            className="block text-xs font-medium text-[var(--color-text-secondary)] mb-1"
          >
            {t('topics.form.quick_add.identifier_label', 'Identifier or URL')}
          </label>
          <div className="relative group">
            <input
              id="source-identifier"
              type="text"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder={platform === 'YOUTUBE' ? t('topics.form.quick_add.identifier_placeholder_youtube', '@handle or channel ID') : t('topics.form.quick_add.identifier_placeholder_blog', 'Blog URL')}
              className={cn(
                "w-full px-3 py-2 text-sm bg-[var(--color-surface-elevated)] text-[var(--color-text-primary)] border rounded-md focus:outline-none focus:ring-2 transition-all duration-200 pr-10",
                validationStatus === 'valid' ? "border-[var(--color-status-success)]/50 focus:ring-[var(--color-status-success)]/20" : 
                validationStatus === 'invalid' ? "border-[var(--color-status-error)]/50 focus:ring-[var(--color-status-error)]/20" :
                validationStatus === 'warning' ? "border-[var(--color-status-warning)]/50 focus:ring-[var(--color-status-warning)]/20" :
                "border-[var(--color-border-subtle)] focus:ring-[var(--color-ring-focus)]"
              )}
            />
            <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center">
              {validationStatus === 'validating' && <Loader2 className="w-4 h-4 text-[var(--color-action-primary)] animate-spin"  strokeWidth={1.5}/>}
              {validationStatus === 'valid' && <Check className="w-4 h-4 text-[var(--color-status-success)]"  strokeWidth={1.5}/>}
              {validationStatus === 'invalid' && <AlertCircle className="w-4 h-4 text-[var(--color-status-error)]"  strokeWidth={1.5}/>}
              {validationStatus === 'warning' && <AlertTriangle className="w-4 h-4 text-[var(--color-status-warning)] animate-pulse"  strokeWidth={1.5}/>}
            </div>
          </div>
          
          {validationStatus === 'valid' && validatedName && (
            <p className="mt-1 text-[11px] text-[var(--color-status-success)] font-medium animate-in fade-in slide-in-from-top-1">
              Found: <span className="font-bold">{validatedName}</span>
            </p>
          )}

          {validationStatus === 'invalid' && (
             <p className="mt-1 text-[11px] text-[var(--color-status-error)] animate-in fade-in slide-in-from-top-1">
               {t('topics.form.quick_add.validation_error_not_found')}
             </p>
          )}

          {validationStatus === 'warning' && (
             <p className="mt-1 text-[11px] text-[var(--color-status-warning)] flex items-center gap-1 animate-in fade-in slide-in-from-top-1 font-medium">
               {t('topics.form.quick_add.validation_warning_api')}
             </p>
          )}
        </div>

        {platform === 'YOUTUBE' && validationStatus === 'idle' && (
          <div className="flex gap-2 p-2 bg-[var(--color-action-primary)]/[0.03] rounded border border-[var(--color-action-primary)] border-opacity-30">
            <Info className="w-3.5 h-3.5 text-[var(--color-action-primary)] shrink-0 mt-0.5"  strokeWidth={1.5}/>
            <div className="text-[10px] text-[var(--color-text-primary)] opacity-80">
              <p className="font-semibold mb-0.5 text-[var(--color-action-primary)]">{t('topics.form.quick_add.youtube_guidance_title', 'YouTube Tips')}</p>
              <ul className="list-disc list-inside space-y-0.5">
                <li>{t('topics.form.quick_add.youtube_guidance_id', 'Paste @handle (e.g., @veritasium)')}</li>
                <li>{t('topics.form.quick_add.youtube_guidance_find', 'Or paste the channel URL')}</li>
              </ul>
            </div>
          </div>
        )}

        {error && (
          <div className="flex items-center gap-2 p-2 bg-[var(--color-status-error)]/10 text-[var(--color-status-error)] text-xs border border-[var(--color-status-error)]/30 rounded">
            <AlertCircle className="w-3.5 h-3.5"  strokeWidth={1.5}/>
            <span>{error}</span>
          </div>
        )}

        <button
          type="submit"
          disabled={!canAdd}
          className={cn(
            "w-full flex items-center justify-center gap-2 px-4 py-2 text-[var(--color-action-primary-text)] text-sm font-medium rounded-md transition-all duration-200",
            canAdd
              ? "bg-[var(--color-status-success)]/80 hover:bg-[var(--color-status-success)] shadow-lg shadow-[var(--color-status-success)]/20"
              : "bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)] cursor-not-allowed"
          )}
        >
          <Check className="w-4 h-4"  strokeWidth={1.5}/>
          {t('topics.form.quick_add.submit_button', 'Add Source')}
        </button>
      </form>
    </div>
  );
}
