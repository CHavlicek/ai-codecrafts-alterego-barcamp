import { describe, expect, test } from 'vitest'
import { formatPercent, nextDelayMs, percentForElapsed } from './progressTicker'

/**
 * 013 Image Generation Progress Indicator — pure helper tests.
 *
 * <p>The three exports are pure functions with injectable RNG, so every
 * assertion is deterministic and runs without timers, the DOM, or React.
 *
 * <p>Contract: {@code specs/013-progress-indicator/contracts/progressTicker.md}.
 */

describe('percentForElapsed', () => {
  test.each([
    [0, 0],
    [200, 1],
    [4_000, 20],
    [10_000, 50],
    [19_800, 99],
    [20_000, 99], // cap engages exactly at the 20 s boundary (FR-1304 / FR-1305)
    [60_000, 99],
    [Number.MAX_SAFE_INTEGER, 99],
    [-1, 0], // defensive clamp; performance.now() is monotonic in practice
  ])('percentForElapsed(%i) === %i', (input, expected) => {
    expect(percentForElapsed(input)).toBe(expected)
  })

  test('is monotonic non-decreasing on a 1000-sample sweep over [0, 60_000] ms', () => {
    let prev = -Infinity
    for (let i = 0; i <= 1000; i += 1) {
      const elapsed = (60_000 * i) / 1000
      const value = percentForElapsed(elapsed)
      expect(value).toBeGreaterThanOrEqual(prev)
      prev = value
    }
  })
})

describe('nextDelayMs', () => {
  test('returns 2000 when rng() === 0 (lower endpoint reachable)', () => {
    expect(nextDelayMs(() => 0)).toBe(2000)
  })

  test('returns 6000 at midpoint rng() === 0.5', () => {
    expect(nextDelayMs(() => 0.5)).toBe(6000)
  })

  test('returns 10000 when rng() approaches 1 (upper endpoint reachable)', () => {
    expect(nextDelayMs(() => 0.99999)).toBe(10000)
    expect(nextDelayMs(() => 0.999_999_999)).toBe(10000)
  })

  test('every result over a 200-sample rng sweep is an integer in [2000, 10000]', () => {
    for (let i = 0; i < 200; i += 1) {
      const v = i / 200
      const delay = nextDelayMs(() => v)
      expect(Number.isInteger(delay)).toBe(true)
      expect(delay).toBeGreaterThanOrEqual(2000)
      expect(delay).toBeLessThanOrEqual(10000)
    }
  })

  test('defaults to Math.random when no rng is passed', () => {
    const delay = nextDelayMs()
    expect(Number.isInteger(delay)).toBe(true)
    expect(delay).toBeGreaterThanOrEqual(2000)
    expect(delay).toBeLessThanOrEqual(10000)
  })
})

describe('formatPercent', () => {
  test.each([
    [0, '0%'],
    [1, '1%'],
    [37, '37%'],
    [99, '99%'],
    [100, '99%'], // defensive clamp — second line of defence behind FR-1305
    [-3, '0%'],
  ])('formatPercent(%i) === %s', (input, expected) => {
    expect(formatPercent(input)).toBe(expected)
  })
})
