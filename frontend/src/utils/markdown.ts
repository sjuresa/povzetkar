import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import rehypeExternalLinks from 'rehype-external-links';

import type { PluggableList } from 'unified';

export const REHYPE_PLUGINS: PluggableList = [
  rehypeRaw,
  rehypeSanitize,
  [rehypeExternalLinks, { target: '_blank', rel: ['noopener', 'noreferrer'] }],
];
