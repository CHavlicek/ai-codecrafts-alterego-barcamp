import { expect, test } from '@playwright/test'
import { fillAllSelections, mockHappyApi } from './helpers'

/**
 * 016 T034 — Playwright spec for the fal.ai happy path.
 *
 * <p>The fal.ai run is deliberately indistinguishable from the Gemini run
 * at the user-visible UI layer (FR-1622). The only difference between the
 * two paths is operator-visible metadata. This test:
 *
 * <ul>
 *   <li>Stubs the network response with {@code meta.provider: 'falai'},
 *       {@code meta.outcome: 'real'}.</li>
 *   <li>Asserts the user-visible flow lands on a complete poster — same
 *       hero title, same tagline, same superpowers as the Gemini path.</li>
 *   <li>Asserts the fallback notice is NOT shown (it's a real-success).</li>
 *   <li>Does NOT assert any provider-specific UI surface — proving the
 *       parity guarantee from FR-1622 / SC-1610.</li>
 * </ul>
 */

test.describe('Generate — fal.ai happy path (016)', () => {
  test('user-visible flow on the falai provider is identical to Gemini', async ({ page }) => {
    await mockHappyApi(page, { provider: 'falai' })
    await page.goto('/')

    await fillAllSelections(page, 'Paula')
    const generate = page.getByRole('button', { name: /generate my alter ego/i })
    await expect(generate).toBeEnabled()
    await generate.click()

    // Same poster rendering as the Gemini path.
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
    await expect(page.getByText('The Cloud Guardrail')).toBeVisible()
    await expect(page.getByText('STILL SHIPS ON FRIDAYS.')).toBeVisible()
    await expect(page.getByAltText(/alter ego poster for PAULA/i)).toBeVisible()

    // FR-1622: no provider name surfaces in the UI. The fallback banner
    // (which would mention nothing about the provider anyway) MUST be
    // absent on a real-success run.
    const article = page.getByRole('article')
    await expect(article.getByRole('alert')).toHaveCount(0)
    // Defensive: the words "fal.ai", "falai", "Gemini", "stub" must not
    // appear anywhere in the article DOM.
    const articleText = await article.innerText()
    expect(articleText.toLowerCase()).not.toContain('fal.ai')
    expect(articleText.toLowerCase()).not.toContain('falai')
    expect(articleText.toLowerCase()).not.toContain('gemini')
    expect(articleText.toLowerCase()).not.toContain('stub')
  })
})
