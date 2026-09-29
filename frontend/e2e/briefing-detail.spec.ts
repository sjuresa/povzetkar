import { test, expect } from './fixtures/csp';

/**
 * POV-202 — Lazy-route regression guard for the briefing-detail page.
 *
 * `/briefings/$briefingId` is the only route that is BOTH newly code-split
 * (lazy chunk) AND markdown-heavy (renders `metaSummary` via react-markdown,
 * which now lives in a deferred chunk). This test proves that after route-level
 * lazy-loading, navigating to the route (a) resolves the lazy chunk behind the
 * async auth guard and (b) executes the markdown pipeline inside it — asserting
 * on the actual rendered markdown output, not merely that the page did not error.
 */

const E2E_TIMEOUT = 15000;

// A distinctive marker rendered from the mocked markdown. Kept OUT of a leading
// `# heading`, because BriefingDetailPage strips a leading heading from metaSummary
// (`metaSummary.replace(/^#\s+.*\n?/, '')`) before rendering.
const MARKDOWN_MARKER = 'Distilled signal from POV-202 lazy chunk';

test.describe('Briefing Detail (lazy route + markdown pipeline)', () => {
  test.beforeEach(async ({ page }) => {
    // Bypass the auth guard: checkAuth() probes GET /api/v1/users/me and treats a
    // response with a `data.id` as authenticated.
    await page.route('**/api/v1/users/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: 1,
            name: 'Test User',
            email: 'user@povzetkar.ai',
            permissions: [],
          },
          error: null,
          meta: { timestamp: new Date().toISOString() },
        }),
      });
    });

    // Mock the briefing-detail fetch (GET /api/v1/briefings/1) with markdown content.
    await page.route('**/api/v1/briefings/1', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: 1,
            uuid: 'test-uuid-1',
            topicId: 1,
            topicName: 'Test Topic',
            title: 'Test Briefing',
            briefingDate: '2026-07-05',
            // Markdown: leading heading is stripped by the component, so the marker
            // is emphasised text on its own line and must render via react-markdown.
            metaSummary: `# Executive Summary\n\n**${MARKDOWN_MARKER}**`,
            summaries: [],
          },
          error: null,
          meta: { timestamp: new Date().toISOString() },
        }),
      });
    });
  });

  test('renders markdown content from the deferred chunk', async ({ page }) => {
    await page.goto('/briefings/1');

    // The marker text proves the lazy chunk loaded AND react-markdown executed.
    const marker = page.getByText(MARKDOWN_MARKER);
    await expect(marker).toBeVisible({ timeout: E2E_TIMEOUT });

    // Assert the markdown actually rendered as HTML (bold), not raw `**...**` text —
    // i.e. the markdown pipeline ran, it wasn't printed literally.
    await expect(page.locator('strong', { hasText: MARKDOWN_MARKER })).toBeVisible();
  });
});
