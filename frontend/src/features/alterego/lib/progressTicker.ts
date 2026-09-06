/**
 * 013 Image Generation Progress Indicator — pure helpers.
 *
 * <p>Three pure functions back the live percent rendered inside the
 * pulsating loading circle. They are kept pure (no React, no DOM, no
 * timers) so the component layer can be a thin glue around a
 * fully-tested numeric core.
 *
 * <p>Contract: see {@code specs/013-progress-indicator/contracts/progressTicker.md}.
 * Design rationale: see {@code specs/013-progress-indicator/research.md}.
 */

// Single source of truth for the RNG contract — defined alongside the 009
// Surprise Me randomiser so seeded test RNGs are interchangeable.
export type { Rng } from './randomSelections'
import type { Rng } from './randomSelections'

/** Total elapsed-time budget that maps to the full percentage range, in ms. */
const FULL_PROGRESS_MS = 20_000

/** HARD CAP — the displayed value MUST never reach 100% (spec FR-1305 / SC-1302). */
const MAX_PERCENT = 99

/** Random-delay window for the tick chain (FR-1306 / SC-1304), in ms. Inclusive on both ends. */
const MIN_DELAY_MS = 2_000
const MAX_DELAY_MS = 10_000

export function percentForElapsed(elapsedMs: number): number {
  const raw = Math.floor((100 * elapsedMs) / FULL_PROGRESS_MS)
  return Math.min(MAX_PERCENT, Math.max(0, raw))
}

export function nextDelayMs(rng: Rng = Math.random): number {
  const span = MAX_DELAY_MS - MIN_DELAY_MS + 1 // inclusive on both endpoints
  return MIN_DELAY_MS + Math.floor(rng() * span)
}

export function formatPercent(percent: number): string {
  const clamped = Math.min(MAX_PERCENT, Math.max(0, Math.floor(percent)))
  return `${clamped}%`
}
