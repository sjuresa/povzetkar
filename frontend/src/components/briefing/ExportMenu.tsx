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
        const newUuid = response.data;
        if (newUuid) {
          setLocalUuid(newUuid);
        }
        queryClient.invalidateQueries({ queryKey: ['briefings'] });
        queryClient.invalidateQueries({ queryKey: ['briefing-detail', briefing.id] });
        toast.success('Public sharing enabled');
      },
      onError: () => toast.error('Failed to enable sharing')
    }
  });

  const unshareMutation = usePostApiV1BriefingsIdUnshare({
    mutation: {
      onSuccess: () => {
        setLocalUuid(null);
        queryClient.invalidateQueries({ queryKey: ['briefings'] });
        queryClient.invalidateQueries({ queryKey: ['briefing-detail', briefing.id] });
        toast.success('Public sharing disabled');
      },
      onError: () => toast.error('Failed to disable sharing')
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
      const { data: response } = await briefingDetailQuery.refetch();
      const detail = response?.data;
      if (!detail) throw new Error('Failed to fetch briefing detail');
      
      return { 
        metaSummary: detail.metaSummary || '', 
        content: detail.summaries?.map(s => s.summaryText).join('\n\n---\n\n') || ''
      };
    } catch (err) {
      toast.error('Could not prepare export data');
      return null;
    } finally {
      setIsExporting(false);
    }
  };

  const exportMarkdown = async () => {
    const data = await ensureData();
    if (!data) return;

    const content = `# ${briefing.title}\n\n` +
      `**Topic:** ${briefing.topicName}\n` +
      `**Date:** ${briefing.briefingDate}\n\n` +
      `## Executive Summary\n\n${data.metaSummary}\n\n` +
      `---\n\n${data.content}`;
    
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

    const htmlContent = `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${briefing.title}</title>
    <style>
        @page { size: A4; margin: 20mm; }
        body { 
            font-family: 'Times New Roman', Times, serif; 
            line-height: 1.5; 
            color: #000; 
            max-width: 800px; 
            margin: 0 auto; 
            padding: 0;
            background-color: #fff;
        }
        h1 { font-size: 24pt; margin-top: 0; margin-bottom: 5pt; color: #000; border-bottom: 1pt solid #000; padding-bottom: 5pt; }
        .metadata { color: #555; font-style: italic; margin-bottom: 20pt; font-size: 10pt; font-family: sans-serif; }
        .summary-box { border: 1pt solid #000; padding: 15pt; margin-bottom: 20pt; background: #fff; }
        .summary-title { font-weight: bold; margin-bottom: 5pt; font-family: sans-serif; text-transform: uppercase; font-size: 9pt; letter-spacing: 0.5pt; }
        h2 { font-size: 16pt; color: #000; margin-top: 25pt; border-bottom: 0.5pt solid #ccc; padding-bottom: 3pt; }
        p { margin-bottom: 10pt; text-align: justify; }
        ul, ol { margin-bottom: 10pt; padding-left: 20pt; }
        li { margin-bottom: 5pt; }
        hr { border: 0; border-top: 0.5pt solid #000; margin: 30pt 0; }
        .footer { text-align: center; color: #777; font-size: 8pt; margin-top: 40pt; font-family: sans-serif; border-top: 0.5pt solid #ddd; padding-top: 10pt; }
        @media print {
            body { max-width: 100%; margin: 0; }
            .no-print { display: none; }
        }
    </style>
</head>
<body>
    <h1>${briefing.title}</h1>
    <div class="metadata">
        Topic: ${briefing.topicName} | Date: ${briefing.briefingDate}
    </div>
    
    <div class="summary-box">
        <div class="summary-title">Executive Summary</div>
        ${summaryHtml}
    </div>

    <div class="content">
        ${textHtml}
    </div>
    
    <div class="footer">
        Generated by Povzetkar | ${new Date().toLocaleDateString()}
    </div>
</body>
</html>`;
    
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
    } catch (err) {
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
              onClick={exportMarkdown}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-slate-300 hover:bg-slate-800 hover:text-white transition-colors group"
            >
              <FileText className="w-4 h-4 text-slate-500 group-hover:text-indigo-400" />
              <div className="flex-1 text-left">
                <p className="font-medium">Markdown</p>
                <p className="text-[10px] text-slate-500">Plain text format</p>
              </div>
            </button>

            <button
              onClick={exportHtml}
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
                  onClick={copyShareLink}
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
                  onClick={() => unshareMutation.mutate({ id: briefing.id! })}
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
                onClick={() => shareMutation.mutate({ id: briefing.id! })}
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
