import { EmailInput } from './EmailInput'

interface Props {
  /** Controlled value — typically {@code session.email}. */
  value: string
  /** Fires the {@code EmailChanged} reducer action with the raw value. */
  onChange: (next: string) => void
  /** When {@code true}, both the input AND the clear-X are disabled —
   *  used while a Send-As-Email request is in flight. */
  disabled?: boolean
}

/**
 * 025 (issue #60) — inline email fallback rendered on the Alter Ego tab,
 * directly above the action-button row, ONLY when the captured email is
 * not send-ready (FR-2501).
 *
 * <p>Thin wrapper around {@link EmailInput} that adds the
 * {@code .alter-ego-panel__email-fallback} container class for spacing
 * (research R5 — visual symmetry with the Setup-tab input). The actual
 * label / input / clear-X / inline-error a11y contract is delegated to
 * {@link EmailInput} verbatim, so there is exactly one source of truth
 * for the email-field UI (FR-2503 / FR-2507 / FR-2513).
 *
 * <p>The wrapper does not own the visibility rule itself —
 * {@code AlterEgoPanel} decides whether to mount this component using
 * the {@code isSendableEmail} selector. The wrapper also does not own
 * the send-pipeline state — {@code AlterEgoPanel} lifts
 * {@code useSendAlterEgoEmail.isPending} and threads it as
 * {@link Props#disabled} so the inline input and the Send-As-Email
 * button share one in-flight signal (research R4).
 */
export function InlineEmailFallback({ value, onChange, disabled = false }: Props) {
  return (
    <div className="alter-ego-panel__email-fallback">
      <EmailInput value={value} onChange={onChange} disabled={disabled} />
    </div>
  )
}
