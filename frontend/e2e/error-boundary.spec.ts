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
    await expect(page.getByText('Something went wrong while loading this page')).toBeVisible();
    await expect(page.getByText('Go to Dashboard')).toBeVisible();
    
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

  /**
   * TC-GRX-002 (Transient Path) / PW-005: Global Error Catching — Toast
   * Verifies that a mutation failure triggers a visible error toast notification
   * instead of crashing the entire page. This complements the fatal crash tests above.
   * 
   * Flow: Navigate to /topics → hover card → click delete → accept confirm → assert error toast.
   */
  test('should show error toast when a mutation fails (GRX-002 transient path)', async ({ page }) => {
    // 1. Mock Topics API — differentiate GET (listing) vs DELETE (failure)
    await page.route('**/api/v1/topics**', async (route, request) => {
      if (request.method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              items: [{
                id: 1,
                name: 'Test Topic',
                enabled: true,
                version: 0,
                contentSources: [],
                lastRunDate: null
              }]
            },
            error: null,
            meta: {
              timestamp: new Date().toISOString(),
              pagination: {
                totalElements: 1, page: 0, size: 100,
                totalPages: 1, hasNext: false
              }
            }
          })
        });
      } else if (request.method() === 'DELETE') {
        await route.fulfill({ status: 500 });
      } else {
        await route.continue();
      }
    });

    await page.goto('/topics');

    // 2. Wait for the topic card to render
    const topicCard = page.locator('div.group').filter({ hasText: 'Test Topic' }).first();
    await expect(topicCard).toBeVisible({ timeout: E2E_RESILIENCE_TIMEOUT });

    // 3. Accept the window.confirm dialog that will appear on delete
    page.on('dialog', dialog => dialog.accept());

    // 4. Hover topic card to reveal action buttons (opacity-0 → opacity-100 on group-hover)
    await topicCard.hover();

    // 5. Click the delete button via resilient data-testid
    const deleteButton = topicCard.getByTestId('delete-topic-button');
    await deleteButton.click();

    // 6. Assert: sonner error toast appears (locale-independent selector)
    await expect(
      page.locator('[data-sonner-toast][data-type="error"]')
    ).toBeVisible({ timeout: E2E_RESILIENCE_TIMEOUT });
  });
});
