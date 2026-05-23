import { describe, it, expect } from 'vitest';
import en from '../../public/locales/en.json';
import sl from '../../public/locales/sl.json';

describe('i18n JSON structure', () => {
  const getKeys = (obj: Record<string, unknown>, prefix = ''): string[] => {
    return Object.keys(obj).reduce((res: string[], el) => {
      const val = obj[el];
      if (typeof val === 'object' && val !== null && !Array.isArray(val)) {
        return [...res, ...getKeys(val as Record<string, unknown>, prefix + el + '.')];
      }
      return [...res, prefix + el];
    }, []);
  };

  const enKeys = getKeys(en as Record<string, unknown>);
  const slKeys = getKeys(sl as Record<string, unknown>);

  // Strip CLDR plural suffixes so each locale can define only the forms
  // its language requires without failing the parity check.
  const pluralSuffix = /_(zero|one|two|few|many|other)$/;
  const normalise = (keys: string[]) =>
    [...new Set(keys.map(k => k.replace(pluralSuffix, '')))].sort((a, b) => a.localeCompare(b));

  it('EN and SL should have the same set of keys', () => {
    const enNorm = normalise(enKeys);
    const slNorm = normalise(slKeys);

    enNorm.forEach(key => {
      if (!slNorm.includes(key)) {
        console.error(`Missing key in SL: ${key}`);
      }
    });

    slNorm.forEach(key => {
      if (!enNorm.includes(key)) {
        console.error(`Missing key in EN: ${key}`);
      }
    });

    expect(slNorm).toEqual(enNorm);
  });

  it('all keys should return strings, not objects', () => {
    const checkValue = (obj: Record<string, unknown>, path: string) => {
        const parts = path.split('.');
        let current: unknown = obj;
        for (const part of parts) {
            current = (current as Record<string, unknown>)[part];
        }
        if (Array.isArray(current)) {
            // Arrays are explicitly allowed for use cases using returnObjects: true
            expect(Array.isArray(current)).toBe(true);
        } else {
            expect(typeof current).toBe('string');
        }
    };

    enKeys.forEach(key => checkValue(en as Record<string, unknown>, key));
    slKeys.forEach(key => checkValue(sl as Record<string, unknown>, key));
  });

  it('all navigation keys should be present', () => {
    const requiredNavigation = [
      'navigation.dashboard',
      'navigation.briefings',
      'navigation.topics',
      'navigation.sources',
      'navigation.administration',
      'navigation.auditLogs',
      'navigation.systemJobs'
    ];
    requiredNavigation.forEach(key => {
      expect(enKeys).toContain(key);
      expect(slKeys).toContain(key);
    });
  });

  it('all profile and dashboard title/description keys should be present', () => {
    expect(enKeys).toContain('profile.title');
    expect(enKeys).toContain('profile.description');
    expect(enKeys).toContain('dashboard.title');
    expect(enKeys).toContain('dashboard.recentBriefings');
    expect(slKeys).toContain('profile.title');
    expect(slKeys).toContain('profile.description');
    expect(slKeys).toContain('dashboard.title');
    expect(slKeys).toContain('dashboard.recentBriefings');
  });
});
