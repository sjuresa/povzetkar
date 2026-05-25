import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import ReactMarkdown from 'react-markdown';
import { REHYPE_PLUGINS } from './markdown';

describe('REHYPE_PLUGINS', () => {
  it('renders external links with target="_blank" and rel="noopener noreferrer"', () => {
    render(
      <ReactMarkdown rehypePlugins={REHYPE_PLUGINS}>
        {'[Visit Example](https://example.com)'}
      </ReactMarkdown>
    );

    const link = screen.getByRole('link', { name: 'Visit Example' });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(link).toHaveAttribute('href', 'https://example.com');
  });
});
