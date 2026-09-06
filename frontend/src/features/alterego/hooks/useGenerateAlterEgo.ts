/**
 * TanStack Query mutation that wires {@link generateAlterEgo} into the
 * AlterEgoSession reducer. Owns the dispatch lifecycle:
 *  - on submit       → ActiveTabChanged → 'alter-ego' (reason: 'generate')
 *                      + GenerateSubmitted
 *  - on 200 success  → GenerateSucceeded (or GenerateFailedWithFallback
 *                      if the server returned `meta.outcome === 'fallback'`)
 *  - on thrown error → GenerateFailedWithFallback with FE-side fallback
 *
 * <p>002 delta: the mutation's {@code onMutate} is where the auto-switch
 * to the "Your Alter Ego" tab lives. Dispatching here (before the fetcher
 * runs) guarantees the tab flips immediately on click — spec FR-108,
 * research.md §R2.
 *
 * <p>005 delta: the {@code ActiveTabChanged} dispatch carries
 * {@code reason: 'generate'} so the reducer latches
 * {@code lastTabChangeReason} and {@code TabsShell} paints the one-shot
 * entrance animation on the incoming alter-ego panel (005 FR-401).
 */
import { useMutation } from '@tanstack/react-query'
import { useCallback } from 'react'
import { FALLBACK_NOTICE_COPY } from '../constants'
import { fallbackPoster } from '../fallback/fallbackPoster'
import { mergeSurpriseWithExplicit } from '../lib/mergeSurpriseWithExplicit'
import { randomSelections } from '../lib/randomSelections'
import { generateAlterEgo } from '../services/alterEgoClient'
import type { AlterEgoResponse, PhotoMode, Selections } from '../types'
import { useAlterEgoSession } from './useAlterEgoSession'

interface SubmitArgs {
  photoBlob: Blob
  selections: Selections
}

/** 009 Surprise Me entry-point arguments. Selections are filled by the
 *  randomizer; the caller only supplies the user-provided inputs.
 *
 *  <p>011 delta: {@code photoMode} is threaded through here so the
 *  randomiser doesn't accidentally drop or randomise it — mode is a
 *  composition choice tied to the photo, not a theme pick (FR-1012). */
interface SurpriseArgs {
  photoBlob: Blob
  firstName: string
  photoMode: PhotoMode
}

function newCorrelationId(): string {
  return crypto.randomUUID()
}

export function useGenerateAlterEgo() {
  const { state, dispatch } = useAlterEgoSession()

  const mutation = useMutation<AlterEgoResponse, Error, SubmitArgs>({
    mutationFn: async ({ photoBlob, selections }) => {
      const correlationId = newCorrelationId()
      return generateAlterEgo({ photoBlob, selections, correlationId })
    },
    onMutate: () => {
      // Order matters: auto-switch the tab first so the loading indicator
      // appears in the Your Alter Ego panel (FR-108), then transition the
      // session phase so the panel renders <GenerationLoading />.
      // 005 FR-401: reason: 'generate' is the animation signal TabsShell
      // reads to paint the one-shot entrance animation on the incoming
      // alter-ego panel. GenerateSubmitted below resets the signal to null
      // immediately after, so the reason is strictly one-shot.
      dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
      dispatch({ type: 'GenerateSubmitted' })
    },
    onSuccess: (result) => {
      // 003 FR-214 / FR-218: `real` means the Gemini provider produced the
      // image; any other outcome (including unknown wire values from a
      // forward-compatible server) is treated as fallback for graceful
      // degradation. `result.meta.reason` is intentionally NOT surfaced to
      // the user — the banner uses the single FALLBACK_NOTICE_COPY string.
      if (result.meta.outcome === 'real') {
        dispatch({ type: 'GenerateSucceeded', result })
      } else {
        dispatch({
          type: 'GenerateFailedWithFallback',
          result,
          errorMessage: FALLBACK_NOTICE_COPY,
        })
      }
    },
    onError: (_error, vars) => {
      // resilientFetch's fallback should normally cover this, but if e.g.
      // the request is aborted mid-flight, generateAlterEgo can throw.
      // The user-visible message is ALWAYS the generic FR-214 copy — we
      // do not surface error.message to the UI.
      const correlationId = newCorrelationId()
      const result = fallbackPoster(vars.selections.firstName, correlationId)
      dispatch({
        type: 'GenerateFailedWithFallback',
        result,
        errorMessage: FALLBACK_NOTICE_COPY,
      })
    },
  })

  const submit = useCallback((args: SubmitArgs) => mutation.mutate(args), [mutation])

  /**
   * 009 — Surprise Me entry point. Draws one value per category at
   * random, commits them to the session via {@code SurpriseMePicked}
   * so the Setup form reflects the picks (FR-907), then fires the
   * same mutation path {@link submit} uses. Ordering matters: the
   * picks land BEFORE the onMutate handler dispatches
   * {@code ActiveTabChanged}/{@code GenerateSubmitted}, so a momentary
   * partially-seeded state is never visible to React consumers.
   *
   * <p>028 — partial-surprise-me. The randomizer's output is no longer
   * the final pick set; instead it is composed through
   * {@link mergeSurpriseWithExplicit} so any category whose session
   * slot is already non-empty (user-picked OR previously rolled —
   * provenance is not tracked) passes through unchanged. Only empty
   * slots inherit the random draw. A non-empty trimmed
   * {@code customRole} also counts as an explicit Role choice; the
   * serialiser drops the `archetype` wire field when `customRole` is
   * present (022). See spec 028 FR-2801..FR-2808 / FR-2814.
   */
  const surprise = useCallback(
    (args: SurpriseArgs) => {
      // 020 delta: picks now contain only archetype / universe / artStyle.
      // The server rolls Pose and Vibe for every Generate request — for
      // Surprise Me, identical wiring (closes issue #51 / spec FR-2031).
      // 028 delta: the full roll is the *fallback* — the merge below
      // overrides each slot with the session's value when the user has
      // already chosen it.
      const fullRoll = randomSelections()
      const picks = mergeSurpriseWithExplicit(state, fullRoll)
      dispatch({ type: 'SurpriseMePicked', picks })
      const trimmedCustomRole = state.customRole.trim()
      const hasCustomRole = trimmedCustomRole.length > 0
      const selections: Selections = {
        // 022 (FR-2211) + 028 (FR-2812): when Custom Role is the explicit
        // Role channel, the outbound `archetype` is null — matching the
        // Generate path's handleSubmit (AlterEgoPage.tsx) byte-for-byte.
        // The serialiser drops the field entirely when null.
        archetype: hasCustomRole ? null : picks.archetype,
        universe: picks.universe,
        artStyle: picks.artStyle,
        photoMode: args.photoMode,
        firstName: args.firstName.trim(),
        // 022 (FR-2211) + 028 (FR-2803): the trimmed Custom Role takes
        // precedence as the role-of-record; the serialiser in
        // alterEgoClient.ts omits the field when blank.
        ...(hasCustomRole ? { customRole: trimmedCustomRole } : {}),
      }
      mutation.mutate({ photoBlob: args.photoBlob, selections })
    },
    [state, dispatch, mutation],
  )

  return { submit, surprise, isPending: mutation.isPending }
}
