import type { Page } from '@playwright/test'

/**
 * Generate a valid PNG inside the browser via {@code canvas.toBlob} and
 * return its bytes. This guarantees Chromium can decode it back through
 * {@code createImageBitmap} (inside downscalePhoto), which is stricter
 * than the {@code <img>} pipeline: hand-rolled base64 PNGs of
 * questionable validity can render via {@code <img>} yet fail
 * {@code createImageBitmap} with InvalidStateError.
 */
export async function generatePngInBrowser(page: Page, width = 16, height = 16): Promise<Buffer> {
  const bytes = await page.evaluate(
    async ({ width, height }) => {
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('No 2D context available')
      ctx.fillStyle = '#c84aff'
      ctx.fillRect(0, 0, width, height)
      const blob = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(
          (b) => (b ? resolve(b) : reject(new Error('canvas.toBlob returned null'))),
          'image/png',
        )
      })
      const buf = await blob.arrayBuffer()
      return Array.from(new Uint8Array(buf))
    },
    { width, height },
  )
  return Buffer.from(bytes)
}

/**
 * Well-formed base64 PNG used in mock {@code AlterEgoResponse} bodies —
 * only rendered via {@code <img src=…>}, never via {@code createImageBitmap},
 * so the stricter decoder doesn't apply. 67-byte 1×1 PNG.
 */
export const RESPONSE_POSTER_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkAAIAAAoAAv/lPAAAAABJRU5ErkJggg=='

export interface SuccessResponseOverrides {
  heroTitleLine1?: string
  heroTitleLine2?: string
  tagline?: string
  /** 003 rename: `success` → `real`. */
  outcome?: 'real' | 'fallback'
  /** 003 FR-218: required when outcome === 'fallback', ignored otherwise. */
  reason?:
    | 'not_configured'
    | 'network_error'
    | 'rate_limited'
    | 'timeout'
    | 'malformed_response'
    | 'safety_refused'
  /**
   * 016 FR-1612: mandatory `provider` discriminator. Defaults are picked
   * from the `outcome` (real → 'gemini', fallback → 'stub') so existing
   * tests don't have to specify it; tests that exercise the fal.ai path
   * pass {@code provider: 'falai'} explicitly.
   */
  provider?: 'gemini' | 'falai' | 'stub'
}

export function buildSampleResponse(overrides: SuccessResponseOverrides = {}) {
  return {
    character: {
      heroTitleLine1: overrides.heroTitleLine1 ?? 'PAULA',
      heroTitleLine2: overrides.heroTitleLine2 ?? 'The Cloud Guardrail',
      tagline: overrides.tagline ?? 'STILL SHIPS ON FRIDAYS.',
      superpowers: [
        'Rolls back with a single keystroke',
        'Hears pager alerts before they fire',
        'Speaks fluent YAML in all dialects',
      ],
      quote: "It's always DNS.",
    },
    poster: {
      dataUrl: `data:image/png;base64,${RESPONSE_POSTER_BASE64}`,
      mediaType: 'image/png',
      widthPx: 900,
      heightPx: 1200,
    },
    meta: {
      outcome: overrides.outcome ?? 'real',
      correlationId: '00000000-0000-0000-0000-000000000001',
      // 016 FR-1612: provider is mandatory. Defaults track outcome unless
      // overridden — fallback ⇒ stub, real ⇒ gemini (preserving 003 e2e
      // behaviour for tests that don't care which real provider answered).
      provider: overrides.provider ?? (overrides.outcome === 'fallback' ? 'stub' : 'gemini'),
      // 003 FR-218: reason is REQUIRED when outcome === 'fallback', omitted
      // otherwise. Default to 'network_error' on fallback overrides unless
      // the caller specifies.
      ...(overrides.outcome === 'fallback' ? { reason: overrides.reason ?? 'network_error' } : {}),
    },
  }
}

/**
 * Intercept {@code POST /api/v1/alter-egos} and fulfill it with a canned
 * 200 response. Must be called before any interaction that triggers the
 * mutation.
 */
export async function mockHappyApi(
  page: Page,
  overrides?: SuccessResponseOverrides,
): Promise<void> {
  await page.route('**/api/v1/alter-egos', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: { 'X-Request-Id': '00000000-0000-0000-0000-000000000001' },
      body: JSON.stringify(buildSampleResponse(overrides)),
    })
  })
}

/**
 * Intercept and return 500 on every call. resilientFetch will retry (fast
 * back-offs in the UI) and then the {@code fallback} hook in
 * alterEgoClient synthesises a FE-side fallback response so the UI still
 * renders "The Resilient".
 */
export async function mockFailingApi(page: Page): Promise<void> {
  await page.route('**/api/v1/alter-egos', async (route) => {
    await route.fulfill({
      status: 500,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'about:blank',
        title: 'Internal Server Error',
        status: 500,
        detail: 'Forced failure for E2E.',
        instance: '/api/v1/alter-egos',
      }),
    })
  })
}

/**
 * Seed a committed photo by driving the 004 inline capture UI: tap the
 * circle (opens the fake MediaStream thanks to the `--use-fake-device-
 * for-media-stream` launch flag in `playwright.config.ts`), press the
 * shutter, press Keep photo. `generatePngInBrowser` is retained for
 * specs that prefer hand-crafted PNG content, but this helper now uses
 * Chromium's synthetic green/red camera pattern — sufficient for every
 * downstream regression assertion that only cares that `photoBlob` is
 * non-null.
 */
export async function selectPhoto(page: Page): Promise<void> {
  await page.getByRole('button', { name: /take a photo of yourself/i }).click()
  await page.getByRole('button', { name: /take photo/i }).waitFor({ state: 'visible' })
  await page.getByRole('button', { name: /take photo/i }).click()
  await page.getByRole('button', { name: /keep photo/i }).waitFor({ state: 'visible' })
  await page.getByRole('button', { name: /keep photo/i }).click()
  // After Keep commits, the circle returns to the empty-state button but
  // with the committed image cropped inside it. The existing
  // "Retake / clear" control appears below.
  await page.getByRole('button', { name: /retake \/ clear/i }).waitFor({ state: 'visible' })
}

/**
 * Click one option in each required picker + enter a first name. Vibe is
 * optional (002 FR-118) so it's intentionally left unselected here. Use
 * {@link fillAllSelectionsWithVibe} to exercise the vibe-present path.
 *
 * 006 delta: Art Style is required — picks "Pixel Art" by default. Pass
 * {@code artStyleLabel} to exercise a different style.
 */
export async function fillAllSelections(
  page: Page,
  firstName = 'Paula',
  artStyleLabel: string = 'Pixel Art',
): Promise<void> {
  await selectPhoto(page)
  await page.getByRole('radio', { name: 'Heroic' }).click()
  await page.getByRole('radio', { name: 'Cloud Architect' }).click()
  await page.getByRole('radio', { name: 'Star Wars' }).click()
  await page.getByRole('radio', { name: artStyleLabel }).click()
  // 011: the name input's label switches between "First name" (single
  // mode, the default) and "Group name" (after the photo-mode toggle is
  // flipped). Match either so callers don't need to thread the label.
  await page.getByLabel(/^(first name|group name)$/i).fill(firstName)
}

/** Variant used by specs that want to exercise the optional Vibe sub-group. */
export async function fillAllSelectionsWithVibe(page: Page, firstName = 'Paula'): Promise<void> {
  await fillAllSelections(page, firstName)
  await page.getByRole('button', { name: 'Rebel' }).click()
}
