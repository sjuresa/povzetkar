import { test, expect } from '@playwright/test';

test.describe('Dashboard MVP', () => {
  test('unauthenticated user is redirected to landing page', async ({ page }) => {
    // Force unauthenticated state
    await page.route('**/api/v1/users/me', route => route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ data: null, error: { code: 'UNAUTHORIZED', message: 'Unauthorized' } })
    }));

    await page.goto('/dashboard');
    await expect(page).toHaveURL('/');
    await expect(page.getByTestId('brand-logo').first()).toBeVisible();
  });

  // Note: Full auth flow test requires a mock OIDC provider or a test account.
  // For this MVP, we verify the redirection logic which is the core of the auth guard.
});
