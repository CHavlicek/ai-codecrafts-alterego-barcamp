import { expect, test } from '@playwright/test'
import { buildSampleResponse, fillAllSelections } from './helpers'

/**
 * 006 T011 — Playwright E2E for the Art Style category.
 *
 * Covers the US1 acceptance scenarios:
 *  - Pick an art style (required) and Generate proceeds.
 *  - Generate remains disabled until the art style is picked.
 *  - The outbound multipart request body carries the chosen kebab-case
 *    wire value on the `selections.artStyle` field (FR-305).
 */

test.describe('Art Style category — 004', () => {
  test('Generate is disabled until the user picks an art style', async ({ page }) => {
    await page.goto('/')

    // Pre-fill everything except the art style.
    await page.getByLabel('Upload photo').setInputFiles({
      name: 'photo.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkAAIAAAoAAv/lPAAAAABJRU5ErkJggg==',
        'base64',
      ),
    })
    await page.getByAltText('Preview of your upload').waitFor({ state: 'visible' })
    await page.getByRole('radio', { name: 'Heroic' }).click()
    await page.getByRole('radio', { name: 'Cloud Architect' }).click()
    await page.getByRole('radio', { name: 'Star Wars' }).click()
    await page.getByLabel('First name').fill('Paula')

    // Generate stays disabled: the art style is still missing.
    const generate = page.getByRole('button', { name: /generate my alter ego/i })
    await expect(generate).toBeDisabled()

    // Picking the style flips Generate to enabled.
    await page.getByRole('radio', { name: 'Oil Painting' }).click()
    await expect(generate).toBeEnabled()
  })

  test('chosen art style is sent to the backend with its kebab wire value', async ({ page }) => {
    let capturedArtStyle: string | undefined

    await page.route('**/api/v1/alter-egos', async (route) => {
      const body = route.request().postDataBuffer()
      if (body) {
        const text = body.toString('utf8')
        const match = text.match(/"artStyle"\s*:\s*"([a-z0-9-]+)"/)
        capturedArtStyle = match?.[1]
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildSampleResponse()),
      })
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula', 'Japanese Woodblock')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    expect(capturedArtStyle).toBe('japanese-woodblock')
  })
})
