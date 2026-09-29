import { defineConfig, devices } from '@playwright/test';

const DEV_URL = 'http://localhost:5173';
// Built SPA served by `vite preview` with the production Content-Security-Policy (POV-214).
const PREVIEW_URL = 'http://localhost:4173';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], baseURL: DEV_URL },
    },
    {
      // Same specs against the production build under the production CSP; the csp fixture fails a test on
      // any policy violation.
      name: 'csp',
      use: { ...devices['Desktop Chrome'], baseURL: PREVIEW_URL },
    },
  ],
  webServer: [
    {
      command: 'npm run dev',
      url: DEV_URL,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run build && npm run preview',
      url: PREVIEW_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
  ],
});
