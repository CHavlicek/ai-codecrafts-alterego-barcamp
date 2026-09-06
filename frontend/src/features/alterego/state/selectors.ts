/**
 * Pure selectors over {@link AlterEgoSession}. Used by GenerateButton
 * (FR-009 gating + FR-010 missing-input hint) and a few other places
 * that need to ask "are we ready yet?".
 *
 * <p>002 delta: {@code colour} dropped from the required-input set; vibe
 * is NOT added because it is explicitly optional (spec FR-118, FR-122).
 *
 * <p>007 delta: {@link tabDisabled} — pure phase-to-disabled predicate
 * consumed by {@code AlterEgoPage} when it hands {@code TabDescriptor.disabled}
 * to {@code TabsShell}. No reducer change; truth table lives in
 * specs/007-tab-access-gating/data-model.md.
 *
 * <p>006 delta: {@code artStyle} added to the required-input set (spec
 * FR-304 — Generate MUST stay disabled until the user picks a style).
 *
 * <p>011 delta: the firstName check now goes through
 * {@code validateFirstName} so length / injection rules also gate Generate.
 * The validator already reports `empty` for blank/whitespace-only input,
 * so the previous {@code state.firstName.trim().length < 1} probe is
 * subsumed.
 */
import type { ActiveTab, AlterEgoSession, SessionPhase } from './reducer'
import { validateFirstName } from '../validation/firstName'
import { validateEmail } from '../validation/email'

/**
 * Names of the inputs the user MUST provide before Generate is enabled.
 *
 * <p>020 delta: {@code 'pose'} is no longer here — Pose has been removed
 * from the Setup UI (the server rolls it per request). The remaining set
 * mirrors the visible Setup-tab categories. Closes issue #51.
 */
export type RequiredInput = 'photo' | 'archetype' | 'universe' | 'artStyle' | 'email' | 'firstName'

/**
 * 022 (issue #50): trimmed custom-role string or `""` when blank /
 * whitespace-only. Single canonical helper consumed by the gating
 * selector below and by the API client when serialising the role of
 * record.
 */
export function customRoleOfRecord(state: AlterEgoSession): string {
  return state.customRole.trim()
}

/**
 * 029 (verbund-rebrand): trimmed custom-universe string or `""` when blank.
 * Mirrors {@link customRoleOfRecord}; consumed by the gating selector below.
 */
export function customUniverseOfRecord(state: AlterEgoSession): string {
  return state.customUniverse.trim()
}

/**
 * 023 (FR-2303 / FR-2304) — the captured email is optional but
 * format-checked when non-blank. `'valid'` covers BOTH blank and
 * well-formed; `'invalid'` only fires when the user has typed
 * something that fails the pragmatic validator. Drives Generate /
 * Surprise Me gating (via {@link missingInputs}) and the Send-As-Email
 * button's disabled state.
 */
export function emailValidity(state: AlterEgoSession): 'valid' | 'invalid' {
  return validateEmail(state.email).ok ? 'valid' : 'invalid'
}

/**
 * 025 (issue #60) — send-ready predicate. True iff the captured email is
 * BOTH well-formed AND non-blank.
 *
 * <p>Differs from {@link emailValidity}, which admits blank as valid
 * because Setup-tab Generate gating treats an optional empty field as a
 * legitimate state. This selector is the *send-ready* gate — the one
 * that needs the trimmed value to be non-empty. It drives:
 *
 *   • the Alter Ego-tab inline-email fallback's visibility (FR-2501 /
 *     FR-2502): the field is rendered iff !isSendableEmail.
 *   • the {@code SendAsEmailButton}'s enabled state (FR-2505):
 *     enabled iff isSendableEmail AND no send is in flight.
 *
 * <p>Invariant: isSendableEmail(s) ⇒ emailValidity(s) === 'valid'.
 */
export function isSendableEmail(state: AlterEgoSession): boolean {
  const v = validateEmail(state.email)
  return v.ok && v.trimmed.length > 0
}

export function missingInputs(state: AlterEgoSession): RequiredInput[] {
  const missing: RequiredInput[] = []
  if (!state.photoBlob) missing.push('photo')
  // 022 (FR-2210): the archetype gate is satisfied by EITHER a prefab
  // selection OR a non-blank custom-role string.
  if (!state.archetype && customRoleOfRecord(state) === '') missing.push('archetype')
  // 029: the universe gate is satisfied by EITHER a prefab selection OR a
  // non-blank custom-universe string (mirrors the archetype gate).
  if (!state.universe && customUniverseOfRecord(state) === '') missing.push('universe')
  if (!state.artStyle) missing.push('artStyle')
  // 023 (FR-2304): a malformed non-blank email blocks Generate / Surprise.
  // Insert at the position that mirrors the field's visual position on
  // Setup — between artStyle and firstName.
  if (emailValidity(state) === 'invalid') missing.push('email')
  if (!validateFirstName(state.firstName).ok) missing.push('firstName')
  return missing
}

export function isReadyToGenerate(state: AlterEgoSession): boolean {
  return missingInputs(state).length === 0
}

/**
 * 009 Surprise Me required-input set — a strict subset of
 * {@link RequiredInput}. The Surprise Me button skips the five
 * category fields (they are filled by the randomizer) and requires
 * only a photo and a non-empty name.
 */
export type SurpriseRequiredInput = Extract<RequiredInput, 'photo' | 'firstName'>

/**
 * Missing-input list for the Surprise Me button's disabled-state hint
 * (009 FR-903). Canonical ordering: photo first, firstName last —
 * mirrors {@link missingInputs} so downstream formatters behave the
 * same whether the user is being hinted for Generate or Surprise Me.
 */
export function missingInputsForSurprise(state: AlterEgoSession): SurpriseRequiredInput[] {
  const missing: SurpriseRequiredInput[] = []
  if (!state.photoBlob) missing.push('photo')
  if (!validateFirstName(state.firstName).ok) missing.push('firstName')
  return missing
}

/**
 * 009 FR-902 — Surprise Me is enabled iff (a) a photo is present,
 * (b) the trimmed first name is non-empty, and (c) the session is
 * not currently in the {@code 'generating'} phase. Category
 * selections are explicitly NOT required — the randomizer fills them.
 *
 * <p>Invariant (tested in selectors.test.ts): for every
 * {@link AlterEgoSession s},
 * {@code isReadyToGenerate(s) ⇒ isReadyToSurprise(s)}.
 */
export function isReadyToSurprise(state: AlterEgoSession): boolean {
  return (
    !!state.photoBlob &&
    validateFirstName(state.firstName).ok &&
    // 023 (FR-2304) — a malformed email blocks Surprise Me too.
    emailValidity(state) === 'valid' &&
    state.phase !== 'generating'
  )
}

/**
 * 007 FR-503..FR-505: which top-level tab should be rendered
 * non-interactive right now, given only the current session phase.
 *
 * <ul>
 *   <li>Before the first generation resolves (phase ∈ idle|picking|generating)
 *       the alter-ego tab has no content to show and is disabled.</li>
 *   <li>While a generation is in flight (phase === generating) the setup
 *       tab is disabled so the user cannot mutate inputs mid-request.</li>
 *   <li>Once any generation resolves (phase ∈ succeeded|failed_with_fallback)
 *       both tabs are enabled until {@code StartOverRequested} resets the
 *       session.</li>
 * </ul>
 *
 * <p>FR-507 invariant (pinned by the selectors test): for every
 * {@link SessionPhase}, at least one tab is enabled — i.e. the active tab
 * always has a non-disabled option to land on.
 */
export function tabDisabled(tab: ActiveTab, phase: SessionPhase): boolean {
  switch (phase) {
    case 'idle':
    case 'picking':
      return tab === 'alter-ego'
    case 'generating':
      return tab === 'setup'
    case 'succeeded':
    case 'failed_with_fallback':
      return false
    default: {
      // Exhaustiveness guard under TS strict — any future SessionPhase
      // addition will fail compilation here until it is handled above.
      const _exhaustive: never = phase
      return _exhaustive
    }
  }
}
