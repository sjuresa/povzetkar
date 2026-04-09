import { test, expect } from '@playwright/test';

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
    body: '<html><body>Mock Login Page</body></html>'
  }));

  await page.goto('/');
  
  // Find the login CTA and click it
  const loginButton = page.getByTestId('hero-cta');
  await expect(loginButton).toBeVisible();
  
  // Intercept the navigation to /auth/login
  // Since this is a full page navigation, we can check the URL change
  // We don't want to actually go to Google, so we'll just check if it tries to go to /auth/login
  
  test.setTimeout(60000);
  await loginButton.click({ force: true });
  
  await page.waitForURL(url => url.toString().includes('/auth/login'), { timeout: 15000 });
  
  const currentUrl = page.url();
  expect(currentUrl).toContain('/auth/login');
});
