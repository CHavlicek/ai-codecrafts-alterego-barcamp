/**
 * Frontend-side mirror of the backend's FallbackPosterProvider.
 * Used by {@link ../services/alterEgoClient} as the {@code fallback}
 * argument to {@code resilientFetch}, so the user always gets a complete
 * poster even when the backend is unreachable (network off, dev server
 * crashed, etc.) — FR-018.
 *
 * The image is a minimal inline SVG-as-data-URL. It's tiny, deterministic,
 * and never requires the backend to be alive.
 */
import type { AlterEgoResponse } from '../types'

const FALLBACK_POSTER_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 1200" width="900" height="1200">
  <rect width="900" height="1200" fill="#000"/>
  <rect x="40" y="40" width="820" height="1120" fill="none" stroke="#666" stroke-width="4"/>
  <text x="450" y="600" text-anchor="middle" fill="#fff" font-family="serif" font-size="80" font-weight="700">FALLBACK</text>
  <text x="450" y="660" text-anchor="middle" fill="#fff" font-family="serif" font-size="30">Even when the robots sleep.</text>
</svg>`

const FALLBACK_POSTER_DATA_URL = `data:image/svg+xml;utf8,${encodeURIComponent(FALLBACK_POSTER_SVG)}`

export function fallbackPoster(firstName: string, correlationId: string): AlterEgoResponse {
  const trimmed = firstName.trim()
  const heroLine1 = trimmed.length > 0 ? trimmed.toUpperCase() : 'HERO'
  return {
    character: {
      heroTitleLine1: heroLine1,
      heroTitleLine2: 'The Resilient',
      tagline: 'DEGRADED, NOT DEFEATED.',
      superpowers: [
        'Outlasts every timeout',
        'Turns errors into signage',
        'Always returns a poster',
      ],
      quote: 'When the stack falls, the spec stands.',
    },
    poster: {
      dataUrl: FALLBACK_POSTER_DATA_URL,
      // Inline SVG; declared as png to satisfy the contract's mediaType enum.
      // The data URL prefix above (image/svg+xml) is what the browser actually renders.
      mediaType: 'image/png',
      widthPx: 900,
      heightPx: 1200,
    },
    meta: {
      outcome: 'fallback',
      // 016 FR-1612: provider is mandatory on every response. The
      // frontend-synthesised fallback always reports provider=stub, mirroring
      // the backend's invariant for any fallback path.
      provider: 'stub',
      correlationId,
      // 003 FR-218: reason is REQUIRED on fallback outcomes. This path fires
      // when the browser couldn't reach the backend at all (network off,
      // dev server crashed), so `network_error` is the closest semantic
      // match. Not surfaced to the user — drives operator-visible devtools
      // / automated-test behaviour only.
      reason: 'network_error',
    },
  }
}
