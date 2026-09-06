import { useEffect } from 'react'
import { useLiveAnnouncer } from '../../../components/useLiveAnnouncer'
import { composePosterAlt } from '../lib/composePosterAlt'
import type { AlterEgoResponse } from '../types'

interface Props {
  result: AlterEgoResponse
  errorMessage: string | null
  /**
   * 017 FR-1714: the user's first name (sourced from `session.firstName`).
   * Combined with {@link role} into the poster image's `alt` text so
   * screen-reader users still learn the identity even though the
   * "{firstName} · {role}" line now lives in the rasterised bottom
   * region of the image.
   */
  firstName: string
  /**
   * 017 (refined 2026-05-08): the human-readable engineering role
   * (e.g. "Cloud Architect", "Backend Dev"), sourced from the
   * humanised {@code session.archetype}. Joined with {@link firstName}
   * for the `alt` text.
   */
  roleLabel: string
}

/**
 * Renders the generated poster + secondary character heading. 017 delta:
 * the user's first name and their role are baked into the poster image
 * bytes by the backend's {@code PosterTextOverlayService} as a single
 * "{firstName} · {role}" line (FR-1701), so the previously-shimmering
 * H2 hero name and the tagline paragraph are no longer rendered as
 * visible HTML next to the poster (FR-1708 / FR-1713). The
 * AI-generated {@code heroTitleLine2} remains as the only on-screen
 * HTML heading. The `<img alt>` (FR-1714) carries the same two pieces
 * of identity that live on the image so screen readers announce them.
 *
 * <p>When {@code errorMessage} is non-null (i.e. server returned
 * meta.outcome=fallback or the call threw), also renders a non-blocking
 * banner per FR-018 / FR-023 and announces it politely.
 */
export function PosterView({ result, errorMessage, firstName, roleLabel }: Props) {
  const { announce } = useLiveAnnouncer()

  useEffect(() => {
    if (errorMessage) {
      announce(errorMessage, 'polite')
    } else {
      announce('Your alter ego is ready.', 'polite')
    }
  }, [announce, errorMessage])

  const { character, poster } = result
  const altText = composePosterAlt(firstName, roleLabel, character.quote)

  return (
    <article className="poster-view">
      {errorMessage ? (
        <div role="alert" className="poster-view__fallback-banner">
          {errorMessage}
        </div>
      ) : null}
      <div className="poster-view__frame">
        <img
          src={poster.dataUrl}
          width={poster.widthPx}
          height={poster.heightPx}
          alt={altText}
          className="poster-view__image"
        />
      </div>
      <header className="poster-view__title">
        <p className="poster-view__title-line-2">{character.heroTitleLine2}</p>
      </header>
      <ul className="poster-view__superpowers">
        {character.superpowers.map((power, i) => (
          <li key={i}>{power}</li>
        ))}
      </ul>
    </article>
  )
}
