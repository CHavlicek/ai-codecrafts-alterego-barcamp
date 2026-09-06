import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { fillAllSelections, mockFailingApi, mockHappyApi } from './helpers'

/**
 * T047 — @axe-core/playwright scan across four page states.
 *
 * Drives SC-007's automated a11y gate: zero `serious` or `critical`
 * WCAG 2.1 AA violations on any of the four meaningful states.
 *
 * States scanned:
 *  1. Entry screen (blank form).
 *  2. Loading state (mid-generation — delayed API).
 *  3. Success state (poster rendered).
 *  4. Fallback state (API 500 → "The Resilient" banner + poster).
 */

async function assertNoSeriousOrCriticalViolations(page: Page, label: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const blocking = results.violations.filter(
    (v) => v.impact === 'serious' || v.impact === 'critical',
  )
  expect(blocking, `a11y violations on ${label}: ${JSON.stringify(blocking, null, 2)}`).toEqual([])
}

test('entry screen has no serious/critical a11y violations', async ({ page }) => {
  await page.goto('/')
  await assertNoSeriousOrCriticalViolations(page, 'entry screen')
})

test('loading state has no serious/critical a11y violations', async ({ page }) => {
  // Delay the response so the loading state persists long enough to scan.
  await page.route('**/api/v1/alter-egos', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 4000))
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        character: {
          heroTitleLine1: 'PAULA',
          heroTitleLine2: 'The Cloud Guardrail',
          tagline: 'T',
          superpowers: ['a', 'b', 'c'],
          quote: 'q',
        },
        poster: {
          dataUrl:
            'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkAAIAAAoAAv/lPAAAAABJRU5ErkJggg==',
          mediaType: 'image/png',
          widthPx: 900,
          heightPx: 1200,
        },
        meta: { outcome: 'real', provider: 'gemini', correlationId: 'x' },
      }),
    })
  })
  await page.goto('/')
  await fillAllSelections(page, 'Paula')
  await page.getByRole('button', { name: /generate my alter ego/i }).click()
  // Two matches — the visible <p> and the live-region <div role="status">.
  // .first() picks the visible paragraph.
  await expect(page.getByText(/generating your alter ego/i).first()).toBeVisible()
  await assertNoSeriousOrCriticalViolations(page, 'loading state')
})

test('success state has no serious/critical a11y violations', async ({ page }) => {
  await mockHappyApi(page)
  await page.goto('/')
  await fillAllSelections(page, 'Paula')
  await page.getByRole('button', { name: /generate my alter ego/i }).click()
  await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
  await assertNoSeriousOrCriticalViolations(page, 'success state')
})

test('fallback state has no serious/critical a11y violations', async ({ page }) => {
  await mockFailingApi(page)
  await page.goto('/')
  await fillAllSelections(page, 'Paula')
  await page.getByRole('button', { name: /generate my alter ego/i }).click()
  await expect(page.getByText('The Resilient')).toBeVisible({ timeout: 8000 })
  await assertNoSeriousOrCriticalViolations(page, 'fallback state')
})

// 004 SC-305 — axe across the three photo states the camera-only capture
// UX introduces: empty circle, live viewfinder, camera-unavailable.
test('004 photo-empty state has no serious/critical a11y violations', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('button', { name: /take a photo of yourself/i })).toBeVisible()
  await assertNoSeriousOrCriticalViolations(page, 'photo empty')
})

test('004 photo-live viewfinder state has no serious/critical a11y violations', async ({
  page,
}) => {
  await page.goto('/')
  await page.getByRole('button', { name: /take a photo of yourself/i }).click()
  await page.locator('video.photo-intake__viewfinder').waitFor({ state: 'visible' })
  await assertNoSeriousOrCriticalViolations(page, 'photo live')
})

test('004 photo-unavailable state has no serious/critical a11y violations', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'mediaDevices', {
      value: undefined,
      configurable: true,
    })
  })
  await page.goto('/')
  await page.getByRole('button', { name: /take a photo of yourself/i }).click()
  await expect(
    page
      .getByRole('alert')
      .filter({ hasText: /doesn't support/i })
      .first(),
  ).toBeVisible({ timeout: 2000 })
  await assertNoSeriousOrCriticalViolations(page, 'photo unavailable')
})
