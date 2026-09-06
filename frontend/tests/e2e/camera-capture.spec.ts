import { expect, test } from '@playwright/test'
import { mockHappyApi } from './helpers'

/**
 * 004 T010 + T017 + T022 — Playwright camera-capture E2E.
 *
 * Runs in Chromium with the `--use-fake-ui-for-media-stream` and
 * `--use-fake-device-for-media-stream` launch flags (see
 * {@link ../../playwright.config.ts}). Those flags provide a
 * deterministic green/red test-pattern stream so `getUserMedia` resolves
 * predictably, and auto-grant the camera permission prompt.
 *
 * Covers:
 *  - T010 happy path: circle → live → shutter → still → Retake →
 *    shutter → Keep → committed → Generate enabled.
 *  - T010 Retake reuses the stream (single getUserMedia call across the
 *    full flow).
 *  - T017 DOM-absence assertions for the removed Camera / Upload pill
 *    buttons across the three photo states.
 *  - T022 unavailable path: MediaDevices absent → polite message +
 *    Generate stays disabled.
 */

test.describe('004 camera capture — happy path', () => {
  test('tap circle → live → shutter → still → Retake → shutter → Keep → photo committed', async ({
    page,
  }) => {
    // Instrument getUserMedia to count invocations before any script runs.
    await page.addInitScript(() => {
      const g = window as unknown as {
        __gumCount?: number
        __origGetUserMedia?: (c: MediaStreamConstraints) => Promise<MediaStream>
      }
      g.__gumCount = 0
      const md = navigator.mediaDevices
      if (md?.getUserMedia) {
        const original = md.getUserMedia.bind(md)
        md.getUserMedia = (constraints: MediaStreamConstraints) => {
          g.__gumCount = (g.__gumCount ?? 0) + 1
          return original(constraints)
        }
      }
    })

    await mockHappyApi(page)
    await page.goto('/')

    const circle = page.getByRole('button', { name: /take a photo of yourself/i })
    await expect(circle).toBeVisible()

    // Step 1: activate the circle — live viewfinder renders inline.
    await circle.click()
    await expect(page.locator('video.photo-intake__viewfinder')).toBeVisible()
    const shutter = page.getByRole('button', { name: /take photo/i })
    const cancel = page.getByRole('button', { name: /^cancel$/i })
    await expect(shutter).toBeVisible()
    await expect(cancel).toBeVisible()

    // Step 2: shutter → still-preview with Keep + Retake.
    await shutter.click()
    await expect(page.locator('img.photo-intake__still')).toBeVisible()
    const keep = page.getByRole('button', { name: /keep photo/i })
    const retake = page.getByRole('button', { name: /^retake$/i })
    await expect(keep).toBeVisible()
    await expect(retake).toBeVisible()

    // Step 3: Retake returns to live (stream reused).
    await retake.click()
    await expect(page.locator('video.photo-intake__viewfinder')).toBeVisible()

    // Step 4: shutter again + Keep commits.
    await page.getByRole('button', { name: /take photo/i }).click()
    await expect(page.locator('img.photo-intake__still')).toBeVisible()
    await page.getByRole('button', { name: /keep photo/i }).click()
    await expect(page.locator('img.photo-intake__image')).toBeVisible()

    // Stream-reuse assertion: only one getUserMedia call across the flow.
    const gumCount = await page.evaluate(
      () => (window as unknown as { __gumCount?: number }).__gumCount ?? -1,
    )
    expect(gumCount).toBe(1)

    // Fill the rest of Setup and assert Generate is enabled.
    await page.getByRole('radio', { name: 'Heroic' }).click()
    await page.getByRole('radio', { name: 'Cloud Architect' }).click()
    await page.getByRole('radio', { name: 'Star Wars' }).click()
    await page.getByLabel('First name').fill('Paula')
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeEnabled()
  })
})

test.describe('004 camera capture — removed controls (US2 / SC-302)', () => {
  const viewports = [
    { label: 'desktop', width: 1440, height: 900 },
    { label: 'tablet', width: 768, height: 1024 },
    { label: 'mobile', width: 375, height: 812 },
  ]

  for (const { label, width, height } of viewports) {
    test(`no Camera/Upload pill buttons or file input at ${label} viewport`, async ({ page }) => {
      await page.setViewportSize({ width, height })
      await page.goto('/')

      // Empty state.
      await expect(page.getByRole('button', { name: /^camera$/i })).toHaveCount(0)
      await expect(page.getByLabel(/upload photo/i)).toHaveCount(0)
      await expect(page.locator('input[type="file"]')).toHaveCount(0)
      await expect(page.locator('.photo-intake__pill')).toHaveCount(0)
      await expect(page.locator('.photo-intake__file-input')).toHaveCount(0)

      // Live state.
      await page.getByRole('button', { name: /take a photo of yourself/i }).click()
      await page.locator('video.photo-intake__viewfinder').waitFor({ state: 'visible' })
      await expect(page.getByRole('button', { name: /^camera$/i })).toHaveCount(0)
      await expect(page.getByLabel(/upload photo/i)).toHaveCount(0)

      // Still-preview state.
      await page.getByRole('button', { name: /take photo/i }).click()
      await page.locator('img.photo-intake__still').waitFor({ state: 'visible' })
      await expect(page.getByRole('button', { name: /^camera$/i })).toHaveCount(0)
      await expect(page.getByLabel(/upload photo/i)).toHaveCount(0)
    })
  }
})

test.describe('004 camera capture — unavailable path (US3 / FR-312)', () => {
  test('MediaDevices absent → polite message + Generate stays disabled', async ({ page }) => {
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'mediaDevices', {
        value: undefined,
        configurable: true,
      })
    })
    await page.goto('/')

    await page.getByRole('button', { name: /take a photo of yourself/i }).click()

    // A role=alert element with FR-313 "supported browser" variant copy.
    await expect(
      page
        .getByRole('alert')
        .filter({ hasText: /doesn't support/i })
        .first(),
    ).toBeVisible({ timeout: 2000 })

    // Raw DOMException name MUST NOT appear anywhere.
    const bodyText = await page.locator('body').innerText()
    expect(bodyText).not.toMatch(/NotAllowedError|NotFoundError|NotReadableError/)

    // Generate stays disabled (no photo committed).
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()
  })
})
