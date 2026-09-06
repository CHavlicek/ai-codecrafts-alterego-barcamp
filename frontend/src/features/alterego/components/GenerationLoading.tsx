import { useEffect, useRef, useState } from 'react'
import { useLiveAnnouncer } from '../../../components/useLiveAnnouncer'
import { formatPercent, nextDelayMs, percentForElapsed, type Rng } from '../lib/progressTicker'

interface GenerationLoadingProps {
  /** Injectable monotonic clock (ms). Defaults to {@link performance.now}. */
  now?: () => number
  /** Injectable RNG returning a number in [0, 1). Defaults to {@link Math.random}. */
  rng?: Rng
  /**
   * Duration of the count-up animation between two consecutive percent
   * values, in ms. Defaults to 400. Pass 0 to snap directly (used by
   * unit tests that assert end-state only).
   */
  countUpDurationMs?: number
  /**
   * Override the {@code prefers-reduced-motion} detection. When true,
   * the count-up animation is skipped — values snap to target. When
   * undefined (production default), the OS / browser preference is read
   * via {@code matchMedia}.
   */
  reduceMotion?: boolean
}

/**
 * Shown while the generation mutation is in-flight. Announces the
 * "generating" state via the singleton live region on mount so AT users
 * get the same state transition sighted users see (FR-022).
 *
 * <p>013 — Renders a live integer percent inside the existing pulsating
 * circle. The percent is a pure function of elapsed wall-clock time
 * since this component mounted, capped at 99% so a "100%" reading can
 * never be displayed (spec FR-1305). The component's own mount/unmount
 * lifecycle is exactly the loading-view lifetime: when the generation
 * resolves, this component unmounts and the pending tick is cancelled
 * — no terminal "100%" frame is ever rendered (FR-1307).
 *
 * <p>Between ticks the displayed value count-up-animates from the
 * previous integer toward the next via a {@code requestAnimationFrame}
 * loop, eased over {@code countUpDurationMs}. The animation is a
 * cosmetic continuity device only: the source-of-truth for "what
 * percent should the user be seeing" is still
 * {@code percentForElapsed(elapsedMs)} — the animation simply walks
 * between two such values. It is suppressed under
 * {@code prefers-reduced-motion: reduce}.
 */
export function GenerationLoading({
  now = () => performance.now(),
  rng = Math.random,
  countUpDurationMs = 400,
  reduceMotion,
}: GenerationLoadingProps = {}) {
  const { announce } = useLiveAnnouncer()
  // Lazy useState initialiser — runs exactly once per mount, even
  // under React 19 Strict Mode's intentional double-render in dev.
  const [startedAtMs] = useState(() => now())
  const [elapsedMs, setElapsedMs] = useState(0)
  const [displayedPercent, setDisplayedPercent] = useState(0)
  // Ref mirrors the rendered value so the count-up effect can capture
  // the current displayed value as its "from" without re-running every
  // animation frame (which it would if it depended on the state).
  const displayedPercentRef = useRef(0)
  const writeDisplayed = (value: number) => {
    displayedPercentRef.current = value
    setDisplayedPercent(value)
  }

  useEffect(() => {
    announce('Generating your alter ego…', 'polite')
  }, [announce])

  useEffect(() => {
    let handle: ReturnType<typeof setTimeout> | null = null

    const tick = () => {
      setElapsedMs(now() - startedAtMs)
      handle = setTimeout(tick, nextDelayMs(rng))
    }

    handle = setTimeout(tick, nextDelayMs(rng))
    return () => {
      if (handle !== null) clearTimeout(handle)
    }
    // now/rng/startedAtMs are captured once on mount; a re-render with
    // a new now/rng prop must not reset the timer chain.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const target = percentForElapsed(elapsedMs)
  const reducedMotionPreferred =
    reduceMotion ??
    (typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  const animated = !reducedMotionPreferred && countUpDurationMs > 0

  // Count-up animation — ease toward the new target whenever elapsed
  // produces a new percent. The effect only runs in animated mode; in
  // snap mode the rendered value is derived from `target` directly,
  // so no setState happens inside the effect at all.
  useEffect(() => {
    if (!animated) return
    const fromValue = displayedPercentRef.current
    if (fromValue === target) return

    const startTimeMs = now()
    let cancelled = false
    let rafId: number | null = null

    const step = () => {
      if (cancelled) return
      const elapsed = now() - startTimeMs
      const progress = Math.min(1, elapsed / countUpDurationMs)
      // ease-out cubic — fast then settle, matches the perceptual
      // feel of "the number is catching up" without overshoot.
      const eased = 1 - Math.pow(1 - progress, 3)
      const current = Math.floor(fromValue + (target - fromValue) * eased)
      writeDisplayed(current)
      if (progress < 1) {
        rafId = requestAnimationFrame(step)
      } else {
        writeDisplayed(target)
      }
    }

    rafId = requestAnimationFrame(step)
    return () => {
      cancelled = true
      if (rafId !== null) cancelAnimationFrame(rafId)
    }
    // now/countUpDurationMs are captured once on mount (see earlier effect).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target, animated])

  const percentText = formatPercent(animated ? displayedPercent : target)

  return (
    <div className="generation-loading" aria-busy="true">
      <p>
        <span className="generation-loading__percent" aria-hidden="true">
          {percentText}
        </span>
        Generating your alter ego…
      </p>
      <ol className="generation-loading__steps">
        <li>Analysing your photo</li>
        <li>Shaping the character</li>
        <li>Rendering the poster</li>
      </ol>
    </div>
  )
}
