import { test as base, expect } from '@playwright/test';

// Fails a test on any Content-Security-Policy violation (POV-214). In the project "csp" the built SPA is
// served with the production policy; in the dev-server project there is no policy, so nothing is reported.
export const test = base.extend<{ cspViolations: string[] }>({
  cspViolations: [
    async ({ page }, use) => {
      const violations: string[] = [];
      await page.exposeFunction('__reportCspViolation', (violation: string) => violations.push(violation));
      await page.addInitScript(() => {
        document.addEventListener('securitypolicyviolation', (event) => {
          const report = (window as unknown as { __reportCspViolation: (v: string) => void }).__reportCspViolation;
          report(`${event.violatedDirective} blocked ${event.blockedURI || 'inline'}`
            + ` at ${event.sourceFile || '?'}:${event.lineNumber}:${event.columnNumber}`
            + (event.sample ? ` (${event.sample})` : ''));
        });
      });
      // No console listener: Chrome logs every violation there too, so each would be reported twice.

      await use(violations);

      expect(violations, 'Content-Security-Policy violations').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };
