import { describe, it, expect } from 'vitest';
import en from '../../public/locales/en.json';
import sl from '../../public/locales/sl.json';

describe('i18n JSON structure', () => {
  const getKeys = (obj: any, prefix = ''): string[] => {
    return Object.keys(obj).reduce((res: string[], el) => {
      if (typeof obj[el] === 'object' && obj[el] !== null && !Array.isArray(obj[el])) {
        return [...res, ...getKeys(obj[el], prefix + el + '.')];
      }
      return [...res, prefix + el];
    }, []);
  };

  const enKeys = getKeys(en);
  const slKeys = getKeys(sl);

  it('EN and SL should have the same set of keys', () => {
    // Check if sl has all en keys
    enKeys.forEach(key => {
      if (!slKeys.includes(key)) {
          console.error(`Missing key in SL: ${key}`);
      }
    });

    // Check if en has all sl keys
    slKeys.forEach(key => {
        if (!enKeys.includes(key)) {
            console.error(`Missing key in EN: ${key}`);
        }
    });

    expect(slKeys.sort()).toEqual(enKeys.sort());
  });

  it('all keys should return strings, not objects', () => {
    const checkValue = (obj: any, path: string) => {
        const parts = path.split('.');
        let current = obj;
        for (const part of parts) {
            current = current[part];
        }
        expect(typeof current).toBe('string');
    };

    enKeys.forEach(key => checkValue(en, key));
    slKeys.forEach(key => checkValue(sl, key));
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
