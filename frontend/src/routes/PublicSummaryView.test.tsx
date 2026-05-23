import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import rehypeExternalLinks from 'rehype-external-links';

const plugins = [rehypeRaw, rehypeSanitize, [rehypeExternalLinks, { target: '_blank', rel: ['noopener', 'noreferrer'] }]] as const;

describe('rehypeExternalLinks plugin chain', () => {
  it('renders external links with target="_blank" and rel="noopener noreferrer"', () => {
    render(
      <ReactMarkdown rehypePlugins={plugins}>
        {'[Visit Example](https://example.com)'}
      </ReactMarkdown>
    );

    const link = screen.getByRole('link', { name: 'Visit Example' });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(link).toHaveAttribute('href', 'https://example.com');
  });
});
