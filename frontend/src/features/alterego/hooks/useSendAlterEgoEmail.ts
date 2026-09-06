/**
 * 023 (issue #57) — TanStack Query mutation that wires
 * {@link sendAlterEgoEmail} into the Alter Ego tab's Send-As-Email
 * button. Fires a single native `window.alert(...)` on every terminal
 * outcome (clarification 2026-05-11 Q2):
 *
 *  - `sent`           → `"Email sent to {to}."`
 *  - `not_configured` → `"The email server is not yet configured."`
 *  - `failed`         → `"Sending failed. Please try again."`
 *
 * The same primitive carries all three messages — no toast/banner
 * system is introduced (research R12 / spec FR-2310..FR-2316).
 */
import { useMutation } from '@tanstack/react-query'
import { useCallback } from 'react'
import { sendAlterEgoEmail, type SendOutcome } from '../services/emailClient'

interface SendArgs {
  to: string
  firstName: string
  posterDataUrl: string
  correlationId: string
}

export function useSendAlterEgoEmail() {
  const mutation = useMutation<{ outcome: SendOutcome }, Error, SendArgs>({
    mutationFn: (args) => sendAlterEgoEmail(args),
    onSuccess: ({ outcome }, variables) => {
      switch (outcome) {
        case 'sent':
          window.alert(`Email sent to ${variables.to}.`)
          return
        case 'not_configured':
          window.alert('The email server is not yet configured.')
          return
        case 'failed':
          window.alert('Sending failed. Please try again.')
          return
      }
    },
    onError: () => {
      // Network errors that escape sendAlterEgoEmail (it normally
      // converts to `failed`) — surface the same retryable alert.
      window.alert('Sending failed. Please try again.')
    },
  })

  const send = useCallback((args: SendArgs) => mutation.mutate(args), [mutation])

  return { send, isPending: mutation.isPending }
}
