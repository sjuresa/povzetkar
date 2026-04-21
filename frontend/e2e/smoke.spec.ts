import { test, expect } from '@playwright/test';

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