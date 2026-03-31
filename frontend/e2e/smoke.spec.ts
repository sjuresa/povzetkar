import { test, expect } from '@playwright/test';

test('has title', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Povzetkar/);
});

test('renders brand name', async ({ page }) => {
  await page.goto('/');
  // Look for the link by aria-label OR by the text content "Povzetkar"
  await expect(page.getByLabel(/Povzetkar/i).or(page.getByText(/Povzetkar/i, { exact: false })).first()).toBeVisible();
});