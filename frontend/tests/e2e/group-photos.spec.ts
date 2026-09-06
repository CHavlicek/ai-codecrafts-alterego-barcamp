import { expect, test } from '@playwright/test'
import { buildSampleResponse, fillAllSelections } from './helpers'

/**
 * 011 Group Photos — end-to-end flow.
 *
 * Covers:
 *  - FR-1003 / SC-1001: switch defaults to Single Person on a fresh session
 *    (aria-checked="false" because Group is the "on" state of the toggle).
 *  - FR-1002: clicking the toggle flips aria-checked.
 *  - FR-1008 / SC-1002: clicking Generate after flipping sends
 *    `selections.photoMode === "group"` in the outbound multipart body.
 *  - FR-1011 / SC-1004: Start-over resets the toggle back to Single.
 */

test.describe('Group Photos — 011', () => {
  test('default Setup state has the Photo Mode toggle in the off (Single) state', async ({
    page,
  }) => {
    await page.goto('/')
    const toggle = page.getByRole('switch', { name: /photo mode/i })
    await expect(toggle).toHaveAttribute('aria-checked', 'false')
    await expect(toggle).toHaveAttribute('data-state', 'single')
  })

  test('FR-1008 / SC-1002 — flipping the toggle sends photoMode: "group" on Generate', async ({
    page,
  }) => {
    let capturedPhotoMode: string | null = null
    await page.route('**/api/v1/alter-egos', async (route) => {
      const request = route.request()
      const multipart = request.postData() ?? ''
      // The selections part is a JSON blob inside a multipart body; a
      // substring match is enough to pin the field.
      const selectionsMatch = multipart.match(/"photoMode":"([^"]+)"/)
      capturedPhotoMode = selectionsMatch ? (selectionsMatch[1] ?? null) : null
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'X-Request-Id': '00000000-0000-0000-0000-000000000011' },
        body: JSON.stringify(buildSampleResponse()),
      })
    })

    await page.goto('/')
    const toggle = page.getByRole('switch', { name: /photo mode/i })
    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-checked', 'true')
    await expect(toggle).toHaveAttribute('data-state', 'group')

    await fillAllSelections(page, 'The Architects')
    const generate = page.getByRole('button', { name: /generate my alter ego/i })
    await expect(generate).toBeEnabled()
    await generate.click()

    // Wait for the poster to land so the fetch has definitely fired.
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
    expect(capturedPhotoMode).toBe('group')
  })

  test('FR-1011 / SC-1004 — Start-over resets the toggle back to Single', async ({ page }) => {
    await page.route('**/api/v1/alter-egos', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'X-Request-Id': '00000000-0000-0000-0000-000000000012' },
        body: JSON.stringify(buildSampleResponse()),
      })
    })

    await page.goto('/')
    const toggle = page.getByRole('switch', { name: /photo mode/i })
    await toggle.click()
    await fillAllSelections(page, 'The Architects')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    await page.getByRole('button', { name: /start over/i }).click()
    // Back on the Setup tab, the toggle has reverted to Single (off).
    await expect(toggle).toHaveAttribute('aria-checked', 'false')
    await expect(toggle).toHaveAttribute('data-state', 'single')
  })
})
