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
import { useGetApiV1BriefingsId, usePostApiV1BriefingsIdShare, usePostApiV1BriefingsIdUnshare } from '../../api/generated/briefings/briefings';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { generateBriefingHtml, generateBriefingMarkdown } from '../../util/exportTemplates';

interface ExportMenuProps {
  briefing: {
    id?: number;
    uuid: string | null;
    title: string;
    topicName: string;
    briefingDate: string;
    metaSummary?: string;
    summaries?: { summaryText?: string }[];
  };
  variant?: 'ghost' | 'outline' | 'primary';
  className?: string;
  align?: 'left' | 'right';
}

export function ExportMenu({ briefing, variant = 'outline', className, align = 'right' }: Readonly<ExportMenuProps>) {
  const [isOpen, setIsOpen] = useState(false);
  const [isCopied, setIsCopied] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [localUuid, setLocalUuid] = useState<string | null>(briefing.uuid);
  const menuRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  // Sync state if uuid changes externally
  useEffect(() => {
    setLocalUuid(briefing.uuid);
  }, [briefing.uuid]);

  // Defer fetching full content until needed (if not already provided)
  const briefingDetailQuery = useGetApiV1BriefingsId(briefing.id!, {
    query: {
      enabled: false,
    }
  });

  const shareMutation = usePostApiV1BriefingsIdShare({
    mutation: {
      onSuccess: (response) => {
        // response.data is the UUID string
        const newUuid = response.data as unknown as string;
        if (newUuid) {
          setLocalUuid(newUuid);
        }
        void queryClient.invalidateQueries({ queryKey: ['briefings'] });
        void queryClient.invalidateQueries({ queryKey: ['briefing-detail', briefing.id] });
        toast.success('Public sharing enabled');
      },
      onError: () => { toast.error('Failed to enable sharing'); }
    }
  });

  const unshareMutation = usePostApiV1BriefingsIdUnshare({
    mutation: {
      onSuccess: () => {
        setLocalUuid(null);
        void queryClient.invalidateQueries({ queryKey: ['briefings'] });
        void queryClient.invalidateQueries({ queryKey: ['briefing-detail', briefing.id] });
        toast.success('Public sharing disabled');
      },
      onError: () => { toast.error('Failed to disable sharing'); }
    }
  });

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
    if (briefing.metaSummary && briefing.summaries) {
      return { 
        metaSummary: briefing.metaSummary,
        content: briefing.summaries.map(s => s.summaryText).join('\n\n---\n\n')
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
      toast.error('Could not prepare export data');
      return null;
    } finally {
      setIsExporting(false);
    }
  };

  const exportMarkdown = async () => {
    const data = await ensureData();
    if (!data) return;

    const content = generateBriefingMarkdown({
      title: briefing.title,
      topicName: briefing.topicName,
      briefingDate: briefing.briefingDate,
      metaSummary: data.metaSummary,
      content: data.content
    });
    
    const safeTopic = briefing.topicName.replaceAll(/\s+/g, '_');
    const safeTitle = briefing.title.replaceAll(/\s+/g, '_');
    const fileName = `${safeTopic}_${safeTitle}.md`;
    
    downloadFile(content, fileName, 'text/markdown');
    toast.success('Markdown exported');
    setIsOpen(false);
  };

  const exportHtml = async () => {
    const data = await ensureData();
    if (!data) return;

    // Use marked to parse markdown to HTML and sanitize the output
    const unsafeSummaryHtml = marked.parse(data.metaSummary || '') as string;
    const unsafeContentHtml = marked.parse(data.content || '') as string;
    
    const summaryHtml = DOMPurify.sanitize(unsafeSummaryHtml);
    const textHtml = DOMPurify.sanitize(unsafeContentHtml);

    const htmlContent = generateBriefingHtml({
      title: briefing.title,
      topicName: briefing.topicName,
      briefingDate: briefing.briefingDate,
      summaryHtml,
      contentHtml: textHtml
    });
    
    const safeTopic = briefing.topicName.replaceAll(/\s+/g, '_');
    const safeTitle = briefing.title.replaceAll(/\s+/g, '_');
    const fileName = `${safeTopic}_${safeTitle}.html`;
    
    downloadFile(htmlContent, fileName, 'text/html');
    toast.success('HTML exported (B&W Print Ready)');
    setIsOpen(false);
  };

  const copyShareLink = async () => {
    const url = `${globalThis.location.origin}/s/${localUuid}`;
    try {
      await navigator.clipboard.writeText(url);
      setIsCopied(true);
      toast.success('Share link copied to clipboard');
      setTimeout(() => setIsCopied(false), 2000);
      setIsOpen(false);
    } catch {
      toast.error('Failed to copy link');
    }
  };

  const variantsList = {
    primary: "bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-500/20",
    outline: "bg-slate-900/40 border border-slate-700/50 text-slate-200 hover:bg-slate-800/60 hover:border-slate-600",
    ghost: "bg-transparent text-slate-400 hover:text-slate-100 hover:bg-slate-800/40"
  };

  return (
    <div className={cn("relative", className)} ref={menuRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          "flex items-center gap-2 px-3 py-1.5 rounded-xl text-sm font-medium transition-all duration-200 active:scale-95",
          variantsList[variant],
          isExporting && "opacity-70 cursor-wait"
        )}
        disabled={isExporting}
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        <Download className="w-4 h-4" />
        <span>Export</span>
        <ChevronDown className={cn("w-3 h-3 transition-transform duration-200", isOpen && "rotate-180")} />
      </button>

      {isOpen && (
        <div className={cn(
          "absolute top-full mt-2 z-50 w-56 rounded-2xl bg-slate-900 border border-slate-800 shadow-2xl shadow-black/80 backdrop-blur-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200",
          align === 'right' ? 'right-0' : 'left-0'
        )}>
          <div className="p-1.5 space-y-1">
            <button
              onClick={() => { void exportMarkdown(); }}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-slate-300 hover:bg-slate-800 hover:text-white transition-colors group"
            >
              <FileText className="w-4 h-4 text-slate-500 group-hover:text-indigo-400" />
              <div className="flex-1 text-left">
                <p className="font-medium">Markdown</p>
                <p className="text-[10px] text-slate-500">Plain text format</p>
              </div>
            </button>

            <button
              onClick={() => { void exportHtml(); }}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-slate-300 hover:bg-slate-800 hover:text-white transition-colors group"
            >
              <Code className="w-4 h-4 text-slate-500 group-hover:text-amber-400" />
              <div className="flex-1 text-left">
                <p className="font-medium">HTML / Print</p>
                <p className="text-[10px] text-slate-500">B&W Print Ready</p>
              </div>
            </button>

            <div className="my-1 border-t border-slate-800" />

            {localUuid ? (
              <>
                <button
                  onClick={() => { void copyShareLink(); }}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-slate-300 hover:bg-slate-800 hover:text-white transition-colors group"
                >
                  {isCopied ? (
                    <Check className="w-4 h-4 text-emerald-400" />
                  ) : (
                    <Copy className="w-4 h-4 text-slate-500 group-hover:text-emerald-400" />
                  )}
                  <div className="flex-1 text-left">
                    <p className="font-medium">Copy Share Link</p>
                    <p className="text-[10px] text-slate-500">URL for public access</p>
                  </div>
                </button>

                <button
                  onClick={() => { void unshareMutation.mutate({ id: briefing.id! }); }}
                  disabled={unshareMutation.isPending}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-rose-400/80 hover:bg-rose-500/10 hover:text-rose-400 transition-colors group"
                >
                  {unshareMutation.isPending ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Trash2 className="w-4 h-4 text-rose-500/50 group-hover:text-rose-500" />
                  )}
                  <div className="flex-1 text-left">
                    <p className="font-medium">Disable Share Link</p>
                    <p className="text-[10px] text-rose-500/60">Invalidates public URL</p>
                  </div>
                </button>
              </>
            ) : (
              <button
                onClick={() => { void shareMutation.mutate({ id: briefing.id! }); }}
                disabled={shareMutation.isPending}
                className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-slate-300 hover:bg-slate-800 hover:text-white transition-colors group"
              >
                {shareMutation.isPending ? (
                  <Loader2 className="w-4 h-4 text-emerald-400 animate-spin" />
                ) : (
                  <Share2 className="w-4 h-4 text-slate-500 group-hover:text-emerald-400" />
                )}
                <div className="flex-1 text-left">
                  <p className="font-medium">Enable Share Link</p>
                  <p className="text-[10px] text-slate-500">Generate a public URL</p>
                </div>
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
