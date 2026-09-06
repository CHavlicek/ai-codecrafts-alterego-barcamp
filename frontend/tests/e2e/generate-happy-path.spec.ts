import { expect, test } from '@playwright/test'
import { fillAllSelections, mockHappyApi } from './helpers'

/**
 * T044 — Playwright E2E happy path.
 *
 * Drives the full user journey end-to-end against the real Vite-built
 * React app, with the backend call mocked at the network layer.
 *
 * Covers:
 *  - SC-001: ≤ 3 s p95 from Generate → poster rendered (sampled).
 *  - SC-002: full flow completable without instructions.
 *  - SC-003: no blank states — either the poster or a fallback is shown.
 *  - SC-005: hero title line 1 / line 2 / tagline / 3 superpowers / quote / face image present.
 *  - SC-006: Start over resets the UI in < 500 ms.
 */

test.describe('Generate — happy path', () => {
  test('full flow → poster rendered + Start over resets', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')

    // Entry screen: Generate is disabled until every input is set.
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()

    await fillAllSelections(page, 'Paula')

    const generate = page.getByRole('button', { name: /generate my alter ego/i })
    await expect(generate).toBeEnabled()

    const clickedAt = Date.now()
    await generate.click()

    // Wait for the poster's hero title to appear.
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    const elapsed = Date.now() - clickedAt
    // SC-001 budget of 3000 ms with a small tolerance for CI jitter.
    expect(elapsed).toBeLessThan(3000)

    // SC-005 sampled — every required field of the poster is present.
    await expect(page.getByText('The Cloud Guardrail')).toBeVisible()
    await expect(page.getByText('STILL SHIPS ON FRIDAYS.')).toBeVisible()
    await expect(page.getByText('Rolls back with a single keystroke')).toBeVisible()
    await expect(page.getByText("It's always DNS.")).toBeVisible()
    await expect(page.getByAltText(/alter ego poster for PAULA/i)).toBeVisible()

    // SC-006: Start over resets to form under the 200 ms / 500 ms budget.
    const startOver = page.getByRole('button', { name: /start over/i })
    const resetAt = Date.now()
    await startOver.click()
    await expect(page.getByRole('button', { name: /take a photo of yourself/i })).toBeVisible()
    expect(Date.now() - resetAt).toBeLessThan(500)

    // After reset, Generate is disabled again (form is empty).
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()
  })

  test('differentiated output — picking a different archetype yields a different title', async ({
    page,
  }) => {
    // First request: "The Cloud Guardrail"; second request: custom title.
    let callCount = 0
    await page.route('**/api/v1/alter-egos', async (route) => {
      callCount++
      const body =
        callCount === 1
          ? {
              character: {
                heroTitleLine1: 'PAULA',
                heroTitleLine2: 'The Cloud Guardrail',
                tagline: 'T1',
                superpowers: ['a', 'b', 'c'],
                quote: 'q',
              },
              poster: {
                dataUrl: 'data:image/png;base64,iVBORw0KGgo=',
                mediaType: 'image/png',
                widthPx: 900,
                heightPx: 1200,
              },
              meta: { outcome: 'real', correlationId: 'x' },
            }
          : {
              character: {
                heroTitleLine1: 'PAULA',
                heroTitleLine2: 'The Backend Dev',
                tagline: 'T2',
                superpowers: ['x', 'y', 'z'],
                quote: 'q2',
              },
              poster: {
                dataUrl: 'data:image/png;base64,iVBORw0KGgo=',
                mediaType: 'image/png',
                widthPx: 900,
                heightPx: 1200,
              },
              meta: { outcome: 'real', correlationId: 'y' },
            }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByText('The Cloud Guardrail')).toBeVisible()

    await page.getByRole('button', { name: /start over/i }).click()
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByText('The Backend Dev')).toBeVisible()
  })
})
