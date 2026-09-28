import { describe, it, expect } from 'vitest';
import { resolveInitialLanguage } from './language';

describe('resolveInitialLanguage', () => {
  it('prefers an explicit stored choice over the browser language', () => {
    expect(resolveInitialLanguage('en', 'sl-SI')).toBe('en');
    expect(resolveInitialLanguage('sl', 'en-US')).toBe('sl');
  });

  it('ignores an unknown stored value', () => {
    expect(resolveInitialLanguage('de', 'sl')).toBe('sl');
    expect(resolveInitialLanguage('de', 'de-DE')).toBe('en');
  });

  it('follows a Slovenian browser when nothing is stored', () => {
    expect(resolveInitialLanguage(null, 'sl')).toBe('sl');
    expect(resolveInitialLanguage(null, 'sl-SI')).toBe('sl');
    expect(resolveInitialLanguage(null, 'SL-si')).toBe('sl');
  });

  it('falls back to English for any other or missing browser language', () => {
    expect(resolveInitialLanguage(null, 'de-DE')).toBe('en');
    expect(resolveInitialLanguage(null, 'en-GB')).toBe('en');
    expect(resolveInitialLanguage(null, undefined)).toBe('en');
  });
});
