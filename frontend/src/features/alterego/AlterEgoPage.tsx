import { useEffect } from 'react'
import { AlterEgoProvider } from './state/AlterEgoProvider'
import { useAlterEgoSession } from './hooks/useAlterEgoSession'
import { useGenerateAlterEgo } from './hooks/useGenerateAlterEgo'
import { AlterEgoPanel } from './components/AlterEgoPanel'
import { BrandHeader } from './components/BrandHeader'
import { SetupLayout } from './components/SetupLayout'
import { TabsShell } from './components/TabsShell'
import { tabDisabled } from './state/selectors'
import { validateFirstName } from './validation/firstName'
import type { Selections } from './types'

/**
 * Composition root for the AI Alter Ego generator feature (002 final).
 *
 * <p>Renders a two-tab shell whose panels are both always mounted:
 *   <ul>
 *     <li>Tab 1 "setup": {@link SetupLayout} — photo + 4 pickers +
 *         name + Generate.</li>
 *     <li>Tab 2 "Your Alter Ego": {@link AlterEgoPanel} — empty /
 *         loading / poster states (spec FR-107).</li>
 *   </ul>
 *
 * <p>Pressing Generate dispatches {@code ActiveTabChanged → 'alter-ego'}
 * from {@link useGenerateAlterEgo}'s {@code onMutate} (FR-108) so the
 * tab flips before the network request starts. Start-over resets the
 * active tab back to 'setup' via the reducer (FR-102, research.md §R10).
 */
export function AlterEgoPage() {
  return (
    <AlterEgoProvider>
      <AlterEgoPageContent />
    </AlterEgoProvider>
  )
}

function AlterEgoPageContent() {
  const { state, dispatch } = useAlterEgoSession()
  const { submit, surprise, isPending } = useGenerateAlterEgo()

  // 007 FR-503..FR-505: per-tab disabled flag is a pure function of the
  // current phase — see specs/007-tab-access-gating/data-model.md.
  const setupDisabled = tabDisabled('setup', state.phase)
  const alterEgoDisabled = tabDisabled('alter-ego', state.phase)

  // 007 FR-507 (defence-in-depth): the active tab must always be enabled.
  // The happy path already satisfies this because `useGenerateAlterEgo.onMutate`
  // dispatches `ActiveTabChanged → 'alter-ego'` BEFORE `GenerateSubmitted`
  // (phase → generating). This effect is the safety net for any future
  // code path that transitions phase without first switching tabs.
  // reason: 'manual' keeps the 005 entrance animation OFF this path.
  useEffect(() => {
    if (state.activeTab === 'setup' && setupDisabled) {
      dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'manual' })
    } else if (state.activeTab === 'alter-ego' && alterEgoDisabled) {
      dispatch({ type: 'ActiveTabChanged', tab: 'setup', reason: 'manual' })
    }
  }, [state.activeTab, setupDisabled, alterEgoDisabled, dispatch])

  const handleSubmit = () => {
    // 020 — pose and vibe are no longer user-controllable; the server
    // rolls them per request. 022 — the archetype gate is satisfied by
    // EITHER a prefab selection OR a non-blank custom-role string
    // (FR-2210). Both empty keeps Generate disabled.
    const trimmedCustomRole = state.customRole.trim()
    if (
      !state.photoBlob ||
      (!state.archetype && trimmedCustomRole === '') ||
      !state.universe ||
      !state.artStyle
    ) {
      return
    }
    // 011 FR-1102 — defence-in-depth: the Generate button is already gated
    // by `isReadyToGenerate` (which routes through the validator), but the
    // handler also refuses an invalid firstName so a stray programmatic
    // click can never reach the backend with an invalid value.
    if (!validateFirstName(state.firstName).ok) {
      return
    }
    const selections: Selections = {
      archetype: state.archetype,
      universe: state.universe,
      artStyle: state.artStyle,
      photoMode: state.photoMode,
      firstName: state.firstName.trim(),
      // 022 (FR-2211): the trimmed custom-role string takes precedence as
      // the role-of-record; the serialiser in alterEgoClient.ts omits the
      // field when blank so the wire shape stays minimal for old backends.
      ...(trimmedCustomRole.length > 0 ? { customRole: trimmedCustomRole } : {}),
    }
    submit({ photoBlob: state.photoBlob, selections })
  }

  const handleSurprise = () => {
    // 009 FR-902 + 011 FR-1102 — defence-in-depth: the button gates on
    // `isReadyToSurprise` already.
    if (!state.photoBlob || !validateFirstName(state.firstName).ok) {
      return
    }
    // 011 FR-1012: Surprise Me reuses the current photoMode (does NOT
    // randomise it — the mode is a photo-composition choice, not a theme).
    surprise({
      photoBlob: state.photoBlob,
      firstName: state.firstName,
      photoMode: state.photoMode,
    })
  }

  const handleStartOver = () => {
    if (state.photoPreviewUrl) {
      URL.revokeObjectURL(state.photoPreviewUrl)
    }
    dispatch({ type: 'StartOverRequested' })
  }

  return (
    <main className="alter-ego-page">
      <BrandHeader />
      <h1 className="alter-ego-page__title">AI @ VERBUND 2026</h1>
      <p className="alter-ego-page__subtitle">
        Discover your AI alter ego — powering the energy transformation.
      </p>
      <TabsShell
        tabs={[
          {
            id: 'setup',
            label: '1 Setup',
            disabled: setupDisabled,
            panel: (
              <SetupLayout
                session={state}
                dispatch={dispatch}
                isSubmitting={isPending}
                onSubmit={handleSubmit}
                onSurprise={handleSurprise}
              />
            ),
          },
          {
            id: 'alter-ego',
            label: '2 Your Alter Ego',
            disabled: alterEgoDisabled,
            panel: (
              <AlterEgoPanel
                session={state}
                isGenerating={isPending}
                onStartOver={handleStartOver}
              />
            ),
          },
        ]}
      />
    </main>
  )
}
