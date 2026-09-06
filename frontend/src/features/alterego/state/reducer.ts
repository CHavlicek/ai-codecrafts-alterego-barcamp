/**
 * AlterEgoSession state machine. See data-model.md § Entities for the
 * full shape + invariants.
 *
 * <p>002 delta: adds {@code activeTab} for the two-tab shell, adds an
 * optional {@code vibe}, drops {@code colour}. New actions
 * {@link ActiveTabChanged} / {@link VibeSelected}; removed
 * {@code ColourSelected}. The {@code StartOverRequested} branch resets
 * {@code activeTab} to {@code 'setup'} so Start-over always returns the
 * user to tab 1 (spec FR-102).
 *
 * <p>005 delta: {@link ActiveTabChanged} gains an optional
 * {@code reason} that carries animation intent. A monotonic
 * {@code generateAutoSwitchNonce} counter is incremented on every
 * {@code reason: 'generate'} dispatch — including no-op tab flips so
 * re-clicking Generate while the alter-ego tab is already active still
 * re-triggers the animation (spec 005 Acceptance 4). Why a counter and
 * not a boolean flag: {@code GenerateSubmitted} fires in the same React
 * commit as the auto-switch, and any boolean would be reset before
 * {@code TabsShell} observes it. A monotonic counter is dep-array-stable
 * across the whole commit.
 *
 * <p>Pure reducer + initial-state factory only. Context wiring lives in
 * {@code context.ts}/{@code AlterEgoProvider.tsx}; consumer hook in
 * {@code ../hooks/useAlterEgoSession.ts}.
 */
import type { SurpriseMePicks } from '../lib/randomSelections'
import type { AlterEgoResponse, Archetype, ArtStyle, PhotoMode, Universe } from '../types'

export type { SurpriseMePicks }

export type SessionPhase = 'idle' | 'picking' | 'generating' | 'succeeded' | 'failed_with_fallback'

export type ActiveTab = 'setup' | 'alter-ego'

/**
 * Why a tab change happened. 005 FR-401 / FR-403: a {@code 'generate'}
 * dispatch animates the incoming alter-ego panel; {@code 'manual'} (or
 * the field being omitted) keeps today's instant switch.
 */
export type ActiveTabChangeReason = 'manual' | 'generate'

export interface AlterEgoSession {
  /** Which top-level tab is currently visible. Default 'setup'. */
  activeTab: ActiveTab
  /**
   * Monotonic counter — incremented on every {@link ActiveTabChanged}
   * dispatched with {@code reason: 'generate'}, including no-op same-tab
   * dispatches (so Generate re-clicks on the alter-ego tab still animate).
   * Consumed by {@code useTabAnimationSignal}.
   */
  generateAutoSwitchNonce: number
  photoBlob: Blob | null
  /** `URL.createObjectURL(photoBlob)`; revoked on reset or replacement. */
  photoPreviewUrl: string | null
  archetype: Archetype | null
  universe: Universe | null
  /** Required — Generate is gated until set (006 FR-304). */
  artStyle: ArtStyle | null
  /**
   * 011: composition mode. Required, never null — defaults to
   * {@code 'single'}. Does NOT enter the {@code missingInputs} list
   * because it is always set (FR-1007).
   */
  photoMode: PhotoMode
  firstName: string
  /**
   * 022 (issue #50): user-typed free-form role string (≤ 100 chars).
   * When the trimmed value is non-empty it takes precedence over
   * {@code archetype} as the role-of-record (see FR-2207, FR-2211).
   * Initial value is the empty string — never null — so consumers can
   * treat the field uniformly with `value.trim()`.
   */
  customRole: string
  /**
   * 023 (issue #57): optional recipient email captured on the Setup
   * tab. Empty initial; never null. Mutated by {@link EmailChanged};
   * cleared by {@code StartOverRequested}. Survives Surprise Me and
   * Generate (FR-2308). Validation lives in `../validation/email.ts`
   * and is consumed by {@code emailValidity} / {@code missingInputs}
   * / {@code SendAsEmailButton}.
   */
  email: string
  phase: SessionPhase
  /** Non-null iff `phase === 'failed_with_fallback'`. */
  errorMessage: string | null
  /** Set when `phase` is `succeeded` or `failed_with_fallback`. */
  result: AlterEgoResponse | null
}

export type AlterEgoAction =
  | { type: 'ActiveTabChanged'; tab: ActiveTab; reason?: ActiveTabChangeReason }
  | { type: 'PhotoSelected'; photoBlob: Blob; photoPreviewUrl: string }
  | { type: 'PhotoCleared' }
  | { type: 'ArchetypeSelected'; archetype: Archetype }
  | { type: 'UniverseSelected'; universe: Universe }
  | { type: 'ArtStyleSelected'; artStyle: ArtStyle }
  | { type: 'PhotoModeSelected'; photoMode: PhotoMode }
  | { type: 'SurpriseMePicked'; picks: SurpriseMePicks }
  | { type: 'FirstNameChanged'; firstName: string }
  | { type: 'CustomRoleChanged'; customRole: string }
  | { type: 'EmailChanged'; email: string }
  | { type: 'GenerateSubmitted' }
  | { type: 'GenerateSucceeded'; result: AlterEgoResponse }
  | { type: 'GenerateFailedWithFallback'; result: AlterEgoResponse; errorMessage: string }
  | { type: 'StartOverRequested' }

export function initialAlterEgoSession(): AlterEgoSession {
  return {
    activeTab: 'setup',
    generateAutoSwitchNonce: 0,
    photoBlob: null,
    photoPreviewUrl: null,
    archetype: null,
    universe: null,
    artStyle: null,
    photoMode: 'single',
    firstName: '',
    customRole: '',
    email: '',
    phase: 'idle',
    errorMessage: null,
    result: null,
  }
}

export function alterEgoReducer(state: AlterEgoSession, action: AlterEgoAction): AlterEgoSession {
  switch (action.type) {
    case 'ActiveTabChanged': {
      const isGenerate = action.reason === 'generate'
      const tabChanged = state.activeTab !== action.tab
      if (!tabChanged && !isGenerate) return state // manual no-op — preserve ref equality
      return {
        ...state,
        activeTab: action.tab,
        generateAutoSwitchNonce: isGenerate
          ? state.generateAutoSwitchNonce + 1
          : state.generateAutoSwitchNonce,
      }
    }
    case 'PhotoSelected':
      return {
        ...state,
        photoBlob: action.photoBlob,
        photoPreviewUrl: action.photoPreviewUrl,
        phase: 'picking',
      }
    case 'PhotoCleared':
      return {
        ...state,
        photoBlob: null,
        photoPreviewUrl: null,
        phase: 'picking',
      }
    case 'ArchetypeSelected':
      return { ...state, archetype: action.archetype, phase: 'picking' }
    case 'UniverseSelected':
      return { ...state, universe: action.universe, phase: 'picking' }
    case 'ArtStyleSelected':
      // Art Style is required and single-select (no deselect toggle, mirrors
      // ArchetypeSelected). Re-clicking the same value is a no-op at the
      // SelectionGrid layer; here we just overwrite.
      return { ...state, artStyle: action.artStyle, phase: 'picking' }
    case 'PhotoModeSelected':
      // 011: photoMode is required and single-select (no deselect). The
      // SelectionGrid primitive blocks re-click on the already-selected
      // option, so this branch is an unconditional overwrite.
      return { ...state, photoMode: action.photoMode, phase: 'picking' }
    case 'SurpriseMePicked':
      // 009 FR-905 / FR-907 — commit the visible category picks in one
      // transition. Does NOT touch photoBlob, firstName, activeTab,
      // generateAutoSwitchNonce, errorMessage, or result — the
      // auto-tab-switch + generating transition fire in the subsequent
      // submit() onMutate so the ordering contract is
      // SurpriseMePicked → ActiveTabChanged → GenerateSubmitted.
      // 020 delta: Pose and Vibe are no longer in the picks payload — the
      // server rolls them per request (closes issue #51).
      // 028 (FR-2814): Surprise Me MUST NOT clear customRole. This
      // supersedes 022 FR-2209 — partial Surprise Me preserves explicit
      // picks, and the typed Custom Role string is an explicit Role
      // channel (FR-2803). The merge that decides which category values
      // to commit lives in `useGenerateAlterEgo.surprise()` upstream;
      // here we just write what the hook computed. The 022 prefab-vs-
      // custom invariant continues to hold via the CustomRoleChanged
      // reducer branch, not this one.
      return {
        ...state,
        archetype: action.picks.archetype,
        universe: action.picks.universe,
        artStyle: action.picks.artStyle,
        phase: 'picking',
      }
    case 'FirstNameChanged':
      return { ...state, firstName: action.firstName, phase: 'picking' }
    case 'CustomRoleChanged': {
      // 022 (issue #50): the precedence rule lives here. When the trimmed
      // payload is non-empty the input claims precedence — silently clear
      // any prefab `archetype` selection (FR-2207). When trimmed-empty
      // (whitespace-only or "") the prefab selection is preserved so the
      // user can type a space then a real character without thrashing.
      const trimmedNonEmpty = action.customRole.trim().length > 0
      return {
        ...state,
        customRole: action.customRole,
        archetype: trimmedNonEmpty ? null : state.archetype,
        phase: 'picking',
      }
    }
    case 'EmailChanged':
      // 023 (FR-2308): email is metadata, NOT a generation-flow transition.
      // Do not touch `phase` — typing in the field while a generation is in
      // flight (impossible today thanks to tab gating, but defensive) must
      // not yank the session back to 'picking'. Storing the raw value
      // preserves the user's whitespace; the validator trims at read time.
      return { ...state, email: action.email }
    case 'GenerateSubmitted':
      // Caller is responsible for gating on isReadyToGenerate; this just
      // transitions the phase. From any non-generating phase → generating.
      return state.phase === 'generating'
        ? state
        : {
            ...state,
            phase: 'generating',
            errorMessage: null,
            result: null,
          }
    case 'GenerateSucceeded':
      return {
        ...state,
        phase: 'succeeded',
        result: action.result,
        errorMessage: null,
      }
    case 'GenerateFailedWithFallback':
      return {
        ...state,
        phase: 'failed_with_fallback',
        result: action.result,
        errorMessage: action.errorMessage,
      }
    case 'StartOverRequested':
      // Always reset, regardless of current phase. The caller is
      // responsible for revoking the prior photoPreviewUrl so blob URLs
      // don't leak (see StartOverButton). The activeTab reset is baked in
      // here so Start-over → tab 1 is a single state transition (spec FR-102,
      // research.md §R10). generateAutoSwitchNonce resets to 0 via
      // initialAlterEgoSession() so Start-over never animates (005 FR-404).
      return initialAlterEgoSession()
  }
}
