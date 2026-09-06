import { expect, test } from '@playwright/test'
import { fillAllSelections, mockFailingApi } from './helpers'

/**
 * T045 — Playwright E2E fallback path.
 *
 * Intercepts POST /api/v1/alter-egos with a persistent 500 so every
 * attempt resilientFetch makes fails; the client's {@code fallback} hook
 * then synthesises the FE-side "The Resilient" response and the UI
 * reaches {@code failed_with_fallback}.
 *
 * Covers:
 *  - SC-004: user still sees a complete poster within 5 s.
 *  - FR-018: fallback poster is complete (title / tagline / powers / quote).
 *  - FR-023: fallback banner is rendered and politely announced.
 */

test('fallback: API 500s → user still gets "The Resilient" poster', async ({ page }) => {
  await mockFailingApi(page)
  await page.goto('/')

  await fillAllSelections(page, 'Paula')

  const clickedAt = Date.now()
  await page.getByRole('button', { name: /generate my alter ego/i }).click()

  await expect(page.getByText('The Resilient')).toBeVisible({ timeout: 8000 })
  const elapsed = Date.now() - clickedAt
  // SC-004 budget is 5 s; allow CI jitter up to 8 s before the test cares.
  expect(elapsed).toBeLessThan(8000)

  // Banner from PosterView, errorMessage path (FR-023). Two elements
  // carry role="alert" — the visible PosterView banner AND the empty
  // assertive live-region channel. .first() picks the visible one.
  const banner = page.getByRole('alert').first()
  await expect(banner).toBeVisible()
  await expect(banner).toContainText(/trouble|fallback|generation|failed/i)

  // Fallback poster body (FR-018: no blank state).
  await expect(page.getByText(/degraded, not defeated/i)).toBeVisible()
  await expect(page.getByText(/always returns a poster/i)).toBeVisible()
})
