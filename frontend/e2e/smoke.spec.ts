import { test, expect } from '@playwright/test';

test('has title', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Povzetkar/);
});

test('renders brand name', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('link', { name: /Povzetkar/i }).first()).toBeVisible();
});