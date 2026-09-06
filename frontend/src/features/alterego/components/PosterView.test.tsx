import { describe, expect, test } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { renderWithLiveRegion } from '../../../test/testSupport'
import { PosterView } from './PosterView'
import { FALLBACK_NOTICE_COPY } from '../constants'
import type { AlterEgoResponse } from '../types'

/** T039 / T040 — PosterView rendering + FR-214 generic fallback banner. */

const sampleResult: AlterEgoResponse = {
  character: {
    heroTitleLine1: 'PAULA',
    heroTitleLine2: 'The Cloud Guardrail',
    tagline: 'STILL SHIPS ON FRIDAYS.',
    superpowers: ['Rolls back with a single keystroke', 'Hears pager alerts', 'YAML fluency'],
    quote: 'It is always DNS.',
  },
  poster: {
    dataUrl: 'data:image/png;base64,AAAA',
    mediaType: 'image/png',
    widthPx: 900,
    heightPx: 1200,
  },
  meta: {
    outcome: 'real',
    provider: 'gemini',
    correlationId: '00000000-0000-0000-0000-000000000001',
  },
}

describe('PosterView', () => {
  test('renders only the secondary line and three superpowers on screen (017 FR-1713)', () => {
    renderWithLiveRegion(
      <PosterView
        result={sampleResult}
        errorMessage={null}
        firstName="Paula"
        roleLabel="Cloud Architect"
      />,
    )
    // 017 FR-1713 (refined 2026-05-08): heroTitleLine1, tagline, AND
    // the quote are NO LONGER rendered as visible HTML — all three
    // live only on the poster image. heroTitleLine2 + the superpowers
    // list remain on screen as the only HTML next to the poster.
    expect(screen.queryByRole('heading', { name: 'PAULA' })).toBeNull()
    expect(screen.queryByText('STILL SHIPS ON FRIDAYS.')).toBeNull()
    expect(screen.queryByText('It is always DNS.')).toBeNull()
    expect(document.querySelector('.poster-view__title-line-1')).toBeNull()
    expect(document.querySelector('.poster-view__tagline')).toBeNull()
    expect(document.querySelector('.poster-view__quote')).toBeNull()
    expect(screen.getByText('The Cloud Guardrail')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
  })

  test('renders the poster image with widened alt carrying first name + role (017 FR-1714, refined 2026-05-08)', () => {
    renderWithLiveRegion(
      <PosterView
        result={sampleResult}
        errorMessage={null}
        firstName="Paula"
        roleLabel="Cloud Architect"
      />,
    )
    const img = screen.getByRole('img') as HTMLImageElement
    expect(img.alt).toBe('Alter ego poster for Paula, Cloud Architect. It is always DNS.')
    expect(img.src).toBe('data:image/png;base64,AAAA')
    expect(img.width).toBe(900)
    expect(img.height).toBe(1200)
  })

  test('shows the fallback banner when an error message is present', () => {
    renderWithLiveRegion(
      <PosterView
        result={{
          ...sampleResult,
          meta: {
            ...sampleResult.meta,
            outcome: 'fallback',
            provider: 'stub',
            reason: 'network_error',
          },
        }}
        errorMessage="We had trouble reaching the generation service."
        firstName="Paula"
        roleLabel="Cloud Architect"
      />,
    )
    // LiveRegionProvider also renders a role="alert" assertive channel, so
    // getByRole('alert') would match two elements. Scope to the PosterView
    // article so only the banner div matches.
    const article = screen.getByRole('article')
    const banner = within(article).getByRole('alert')
    expect(banner).toHaveTextContent(/had trouble reaching/i)
  })

  test('announces "Your alter ego is ready." politely on success', async () => {
    const { container } = renderWithLiveRegion(
      <PosterView
        result={sampleResult}
        errorMessage={null}
        firstName="Paula"
        roleLabel="Cloud Architect"
      />,
    )
    await waitFor(() => {
      const polite = container.querySelector('[role="status"][aria-live="polite"]')
      expect(polite?.textContent ?? '').toMatch(/alter ego is ready/i)
    })
  })

  test('announces the error message politely when the fallback path fires', async () => {
    const { container } = renderWithLiveRegion(
      <PosterView
        result={sampleResult}
        errorMessage="Service down"
        firstName="Paula"
        roleLabel="Cloud Architect"
      />,
    )
    await waitFor(() => {
      const polite = container.querySelector('[role="status"][aria-live="polite"]')
      expect(polite?.textContent ?? '').toMatch(/service down/i)
    })
  })

  // T040 — FR-214: the fallback notice MUST be the generic single-variant
  // copy. Renders whatever string `errorMessage` carries (PosterView is
  // copy-agnostic by design); the contract here is that callers pass the
  // FALLBACK_NOTICE_COPY constant, and it never mentions the provider or
  // the reason code.
  test('FALLBACK_NOTICE_COPY does not mention the provider or any reason code', () => {
    const forbiddenSubstrings = [
      'gemini',
      'Gemini',
      'google',
      'Google',
      'not_configured',
      'network_error',
      'rate_limited',
      'timeout',
      'malformed_response',
      'safety_refused',
    ]
    for (const s of forbiddenSubstrings) {
      expect(FALLBACK_NOTICE_COPY).not.toContain(s)
    }
  })

  test('banner renders the FALLBACK_NOTICE_COPY when the hook passes it in', () => {
    renderWithLiveRegion(
      <PosterView
        result={{
          ...sampleResult,
          meta: {
            ...sampleResult.meta,
            outcome: 'fallback',
            provider: 'stub',
            reason: 'rate_limited',
          },
        }}
        errorMessage={FALLBACK_NOTICE_COPY}
        firstName="Paula"
        roleLabel="Cloud Architect"
      />,
    )
    const article = screen.getByRole('article')
    const banner = within(article).getByRole('alert')
    expect(banner).toHaveTextContent(FALLBACK_NOTICE_COPY)
    // The `reason` passed through `meta` MUST NOT leak into the UI.
    expect(banner).not.toHaveTextContent('rate_limited')
    expect(banner).not.toHaveTextContent(/gemini/i)
  })
})
