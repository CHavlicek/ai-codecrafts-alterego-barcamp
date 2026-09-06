import { useState } from 'react'
import { UserCircle2 } from 'lucide-react'
import type { AlterEgoSession } from '../state/reducer'
import { isSendableEmail } from '../state/selectors'
import { humanizeArchetype } from '../lib/humanizeSelection'
import { useAlterEgoSession } from '../hooks/useAlterEgoSession'
import { useSendAlterEgoEmail } from '../hooks/useSendAlterEgoEmail'
import { GenerationLoading } from './GenerationLoading'
import { InlineEmailFallback } from './InlineEmailFallback'
import { PosterView } from './PosterView'
import { PrintButton } from './PrintButton'
import { SendAsEmailButton } from './SendAsEmailButton'
import { StartOverButton } from './StartOverButton'

interface Props {
  session: AlterEgoSession
  isGenerating: boolean
  onStartOver: () => void
}

/**
 * Content for the "2 Your Alter Ego" tab. Renders one of three states
 * depending on the session phase (spec FR-107):
 *
 *   a) Empty placeholder pre-generation — calm, directive copy per
 *      research.md §R3. Not a broken/error state.
 *   b) Loading indicator during generation (inherited from 001 FR-011).
 *   c) Generated poster + Start-over (inherited from 001 FR-012/013).
 *
 * <p>025 (issue #60) delta: when the panel is in the poster branch AND
 * the captured email is not send-ready (blank or invalid), an inline
 * email-fallback is rendered above the actions row so the user can
 * recover a forgotten / mistyped address without leaving the Alter Ego
 * tab (FR-2501..FR-2509). The visibility predicate is
 * {@link isSendableEmail}; the in-flight signal is lifted from a single
 * {@link useSendAlterEgoEmail} call so the inline field and the
 * {@code SendAsEmailButton} share one source of truth (research R4).
 *
 * <p>Hooks are unconditional at the top of the function (React rule-of-
 * hooks). {@code useSendAlterEgoEmail} mounts {@code useMutation} which
 * is cheap and idempotent — calling it in the empty/loading branches
 * has no observable effect because nothing dispatches.
 */
export function AlterEgoPanel({ session, isGenerating, onStartOver }: Props) {
  const { dispatch } = useAlterEgoSession()
  const { send, isPending } = useSendAlterEgoEmail()

  // 025 sticky-visibility latch (clarification 2026-05-15): once the
  // inline email-fallback is rendered during a given poster session,
  // it stays visible for the rest of that session — even after the
  // captured email becomes send-ready (FR-2508 amended). A bare
  // visibility flip mid-typing would jolt the layout. The latch resets
  // when the session leaves the terminal phases (e.g. via Start Over
  // or a fresh Generate), so a new poster session re-evaluates from
  // scratch.
  //
  // Implementation uses the React "setState during render" idiom
  // rather than `useEffect`. The latch is fully derived from props
  // (`session.phase` + `isSendableEmail(session)`), so an Effect would
  // be over-engineering and would also trip the
  // `react-hooks/set-state-in-effect` rule. See React docs: "You Might
  // Not Need an Effect — Adjusting state when a prop changes".
  const isTerminalPhase = session.phase === 'succeeded' || session.phase === 'failed_with_fallback'
  const sendable = isSendableEmail(session)
  const [hasShownInline, setHasShownInline] = useState(false)
  const nextLatch = isTerminalPhase ? hasShownInline || !sendable : false
  if (nextLatch !== hasShownInline) {
    setHasShownInline(nextLatch)
  }

  if (isGenerating || session.phase === 'generating') {
    return (
      <div className="alter-ego-panel alter-ego-panel--loading">
        <GenerationLoading />
      </div>
    )
  }

  if (
    (session.phase === 'succeeded' || session.phase === 'failed_with_fallback') &&
    session.result
  ) {
    // Sticky: show iff (a) the captured email is currently not send-
    // ready, OR (b) the inline field was already rendered earlier in
    // this poster session (FR-2508 amended). `nextLatch` captures both
    // conditions at once.
    const showInlineEmail = nextLatch
    return (
      <div className="alter-ego-panel alter-ego-panel--poster">
        <PosterView
          result={session.result}
          errorMessage={session.errorMessage}
          firstName={session.firstName}
          roleLabel={session.archetype ? humanizeArchetype(session.archetype) : ''}
        />
        {showInlineEmail ? (
          <InlineEmailFallback
            value={session.email}
            onChange={(email) => dispatch({ type: 'EmailChanged', email })}
            disabled={isPending}
          />
        ) : null}
        <div className="alter-ego-panel__actions">
          <StartOverButton photoPreviewUrl={session.photoPreviewUrl} onStartOver={onStartOver} />
          <PrintButton posterDataUrl={session.result.poster.dataUrl} />
          {/* 023 (issue #57): sibling action — Send As Email sits to the
              right of Print (FR-2310). Disabled until the captured email
              is non-blank and validates (clarification 2026-05-11 Q1).
              025 (issue #60): isPending + send lifted from this parent so
              the inline email-fallback above shares the in-flight signal. */}
          <SendAsEmailButton
            email={session.email}
            firstName={session.firstName}
            posterDataUrl={session.result.poster.dataUrl}
            isPending={isPending}
            send={send}
          />
        </div>
      </div>
    )
  }

  return (
    <div className="alter-ego-panel alter-ego-panel--empty">
      <div className="alter-ego-panel__empty-icon" aria-hidden="true">
        <UserCircle2 width={56} height={56} strokeWidth={1.25} />
      </div>
      <p className="alter-ego-panel__empty-copy">
        Your alter ego will appear here once you press <strong>Generate</strong> on the Setup tab.
      </p>
    </div>
  )
}
