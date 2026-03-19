import { test, expect } from '@playwright/test';

/**
 * Timeout to account for axios-retry logic (3 retries with exponential backoff).
 * Standard 5s timeout is insufficient when simulating transient failures.
 */
const E2E_RESILIENCE_TIMEOUT = 15000;

test.describe('Global Error Handling', () => {
  test.beforeEach(async ({ page }) => {
    // 1. Mock Auth - Always return a valid user to bypass login
    await page.route('**/api/v1/users/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ 
          data: { 
            id: 1, 
            name: 'Test Administrator', 
            email: 'admin@povzetkar.ai', 
            permissions: ['ADMIN_AUDIT_LOG_VIEW', 'ADMIN_DASHBOARD_VIEW'] 
          },
          error: null,
          meta: { timestamp: new Date().toISOString() }
        }),
      });
    });
  });

  test('should show Error Page when dashboard fails with 500', async ({ page }) => {
    // Mock Dashboard failure with 500
    await page.route('**/api/v1/dashboard', async (route) => {
      await route.fulfill({ 
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          data: null,
          error: { code: 'INTERNAL_SERVER_ERROR', message: 'Something went Boom!' },
          meta: { timestamp: new Date().toISOString() }
        })
      });
    });

    await page.goto('/dashboard');
    
    // TanStack Query with throwOnError will trigger the ErrorComponent (ErrorPage)
    // defined in router.tsx
    await expect(page.getByText('A glitch in the matrix')).toBeVisible({ timeout: E2E_RESILIENCE_TIMEOUT });
    await expect(page.getByText('Something went wrong while loading this page')).toBeVisible({ timeout: E2E_RESILIENCE_TIMEOUT });
    await expect(page.getByText('Go to Dashboard')).toBeVisible({ timeout: E2E_RESILIENCE_TIMEOUT });
    
    // Verify that "Try Refreshing" exists
    await expect(page.getByRole('button', { name: 'Try Refreshing' })).toBeVisible();
  });

  test('should show Error Page when audit log fails catastrophically', async ({ page }) => {
    // Mock Audit Log failure
    await page.route('**/api/v1/admin/audit-log*', async (route) => {
      await route.fulfill({ status: 500 });
    });

    await page.goto('/admin/audit-log');
    
    await expect(page.getByText('A glitch in the matrix')).toBeVisible({ timeout: E2E_RESILIENCE_TIMEOUT });
  });
});
