import { useSendAlterEgoEmail } from '../hooks/useSendAlterEgoEmail'
import { validateEmail } from '../validation/email'

export interface SendArgs {
  to: string
  firstName: string
  posterDataUrl: string
  correlationId: string
}

interface Props {
  /** Session.email — controlled by the Setup tab's EmailInput AND the
   *  025 inline-fallback on the Alter Ego tab. */
  email: string
  /** Session.firstName — passes through to the email body template. */
  firstName: string
  /** Currently-displayed poster's data URL. The button decodes this
   *  into a Blob inside {@link sendAlterEgoEmail}. */
  posterDataUrl: string
  /**
   * 025 (issue #60) — optional in-flight signal lifted from the parent.
   * When provided, OVERRIDES the value read from the internal
   * {@code useSendAlterEgoEmail()} hook. Allows
   * {@code AlterEgoPanel} to share one in-flight state between the
   * inline email-fallback's `disabled` prop and the button itself
   * (research R4).
   */
  isPending?: boolean
  /**
   * 025 (issue #60) — optional sender lifted from the parent. When
   * provided, OVERRIDES the value read from the internal
   * {@code useSendAlterEgoEmail()} hook. Lets the parent call the
   * hook exactly once and pass the same `send` reference to multiple
   * children.
   */
  send?: (args: SendArgs) => void
}

/**
 * 023 (issue #57) — Alter Ego tab action button, rendered to the right
 * of {@code PrintButton} in {@code AlterEgoPanel}'s actions row.
 *
 * <p>Gating (clarification 2026-05-11 Q1, FR-2315):
 *   - Disabled (with `aria-disabled` + reduced opacity via CSS + an
 *     `aria-label` / `title` explaining the missing prerequisite)
 *     when the captured email is blank OR fails the format validator.
 *   - Disabled while a send is in flight (`isPending`) so the button
 *     can't be double-clicked into a duplicate dispatch.
 *
 * <p>025 (issue #60) delta: optional {@code isPending} / {@code send}
 * props let {@code AlterEgoPanel} call {@code useSendAlterEgoEmail}
 * once and share both values with the inline email-fallback. When the
 * props are absent (e.g. legacy callsites), the component falls back to
 * the hook just like before — the 023 callsites' tests still pin that
 * fallback behaviour explicitly.
 *
 * <p>Click: dispatches the mutation with `to = email.trim()`,
 * `firstName`, `posterDataUrl`, and a fresh UUID correlation ID. The
 * hook surfaces one native alert per terminal outcome — there is no
 * inline success/failure UI here.
 */
export function SendAsEmailButton({
  email,
  firstName,
  posterDataUrl,
  isPending: isPendingProp,
  send: sendProp,
}: Props) {
  const { send: sendFromHook, isPending: isPendingFromHook } = useSendAlterEgoEmail()
  const effectiveSend = sendProp ?? sendFromHook
  const effectivePending = isPendingProp ?? isPendingFromHook

  const validation = validateEmail(email)
  const trimmed = validation.ok ? validation.trimmed : ''
  const hasValidRecipient = trimmed.length > 0
  const disabled = !hasValidRecipient || effectivePending

  const accessibleHint = hasValidRecipient
    ? effectivePending
      ? 'Sending email…'
      : 'Send my alter ego by email'
    : 'Enter a valid email above to enable sending'

  return (
    <button
      type="button"
      className="send-as-email-button"
      aria-disabled={disabled || undefined}
      disabled={disabled}
      aria-label={accessibleHint}
      title={accessibleHint}
      onClick={() => {
        if (disabled) return
        effectiveSend({
          to: trimmed,
          firstName,
          posterDataUrl,
          correlationId: crypto.randomUUID(),
        })
      }}
    >
      Send As Email
    </button>
  )
}
