import { test, expect } from '@playwright/test';

test.describe('Auth & Authorization', () => {
  test('login button redirects to /auth/login', async ({ page }) => {
    // Force unauthenticated state
    await page.route('**/api/v1/users/me', route => route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ data: null, error: { code: 'UNAUTHORIZED', message: 'Unauthorized' } })
    }));

    // Mock the auth login redirect to avoid backend dependency
    await page.route('**/auth/login', route => route.fulfill({
      status: 200,
      contentType: 'text/html',
      body: '<html lang="en"><body>Mock Login Page</body></html>'
    }));

    await page.goto('/');
    
    // Find the login CTA and click it
    const loginButton = page.getByTestId('hero-cta');
    await expect(loginButton).toBeVisible();
    
    test.setTimeout(60000);
    await loginButton.click();
    
    await page.waitForURL(url => url.toString().includes('/auth/login'), { timeout: 15000 });
    
    const currentUrl = page.url();
    expect(currentUrl).toContain('/auth/login');
  });

  /**
   * TC-SEC-003 / PW-007: Session Expiry Auto-Login
   * Verifies that a 401 response from a protected API triggers an automatic redirect
   * to /auth/login with the current path encoded as a ?redirect= param.
   * The backend LoginResource.resolveSafeDestination() validates and applies the redirect
   * after OAuth completes, returning the user to their original page.
   */
  test('401 from protected API redirects to /auth/login with redirect param (SEC-003)', async ({ page }) => {
    // 1. Mock Auth — authenticated user on first /me call (session appears valid initially)
    let meCallCount = 0;
    await page.route('**/api/v1/users/me', route => {
      meCallCount++;
      if (meCallCount === 1) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: { id: 1, name: 'Test User', email: 'user@povzetkar.ai', permissions: ['TOPIC_VIEW'] },
            error: null,
            meta: { timestamp: new Date().toISOString() }
          })
        });
      }
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ data: null, error: { code: 'UNAUTHORIZED', message: 'Unauthorized' }, meta: { timestamp: new Date().toISOString() } })
      });
    });

    // 2. Mock dashboard — returns 401 to simulate session expiry mid-use
    await page.route('**/api/v1/dashboard', route => route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ data: null, error: { code: 'UNAUTHORIZED', message: 'Unauthorized' }, meta: { timestamp: new Date().toISOString() } })
    }));

    // 3. Intercept the navigation to /auth/login before it leaves the SPA
    await page.route('**/auth/login**', route => {
      return route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<html lang="en"><body>Mock Login</body></html>'
      });
    });

    // 4. Navigate to dashboard (authenticated route)
    await page.goto('/dashboard');

    // 5. Wait for the redirect to /auth/login to fire
    await page.waitForURL(url => url.toString().includes('/auth/login'), { timeout: 10000 });

    // 6. Assert: URL contains redirect param pointing to /dashboard
    const currentUrl = page.url();
    expect(currentUrl).toContain('/auth/login');
    expect(currentUrl).toContain('redirect=');
    const url = new URL(currentUrl);
    const redirectParam = url.searchParams.get('redirect');
    expect(redirectParam).toBe('/dashboard');
  });

  /**
   * TC-SEC-002 / PW-006: Role Protection
   * Verifies that admin sections are blocked for users without admin permissions.
   * The adminLayoutRoute.beforeLoad guard in router.tsx checks for ADMIN_AUDIT_LOG_VIEW
   * or ADMIN_DASHBOARD_VIEW permissions and redirects to /dashboard if missing.
   */
  test('non-admin user is redirected away from admin routes (SEC-002)', async ({ page }) => {
    // 1. Mock Auth — authenticated user with NO admin permissions
    await page.route('**/api/v1/users/me', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          id: 1,
          name: 'Regular User',
          email: 'user@povzetkar.ai',
          permissions: []
        },
        error: null,
        meta: { timestamp: new Date().toISOString() }
      })
    }));

    // 2. Mock Dashboard API — prevent error boundary after redirect
    await page.route('**/api/v1/dashboard', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: { briefings: [], topics: [] },
        error: null,
        meta: { timestamp: new Date().toISOString() }
      })
    }));

    // 3. Attempt to navigate to admin-protected route
    await page.goto('/admin/audit-log');

    // 4. Assert: redirected to /dashboard (adminLayoutRoute.beforeLoad guard)
    await expect(page).toHaveURL('/dashboard');
  });
});
