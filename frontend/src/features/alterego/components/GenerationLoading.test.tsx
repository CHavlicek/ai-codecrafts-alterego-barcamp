import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { act, screen, waitFor } from '@testing-library/react'
import { renderWithLiveRegion } from '../../../test/testSupport'
import { GenerationLoading } from './GenerationLoading'

/** T038 — GenerationLoading announces the loading state via ARIA live region. */
describe('GenerationLoading', () => {
  test('renders a visible "Generating your alter ego" notice', () => {
    renderWithLiveRegion(<GenerationLoading />)
    expect(screen.getByText(/generating your alter ego/i)).toBeInTheDocument()
  })

  test('marks itself busy and lists the three generation steps', () => {
    const { container } = renderWithLiveRegion(<GenerationLoading />)
    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull()
    const steps = screen.getAllByRole('listitem')
    expect(steps).toHaveLength(3)
  })

  test('pushes a polite announcement into the live region on mount (FR-022)', async () => {
    const { container } = renderWithLiveRegion(<GenerationLoading />)
    // Live region renders the announcement after a requestAnimationFrame tick.
    await waitFor(() => {
      const polite = container.querySelector('[role="status"][aria-live="polite"]')
      expect(polite?.textContent ?? '').toMatch(/generating your alter ego/i)
    })
  })
})

/**
 * 013 Image Generation Progress Indicator.
 *
 * <p>Each test passes an injected {@code now} (deterministic clock) and
 * an injected {@code rng} (deterministic RNG) so the tick chain is
 * fully reproducible. Vitest's {@code vi.useFakeTimers()} drives the
 * {@code setTimeout} chain itself; the injected {@code now} is the
 * source of "elapsed time" the rendered percent reads from.
 *
 * <p>Most end-state tests pass {@code countUpDurationMs={0}} so the
 * count-up animation snaps to target — the assertions are about
 * "where does the percent settle?", not "is the count-up smooth?".
 * Animation behaviour itself is covered in its own describe-block
 * with a non-zero duration.
 */
describe('GenerationLoading progress indicator (013)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  /** Build a simple "fake clock" the test can advance manually. */
  function makeClock(): { now: () => number; advance: (ms: number) => void } {
    let nowMs = 1_000_000 // arbitrary non-zero baseline
    return {
      now: () => nowMs,
      advance: (ms: number) => {
        nowMs += ms
      },
    }
  }

  /**
   * Advance both the fake clock and the Vitest timer queue in 2-second
   * steps (matching the rng=0 minimum tick delay). Each step keeps the
   * fake clock and the scheduled timer chain in lockstep so each tick
   * reads a freshly-advanced clock. Hard-coded 2 s because all callers
   * use {@code rng={() => 0}}; a future test using a different rng
   * would need its own helper.
   */
  async function advanceMinTickSteps(
    clock: { advance: (ms: number) => void },
    steps: number,
  ): Promise<void> {
    for (let i = 0; i < steps; i += 1) {
      await act(async () => {
        clock.advance(2_000)
        await vi.advanceTimersByTimeAsync(2_000)
      })
    }
  }

  test('renders an integer percent with a % sign inside the loading view (FR-1301, FR-1302)', () => {
    const clock = makeClock()
    const { container } = renderWithLiveRegion(
      <GenerationLoading now={clock.now} rng={() => 0.5} countUpDurationMs={0} />,
    )
    const percent = container.querySelector('.generation-loading__percent')
    expect(percent).not.toBeNull()
    // First render at elapsed=0 → 0%.
    expect(percent?.textContent).toBe('0%')
  })

  test('advances the percent over fake time at the 20 s budget (FR-1303, FR-1304, FR-1306)', async () => {
    const clock = makeClock()
    const { container } = renderWithLiveRegion(
      // rng=0 → every scheduled delay is the minimum 2000 ms.
      <GenerationLoading now={clock.now} rng={() => 0} countUpDurationMs={0} />,
    )
    const percentEl = () => container.querySelector('.generation-loading__percent')
    expect(percentEl()?.textContent).toBe('0%')

    // 10 s of virtual time → percentForElapsed(10_000) === 50 at 20 s budget.
    await advanceMinTickSteps(clock, 5)
    expect(percentEl()?.textContent).toBe('50%')
  })

  test('caps at 99% at the 20 s boundary and stays there beyond it (FR-1305 / SC-1302)', async () => {
    const clock = makeClock()
    const { container } = renderWithLiveRegion(
      <GenerationLoading now={clock.now} rng={() => 0} countUpDurationMs={0} />,
    )
    const percentEl = () => container.querySelector('.generation-loading__percent')

    // Walk to exactly 20 000 ms — the cap engages at the boundary.
    await advanceMinTickSteps(clock, 10)
    expect(percentEl()?.textContent).toBe('99%')

    // Walk to 90 000 ms total — still 99%, never 100%.
    await advanceMinTickSteps(clock, 35)
    expect(percentEl()?.textContent).toBe('99%')
    expect(container.textContent).not.toContain('100%')
  })

  test('a fresh mount restarts the percent from 0% (FR-1308)', async () => {
    const clock = makeClock()
    const first = renderWithLiveRegion(
      <GenerationLoading now={clock.now} rng={() => 0} countUpDurationMs={0} />,
    )
    // 10 s of virtual time → 50% at 20 s budget.
    await advanceMinTickSteps(clock, 5)
    expect(first.container.querySelector('.generation-loading__percent')?.textContent).toBe('50%')
    first.unmount()

    // Carry the clock forward (simulating real wall time passing while
    // a generation is not in flight) and remount. The new instance
    // must start at 0%, not pick up where the previous one left off.
    clock.advance(60_000)
    const second = renderWithLiveRegion(
      <GenerationLoading now={clock.now} rng={() => 0} countUpDurationMs={0} />,
    )
    expect(second.container.querySelector('.generation-loading__percent')?.textContent).toBe('0%')
  })

  test('unmounting mid-flight cancels the pending tick and never renders 100% afterwards (FR-1307)', async () => {
    const clock = makeClock()
    const { container, unmount } = renderWithLiveRegion(
      <GenerationLoading now={clock.now} rng={() => 0} countUpDurationMs={0} />,
    )
    // Mid-flight at ~6 s elapsed → 30% at the 20 s budget.
    await advanceMinTickSteps(clock, 3)
    expect(container.querySelector('.generation-loading__percent')?.textContent).toBe('30%')

    // Unmount BEFORE the percent reaches the cap. The host should be
    // empty afterwards, and advancing further fake time must not cause
    // any "100%" string to appear anywhere.
    unmount()
    await advanceMinTickSteps(clock, 60)
    expect(container.textContent ?? '').not.toContain('100%')
    expect(container.querySelector('.generation-loading__percent')).toBeNull()
  })

  test('the percent is decorative for ATs — aria-hidden and not inside any aria-live region (FR-1309)', () => {
    const clock = makeClock()
    const { container } = renderWithLiveRegion(
      <GenerationLoading now={clock.now} rng={() => 0.5} countUpDurationMs={0} />,
    )
    const percent = container.querySelector('.generation-loading__percent')
    expect(percent).not.toBeNull()
    expect(percent?.getAttribute('aria-hidden')).toBe('true')
    // No ancestor of the percent node carries aria-live.
    const ancestorWithAriaLive = percent?.closest('[aria-live]')
    expect(ancestorWithAriaLive).toBeNull()
  })

  test('does not regress the existing busy state, steps list, or polite announcement (SC-1306)', async () => {
    const clock = makeClock()
    const { container } = renderWithLiveRegion(
      <GenerationLoading now={clock.now} rng={() => 0.5} countUpDurationMs={0} />,
    )
    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull()
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
    // Live region defers via requestAnimationFrame; under fake timers we
    // have to drive a frame forward before the polite text materialises.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(16)
    })
    const polite = container.querySelector('[role="status"][aria-live="polite"]')
    expect(polite?.textContent ?? '').toMatch(/generating your alter ego/i)
  })
})

/**
 * 013 — count-up animation between consecutive percent updates.
 *
 * <p>Asserts that with a non-zero {@code countUpDurationMs} the
 * displayed value walks through intermediate integers between two
 * tick targets, and that {@code reduceMotion=true} suppresses the
 * animation entirely.
 */
describe('GenerationLoading count-up animation (013)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  function makeClock(): { now: () => number; advance: (ms: number) => void } {
    let nowMs = 1_000_000
    return {
      now: () => nowMs,
      advance: (ms: number) => {
        nowMs += ms
      },
    }
  }

  test('walks through intermediate integer values between two ticks', async () => {
    const clock = makeClock()
    const { container } = renderWithLiveRegion(
      <GenerationLoading
        now={clock.now}
        rng={() => 0}
        countUpDurationMs={400}
        reduceMotion={false}
      />,
    )
    const percentEl = () => container.querySelector('.generation-loading__percent')
    const seen = new Set<string>()

    // Drive the first tick: at +2 s elapsed, target jumps from 0 → 10.
    // Walk frame-by-frame (16 ms ≈ one rAF) so we sample the count-up.
    await act(async () => {
      clock.advance(2_000)
      await vi.advanceTimersByTimeAsync(2_000)
    })
    seen.add(percentEl()?.textContent ?? '')

    for (let i = 0; i < 30; i += 1) {
      await act(async () => {
        clock.advance(16)
        await vi.advanceTimersByTimeAsync(16)
      })
      seen.add(percentEl()?.textContent ?? '')
    }

    // Final landing value after the animation completes is 10%.
    expect(percentEl()?.textContent).toBe('10%')
    // At least one strictly-intermediate integer must have been
    // observed (e.g. 1%, 4%, 7% — depends on the easing curve).
    const intermediate = Array.from(seen).filter((v) => v !== '0%' && v !== '10%')
    expect(intermediate.length).toBeGreaterThan(0)
  })

  test('reduceMotion=true snaps to target with no intermediate frames', async () => {
    const clock = makeClock()
    const { container } = renderWithLiveRegion(
      <GenerationLoading
        now={clock.now}
        rng={() => 0}
        countUpDurationMs={400}
        reduceMotion={true}
      />,
    )
    const percentEl = () => container.querySelector('.generation-loading__percent')

    // Drive one tick to +2 s. Without animation, the value jumps
    // 0 → 10 in a single render — no rAF walk in between.
    await act(async () => {
      clock.advance(2_000)
      await vi.advanceTimersByTimeAsync(2_000)
    })
    expect(percentEl()?.textContent).toBe('10%')
  })
})
