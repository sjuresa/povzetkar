import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import rehypeExternalLinks from 'rehype-external-links';

export const REHYPE_PLUGINS = [
  rehypeRaw,
  rehypeSanitize,
  [rehypeExternalLinks, { target: '_blank', rel: ['noopener', 'noreferrer'] }],
] as const;
