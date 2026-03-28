import { test, expect } from '@playwright/test';

test('login button redirects to /auth/login', async ({ page }) => {
  await page.goto('/');
  
  // Find the login button and click it
  const loginButton = page.getByRole('button', { name: /Get Key Insights/i });
  await expect(loginButton).toBeVisible();
  
  // Intercept the navigation to /auth/login
  // Since this is a full page navigation, we can check the URL change
  // We don't want to actually go to Google, so we'll just check if it tries to go to /auth/login
  
  await loginButton.click();
  
  // Wait for the URL to change - it should either be our /auth/login or 
  // the resulting redirect to accounts.google.com
  await page.waitForURL(url => 
    url.toString().includes('/auth/login') || 
    url.toString().includes('google.com')
  );
  
  const currentUrl = page.url();
  expect(currentUrl).toMatch(/auth\/login|google\.com/);
});
