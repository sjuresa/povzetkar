import { test, expect } from '@playwright/test';

test.describe('Dashboard MVP', () => {
  test('unauthenticated user is redirected to landing page', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL('/');
    await expect(page.getByText('POVZETKAR')).toBeVisible();
  });

  // Note: Full auth flow test requires a mock OIDC provider or a test account.
  // For this MVP, we verify the redirection logic which is the core of the auth guard.
});
