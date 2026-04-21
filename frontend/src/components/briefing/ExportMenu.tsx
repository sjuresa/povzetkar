import { useState, useRef, useEffect } from 'react';
import { 
  Download, 
  FileText, 
  Code, 
  Share2, 
  ChevronDown,
  Check,
  Copy,
  Trash2,
  Loader2
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '../../util/cn';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { 
  useGetApiV1BriefingsId, 
  usePostApiV1BriefingsIdShare, 
  usePostApiV1BriefingsIdUnshare,
  getGetApiV1BriefingsQueryKey,
  getGetApiV1BriefingsIdQueryKey
} from '../../api/generated/briefings/briefings';
import { 
  usePostApiV1ContentItemsIdSummaryShare, 
  usePostApiV1ContentItemsIdSummaryUnshare,
  getGetApiV1ContentItemsQueryKey,
  getGetApiV1ContentItemsIdQueryKey
} from '../../api/generated/content-items/content-items';
import { getGetApiV1SourcesQueryKey } from '../../api/generated/content-sources/content-sources';
import { useOnboardingStore } from '../../store/onboardingStore';
import { unified } from 'unified';
import remarkParse from 'remark-parse';
import remarkGfm from 'remark-gfm';
import remarkRehype from 'remark-rehype';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import rehypeStringify from 'rehype-stringify';
import { generateBriefingHtml, generateBriefingMarkdown } from '../../util/exportTemplates';

const processor = unified()
  .use(remarkParse)
  .use(remarkGfm)
  .use(remarkRehype, { allowDangerousHtml: true })
  .use(rehypeRaw)
  .use(rehypeSanitize)
  .use(rehypeStringify);

interface ExportMenuProps {
  data: {
    id?: number;
    uuid: string | null;
    title: string;
    topicName: string;
    date: string;
    metaSummary?: string;
    summaries?: { summaryText?: string }[];
  };
  type?: 'briefing' | 'summary';
  variant?: 'ghost' | 'outline' | 'primary';
  className?: string;
  align?: 'left' | 'right';
  readOnly?: boolean;
}

export function ExportMenu({ data, type = 'briefing', variant = 'outline', className, align = 'right', readOnly = false }: Readonly<ExportMenuProps>) {
  const [isOpen, setIsOpen] = useState(false);
  const [isCopied, setIsCopied] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [localUuid, setLocalUuid] = useState<string | null>(data.uuid);
  const menuRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();
  const { t } = useTranslation();

  // Sync state if uuid changes externally
  useEffect(() => {
    setLocalUuid(data.uuid);
  }, [data.uuid]);

  const onShareSuccess = (invalidationAction: () => void, successMsg: string, isShare: boolean) => (res?: unknown) => {
    if (isShare) {
      const newUuid = (res as { data: string }).data;
      if (newUuid) {
        setLocalUuid(newUuid);
        useOnboardingStore.getState().markStepComplete('share');
      }
    } else {
      setLocalUuid(null);
    }
    invalidationAction();
    toast.success(t(successMsg));
  };

  const onShareError = (errorMsg: string) => () => {
    toast.error(t(errorMsg));
  };

  // Briefing hooks
  const briefingDetailQuery = useGetApiV1BriefingsId(data.id!, {
    query: { enabled: false }
  });

  const briefingShare = usePostApiV1BriefingsIdShare({
    mutation: {
      onSuccess: onShareSuccess(() => {
        void queryClient.invalidateQueries({ queryKey: getGetApiV1BriefingsQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiV1BriefingsIdQueryKey(data.id!) });
      }, 'briefingDetail.export.toasts.shareEnabled', true),
      onError: onShareError('briefingDetail.export.toasts.shareEnableFailed')
    }
  });

  const briefingUnshare = usePostApiV1BriefingsIdUnshare({
    mutation: {
      onSuccess: onShareSuccess(() => {
        void queryClient.invalidateQueries({ queryKey: getGetApiV1BriefingsQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiV1BriefingsIdQueryKey(data.id!) });
      }, 'briefingDetail.export.toasts.shareDisabled', false),
      onError: onShareError('briefingDetail.export.toasts.shareDisableFailed')
    }
  });

  // Summary hooks
  const summaryShare = usePostApiV1ContentItemsIdSummaryShare({
    mutation: {
      onSuccess: onShareSuccess(() => {
        void queryClient.invalidateQueries({ queryKey: getGetApiV1ContentItemsQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiV1SourcesQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiV1ContentItemsIdQueryKey(data.id!) });
      }, 'briefingDetail.export.toasts.shareEnabled', true),
      onError: onShareError('briefingDetail.export.toasts.shareEnableFailed')
    }
  });

  const summaryUnshare = usePostApiV1ContentItemsIdSummaryUnshare({
    mutation: {
      onSuccess: onShareSuccess(() => {
        void queryClient.invalidateQueries({ queryKey: getGetApiV1ContentItemsQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiV1SourcesQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiV1ContentItemsIdQueryKey(data.id!) });
      }, 'briefingDetail.export.toasts.shareDisabled', false),
      onError: onShareError('briefingDetail.export.toasts.shareDisableFailed')
    }
  });

  const isPending = briefingShare.isPending || briefingUnshare.isPending || summaryShare.isPending || summaryUnshare.isPending;

  const handleShare = () => {
    if (type === 'briefing') {
      briefingShare.mutate({ id: data.id! });
    } else {
      summaryShare.mutate({ id: data.id! });
    }
  };

  const handleUnshare = () => {
    if (type === 'briefing') {
      briefingUnshare.mutate({ id: data.id! });
    } else {
      summaryUnshare.mutate({ id: data.id! });
    }
  };

  // Close menu when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const downloadFile = (content: string, fileName: string, contentType: string) => {
    const a = document.createElement('a');
    const file = new Blob([content], { type: contentType });
    a.href = URL.createObjectURL(file);
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const ensureData = async () => {
    if (type === 'summary') {
        return {
            metaSummary: data.metaSummary || '',
            content: data.summaries?.[0]?.summaryText || ''
        };
    }

    if (data.metaSummary && data.summaries) {
      return { 
        metaSummary: data.metaSummary,
        content: data.summaries.map(s => s.summaryText || '').filter(Boolean).join('\n\n---\n\n')
      };
    }
    
    setIsExporting(true);
    try {
      const detailsResponse = await briefingDetailQuery.refetch();
      const detail = detailsResponse.data?.data;
      if (!detail) throw new Error('Failed to fetch briefing detail');
      
      return { 
        metaSummary: detail.metaSummary || '', 
        content: detail.summaries?.map(s => s.summaryText).join('\n\n---\n\n') || ''
      };
    } catch {
      toast.error(t('briefingDetail.export.toasts.exportDataError'));
      return null;
    } finally {
      setIsExporting(false);
    }
  };

  const exportMarkdown = async () => {
    const result = await ensureData();
    if (!result) return;

    const content = generateBriefingMarkdown({
      title: data.title,
      topicName: data.topicName,
      briefingDate: data.date,
      metaSummary: result.metaSummary,
      content: result.content
    });
    
    const safeTopic = data.topicName.replaceAll(/\s+/g, '_');
    const safeTitle = data.title.replaceAll(/\s+/g, '_');
    const fileName = `${safeTopic}_${safeTitle}.md`;
    
    downloadFile(content, fileName, 'text/markdown');
    toast.success(t('briefingDetail.export.toasts.markdownSuccess'));
    setIsOpen(false);
  };

  const exportHtml = async () => {
    const result = await ensureData();
    if (!result) return;

    // Optimized Markdown to HTML rendering using a shared unified processor instance in parallel
    const [summaryVFile, textVFile] = await Promise.all([
      processor.process(result.metaSummary || ''),
      processor.process(result.content || '')
    ]);
    const summaryHtml = summaryVFile.toString();
    const textHtml = textVFile.toString();

    const htmlContent = generateBriefingHtml({
      title: data.title,
      topicName: data.topicName,
      briefingDate: data.date,
      summaryHtml,
      contentHtml: textHtml
    });
    
    const safeTopic = data.topicName.replaceAll(/\s+/g, '_');
    const safeTitle = data.title.replaceAll(/\s+/g, '_');
    const fileName = `${safeTopic}_${safeTitle}.html`;
    
    downloadFile(htmlContent, fileName, 'text/html');
    toast.success(t('briefingDetail.export.toasts.htmlSuccess'));
    setIsOpen(false);
  };

  const copyShareLink = async () => {
    const url = `${globalThis.location.origin}/s/${localUuid}`;
    try {
      await navigator.clipboard.writeText(url);
      setIsCopied(true);
      toast.success(t('briefingDetail.export.toasts.copySuccess'));
      setTimeout(() => setIsCopied(false), 2000);
      setIsOpen(false);
    } catch {
      toast.error(t('briefingDetail.export.toasts.copyFailed'));
    }
  };

  const variantsList = {
    primary: "bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] text-[var(--color-action-primary-text)] shadow-lg shadow-[var(--color-action-primary)]/20",
    outline: "bg-[var(--color-bg-primary)] border border-[var(--color-border-primary)] text-[var(--color-text-primary)] hover:bg-[var(--color-surface-hover)]",
    ghost: "bg-transparent text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-hover)]"
  };

  return (
    <div className={cn("relative", className)} ref={menuRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        data-testid="unique-export-dropdown-trigger"
        className={cn(
          "flex items-center gap-2 px-3 py-1.5 rounded-xl text-sm font-medium transition-all duration-200 active:scale-95",
          variantsList[variant],
          (isExporting || isPending) && "opacity-70 cursor-wait"
        )}
        disabled={isExporting || isPending}
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        <Download className="w-4 h-4"  strokeWidth={1.5}/>
        <span>{t('briefingDetail.export.button')}</span>
        <ChevronDown className={cn("w-3 h-3 transition-transform duration-200", isOpen && "rotate-180")}  strokeWidth={1.5}/>
      </button>

      {isOpen && (
        <div className={cn(
          "absolute top-full mt-2 z-50 w-64 rounded-xl md:rounded-[28px] bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] shadow-2xl shadow-black/80 backdrop-blur-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200",
          align === 'right' ? 'right-0' : 'left-0'
        )}>
          <div className="p-1.5 space-y-1">
            <button
              onClick={() => { void exportMarkdown(); }}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-text-primary)] transition-colors group"
            >
              <FileText className="w-4 h-4 text-[var(--color-text-secondary)] group-hover:text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
              <div className="flex-1 text-left">
                <p className="font-medium">{t('briefingDetail.export.markdown')}</p>
                <p className="text-[10px] opacity-60">{t('briefingDetail.export.markdownDesc')}</p>
              </div>
            </button>

            <button
              onClick={() => { void exportHtml(); }}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-text-primary)] transition-colors group"
            >
              <Code className="w-4 h-4 text-[var(--color-text-secondary)] group-hover:text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
              <div className="flex-1 text-left">
                <p className="font-medium">{t('briefingDetail.export.html')}</p>
                <p className="text-[10px] opacity-60">{t('briefingDetail.export.htmlDesc')}</p>
              </div>
            </button>

            <div className="my-1 border-t border-[var(--color-border-subtle)] opacity-50" />

            {!readOnly && (
              localUuid ? (
                <>
                  <button
                    onClick={() => { void copyShareLink(); }}
                    className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-text-primary)] transition-colors group"
                  >
                    {isCopied ? (
                      <Check className="w-4 h-4 text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
                    ) : (
                      <Copy className="w-4 h-4 text-[var(--color-text-secondary)] group-hover:text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
                    )}
                    <div className="flex-1 text-left">
                      <p className="font-medium">{t('briefingDetail.export.copyLink')}</p>
                      <p className="text-[10px] opacity-60">{t('briefingDetail.export.copyLinkDesc')}</p>
                    </div>
                  </button>

                  <button
                    onClick={handleUnshare}
                    disabled={isPending}
                    className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-[var(--color-status-error)]/80 hover:bg-[var(--color-status-error)]/10 hover:text-[var(--color-status-error)] transition-colors group"
                  >
                    {isPending ? (
                      <Loader2 className="w-4 h-4 animate-spin"  strokeWidth={1.5}/>
                    ) : (
                      <Trash2 className="w-4 h-4 text-[var(--color-status-error)]/50 group-hover:text-[var(--color-status-error)]"  strokeWidth={1.5}/>
                    )}
                    <div className="flex-1 text-left">
                      <p className="font-medium">{t('briefingDetail.export.disableShare')}</p>
                      <p className="text-[10px] text-[var(--color-status-error)]/60">{t('briefingDetail.export.disableShareDesc')}</p>
                    </div>
                  </button>
                </>
              ) : (
                <button
                  onClick={handleShare}
                  disabled={isPending}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-text-primary)] transition-colors group"
                >
                  {isPending ? (
                    <Loader2 className="w-4 h-4 text-[var(--color-action-primary)] animate-spin"  strokeWidth={1.5}/>
                  ) : (
                    <Share2 className="w-4 h-4 text-[var(--color-text-secondary)] group-hover:text-[var(--color-action-primary)]"  strokeWidth={1.5}/>
                  )}
                  <div className="flex-1 text-left">
                    <p className="font-medium">{t('briefingDetail.export.enableShare')}</p>
                    <p className="text-[10px] opacity-60">{t('briefingDetail.export.enableShareDesc')}</p>
                  </div>
                </button>
              )
            )}
          </div>
        </div>
      )}
    </div>
  );
}
