import type { Page } from '@playwright/test';
import { test, expect } from './fixtures/csp';

test('has title', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Povzetkar/);
});

test('renders brand name', async ({ page }) => {
  // Force unauthenticated state
  await page.route('**/api/v1/users/me', route => route.fulfill({
    status: 401,
    contentType: 'application/json',
    body: JSON.stringify({ data: null, error: { code: 'UNAUTHORIZED', message: 'Unauthorized' } })
  }));

  await page.goto('/');
  // Look for the link by aria-label OR by the text content "Povzetkar"
  await expect(page.getByTestId('brand-logo').first()).toBeVisible();
});
// TC-UI-006: the pre-paint script in index.html applies theme and language before React loads.
// The app bundle is blocked, so only that script can have set <html class> and <html lang> — in the "csp"
// project this also proves the strict CSP lets the script run (its sha256 is allowed).
// Dev server: /src/main.tsx; built SPA (vite preview): /assets/index-<hash>.js.
async function blockAppBundle(page: Page) {
  await page.route('**/src/main.tsx', route => route.abort());
  await page.route(/\/assets\/index-[\w-]+\.js$/, route => route.abort());
}

test.describe('pre-paint theme and language', () => {
  test.use({ colorScheme: 'light', locale: 'sl-SI' });

  test('first visit follows the system theme and browser language', async ({ page }) => {
    await blockAppBundle(page);
    await page.goto('/');
    await expect(page.locator('html')).toHaveClass(/\blight\b/);
    await expect(page.locator('html')).toHaveAttribute('lang', 'sl');
  });

  test('a stored choice wins over the system theme and browser language', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('theme-storage', JSON.stringify({ state: { theme: 'dark' }, version: 0 }));
      localStorage.setItem('povzetkar-language', 'en');
    });
    await blockAppBundle(page);
    await page.goto('/');
    await expect(page.locator('html')).toHaveClass(/\bdark\b/);
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });
});

// A failed system-metadata call (backend restarting, dev without backend) must not replace the landing page
// with the error page — the footer just omits the version (POV-220; global QueryClient throwOnError is true).
test('landing page survives a failing system-metadata call', async ({ page }) => {
  await page.route('**/api/v1/users/me', route => route.fulfill({
    status: 401,
    contentType: 'application/json',
    body: JSON.stringify({ data: null, error: { code: 'UNAUTHORIZED', message: 'Unauthorized' } })
  }));
  let metadataCalls = 0;
  await page.route('**/api/v1/system/metadata', route => {
    metadataCalls++;
    return route.fulfill({ status: 500, contentType: 'application/json', body: '{"data":null}' });
  });

  await page.goto('/');
  // Client retries (3) + one react-query retry = 8 calls before the query settles as failed.
  await expect.poll(() => metadataCalls, { timeout: 15000 }).toBeGreaterThanOrEqual(8);
  // The failed query settles once the network is idle; an error boundary would render right after that.
  await page.waitForLoadState('networkidle');

  await expect(page.getByText('A glitch in the matrix')).toHaveCount(0);
  await expect(page.getByTestId('hero-cta')).toBeVisible();
});
