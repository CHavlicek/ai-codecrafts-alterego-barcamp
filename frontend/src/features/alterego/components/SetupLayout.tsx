import type { AlterEgoSession } from '../state/reducer'
import { ArchetypeGrid } from './ArchetypeGrid'
import { ArtStyleGrid } from './ArtStyleGrid'
import { CustomRoleInput } from './CustomRoleInput'
import { CustomUniverseInput } from './CustomUniverseInput'
import { EmailInput } from './EmailInput'
import { FirstNameInput } from './FirstNameInput'
import { GenerateButton } from './GenerateButton'
import { PhotoIntake } from './PhotoIntake'
import { PhotoModeSwitch } from './PhotoModeSwitch'
import { SurpriseMeButton } from './SurpriseMeButton'
import { UniverseGrid } from './UniverseGrid'
import type { AlterEgoAction } from '../state/reducer'

interface Props {
  session: AlterEgoSession
  dispatch: (action: AlterEgoAction) => void
  isSubmitting: boolean
  onSubmit: () => void
  /** 009 — fires the Surprise Me flow. Wired to
   *  {@code useGenerateAlterEgo().surprise} in {@code AlterEgoPage}. */
  onSurprise: () => void
}

/**
 * Two-column Setup tab layout (spec FR-110 / FR-115).
 *
 * <p>Desktop (≥ 1024 px): left column is <strong>YOUR PHOTO</strong>,
 * right column is <strong>ROLE, UNIVERSE &amp; KEYS</strong> which hosts
 * three numbered sub-groups (Engineer role / Universe / Art style),
 * the Name input, and the Generate button in that order.
 *
 * <p>020 delta: Pose and Vibe were removed from the Setup UI (closes
 * issue #51 / specs/020-hide-vibe-pose). The server rolls those values
 * per request; the remaining three theme groups are renumbered 1..3.
 *
 * <p>Narrow viewports (&lt; 1024 px): columns collapse to a single stack
 * with the photo column first. The CSS Grid in tokens consumes the
 * {@code .setup-layout} class to do the flip via a media query.
 */
export function SetupLayout({ session, dispatch, isSubmitting, onSubmit, onSurprise }: Props) {
  return (
    <form
      className="setup-layout"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit()
      }}
    >
      <section className="setup-layout__photo" aria-labelledby="setup-photo-heading">
        <header className="setup-layout__section-header">
          <h2 id="setup-photo-heading" className="setup-layout__section-title">
            YOUR PHOTO
          </h2>
          <p className="setup-layout__section-subtitle">Real face as a direct reference</p>
        </header>
        <PhotoIntake
          photoPreviewUrl={session.photoPreviewUrl}
          onPhotoSelected={(photoBlob, photoPreviewUrl) =>
            dispatch({ type: 'PhotoSelected', photoBlob, photoPreviewUrl })
          }
          onPhotoCleared={() => dispatch({ type: 'PhotoCleared' })}
        />
        {/* 011 — Composition mode lives in the photo column because it is
            metadata about the captured photo, not a theme pick. The toggle
            sits below the camera circle as its own subsection so the visual
            split between "what's in the photo" and "what to make of it" stays
            crisp. */}
        <div className="setup-layout__photo-mode">
          <PhotoModeSwitch
            value={session.photoMode}
            onChange={(photoMode) => dispatch({ type: 'PhotoModeSelected', photoMode })}
          />
        </div>
      </section>

      <section className="setup-layout__selections" aria-labelledby="setup-selections-heading">
        <header className="setup-layout__section-header">
          <h2 id="setup-selections-heading" className="setup-layout__section-title">
            ROLE, UNIVERSE &amp; KEYS
          </h2>
        </header>

        <div className="setup-layout__numbered-group" data-step="1">
          {/* 022 (issue #50): when the custom-role input has a non-blank
              trimmed value it takes precedence — the prefab grid renders
              blurred and non-interactive (FR-2205). The CustomRoleInput
              sits immediately below the grid so the precedence rule is
              visually obvious. */}
          <ArchetypeGrid
            value={session.archetype}
            onChange={(archetype) => dispatch({ type: 'ArchetypeSelected', archetype })}
            disabled={session.customRole.trim().length > 0}
          />
          <CustomRoleInput
            value={session.customRole}
            onChange={(customRole) => dispatch({ type: 'CustomRoleChanged', customRole })}
            disabled={isSubmitting}
          />
        </div>

        <div className="setup-layout__numbered-group" data-step="2">
          {/* 029 (verbund-rebrand): mirrors the custom-Role precedence — when
              the custom-universe input has a non-blank trimmed value the
              prefab grid renders blurred and non-interactive. */}
          <UniverseGrid
            value={session.universe}
            onChange={(universe) => dispatch({ type: 'UniverseSelected', universe })}
            disabled={session.customUniverse.trim().length > 0}
          />
          <CustomUniverseInput
            value={session.customUniverse}
            onChange={(customUniverse) =>
              dispatch({ type: 'CustomUniverseChanged', customUniverse })
            }
            disabled={isSubmitting}
          />
        </div>

        <div className="setup-layout__numbered-group" data-step="3">
          <ArtStyleGrid
            value={session.artStyle}
            onChange={(artStyle) => dispatch({ type: 'ArtStyleSelected', artStyle })}
          />
        </div>

        {/* 023 (issue #57): optional Email input sits between the last
            numbered category group and the first-name input (FR-2301).
            Not wrapped in a `.setup-layout__numbered-group` because it
            is metadata at the same hierarchical level as first-name,
            not a category pick. */}
        <EmailInput
          value={session.email}
          onChange={(email) => dispatch({ type: 'EmailChanged', email })}
          disabled={isSubmitting}
        />

        <FirstNameInput
          value={session.firstName}
          onChange={(firstName) => dispatch({ type: 'FirstNameChanged', firstName })}
          mode={session.photoMode}
        />

        <div className="setup-layout__actions">
          <GenerateButton session={session} isSubmitting={isSubmitting} onSubmit={onSubmit} />
          <SurpriseMeButton session={session} isSubmitting={isSubmitting} onSurprise={onSurprise} />
        </div>
      </section>
    </form>
  )
}
