import { useState, useEffect } from 'react';
import { useGetApiV1Sources } from '../api/generated/content-sources/content-sources';
import { Pagination } from '../components/ui/Pagination';
import { Skeleton } from '../components/ui/Skeleton';
import { Link } from '@tanstack/react-router';
import { Search, Globe, Youtube } from 'lucide-react';
import { useDebounce } from '../hooks/useDebounce';
import { cn } from '../util/cn';

/**
 * SourcesLibraryPage displays a searchable, paginated list of all content sources.
 * Part of POV-148 implementation.
 */
export function SourcesLibraryPage() {
  const [page, setPage] = useState(0);
  const [size] = useState(12);
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 500);

  const { data: response, isLoading } = useGetApiV1Sources({
    page,
    size,
    search: debouncedSearch || undefined
  }, {
    query: {
      placeholderData: (previousData) => previousData, // Smooth transitions during pagination
    }
  });

  // Reset to first page when debounced search term changes
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const data = response?.data;
  const items = data?.items || [];
  const totalCount = data?.totalCount || 0;
  const totalPages = Math.ceil(totalCount / size);

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div>
          <h2 className="text-4xl font-extrabold tracking-tight text-[var(--color-text-primary)] mb-2 text-glow">Source Library</h2>
          <p className="text-[var(--color-text-secondary)] max-w-lg">
            Manage and browse all content sources integrated with Povzetkar. Inspect specific items and their processing status.
          </p>
        </div>
        
        <div className="flex items-center space-x-2 glass border border-[var(--color-border-subtle)] rounded-2xl px-4 py-2">
           <div className="text-right">
             <div className="text-[10px] font-bold text-[var(--color-text-secondary)] uppercase tracking-widest">Active Sources</div>
             <div className="text-sm font-mono text-indigo-500 font-bold">{totalCount.toLocaleString()} Integrated</div>
           </div>
           <div className="w-8 h-8 rounded-lg bg-indigo-500/10 flex items-center justify-center text-indigo-500 ml-2">
              <Globe className="w-4 h-4" />
           </div>
        </div>
      </div>

      <div className="bg-[var(--color-background-alt)] border border-[var(--color-border-subtle)] rounded-3xl p-6 shadow-xl shadow-black/5 backdrop-blur-sm">
        <div className="relative group">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--color-text-secondary)] group-focus-within:text-indigo-500 transition-colors" />
          <input 
            type="text" 
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0); // Reset to first page on search
            }}
            placeholder="Search sources by name, identifier or description..."
            className="w-full bg-[var(--color-surface-elevated)] border border-[var(--color-border-subtle)] rounded-2xl pl-11 pr-4 py-3 text-sm focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all outline-none text-[var(--color-text-primary)] shadow-inner"
          />
        </div>
      </div>

      <div className="space-y-4">
        {isLoading && items.length === 0 ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
             {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-48 w-full" />
             ))}
          </div>
        ) : items.length === 0 ? (
          <div className="glass border border-dashed border-[var(--color-border-subtle)] rounded-3xl p-12 text-center">
            <div className="w-16 h-16 rounded-full bg-[var(--color-surface-deep)] flex items-center justify-center mx-auto mb-4 text-[var(--color-text-secondary)]">
              <Search className="w-8 h-8" />
            </div>
            <h3 className="text-lg font-bold text-[var(--color-text-primary)]">No sources found</h3>
            <p className="text-sm text-[var(--color-text-secondary)] mt-1">Try adjusting your search terms.</p>
          </div>
        ) : (
          <>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              {items.map((source) => (
                <Link 
                  key={source.id}
                  to="/sources/$sourceId"
                  params={{ sourceId: source.id?.toString() || '' }}
                  className="group glass border border-[var(--color-border-subtle)] rounded-3xl p-6 hover:bg-[var(--color-surface-hover)] hover:border-indigo-500/50 transition-all hover:scale-[1.02] active:scale-[0.98] relative overflow-hidden"
                >
                  <div className="absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity pointer-events-none">
                    {source.platform === 'YOUTUBE' ? <Youtube size={64} /> : <Globe size={64} />}
                  </div>
                  
                  <div className="flex items-start justify-between mb-4">
                    <div className={cn(
                      "w-10 h-10 rounded-xl flex items-center justify-center shadow-inner",
                      source.platform === 'YOUTUBE' ? "bg-red-500/10 text-red-500" : "bg-blue-500/10 text-blue-500"
                    )}>
                      {source.platform === 'YOUTUBE' ? <Youtube size={20} /> : <Globe size={20} />}
                    </div>
                  </div>

                  <h3 className="font-bold text-[var(--color-text-primary)] text-lg mb-1 truncate group-hover:text-indigo-400 transition-colors">
                    {source.name}
                  </h3>
                  <p className="text-xs font-mono text-[var(--color-text-secondary)] mb-3 opacity-70">
                    {source.sourceIdentifier}
                  </p>
                  
                  <p className="text-sm text-[var(--color-text-secondary)] line-clamp-2 mb-4 h-10">
                    {source.description || 'No description provided.'}
                  </p>

                  <div className="flex items-center justify-between pt-4 border-t border-[var(--color-border-subtle)]/50">
                    <div className="text-[10px] font-bold uppercase tracking-wider text-indigo-500 bg-indigo-500/10 px-2 py-1 rounded-lg">
                      {source.platform}
                    </div>
                    <div className="flex items-center text-xs text-[var(--color-text-secondary)]">
                      <span className="font-bold text-[var(--color-text-primary)] mr-1">{source.subscriberCount?.toLocaleString() || 0}</span>
                      Subscribers
                    </div>
                  </div>
                </Link>
              ))}
            </div>
            
            <Pagination 
              page={page} 
              totalPages={totalPages} 
              onPageChange={setPage} 
              isLoading={isLoading} 
            />
          </>
        )}
      </div>
    </div>
  );
}
