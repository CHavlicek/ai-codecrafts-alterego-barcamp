import { describe, expect, test, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { AlterEgoProvider } from '../state/AlterEgoProvider'
import { initialAlterEgoSession } from '../state/reducer'
import { SetupLayout } from './SetupLayout'
import { LiveRegionProvider } from '../../../components/LiveRegion'

// 004: PhotoIntake now consumes useLiveAnnouncer to announce camera
// state transitions (FR-311), so rendering SetupLayout in isolation
// requires the LiveRegionProvider in context.

/**
 * SetupLayout (020 update). Spec FR-110 / FR-115 + spec
 * specs/020-hide-vibe-pose: two columns on desktop with YOUR PHOTO on
 * the left and ROLE, UNIVERSE &amp; KEYS on the right; the right column
 * hosts exactly three numbered sub-groups (Engineer role / Universe /
 * Art style) plus the Name input and the Generate button in that order.
 * Pose and Vibe are no longer rendered (closes issue #51).
 */

function renderLayout() {
  return render(
    <LiveRegionProvider>
      <AlterEgoProvider>
        <SetupLayout
          session={initialAlterEgoSession()}
          dispatch={() => {}}
          isSubmitting={false}
          onSubmit={vi.fn()}
          onSurprise={vi.fn()}
        />
      </AlterEgoProvider>
    </LiveRegionProvider>,
  )
}

describe('SetupLayout', () => {
  test('renders the two column headings', () => {
    renderLayout()
    expect(screen.getByRole('heading', { name: /^YOUR PHOTO$/i, level: 2 })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: /^ROLE, UNIVERSE & KEYS$/i, level: 2 }),
    ).toBeInTheDocument()
  })

  test('photo column shows the "Real face as a direct reference" subtitle', () => {
    renderLayout()
    expect(screen.getByText(/real face as a direct reference/i)).toBeInTheDocument()
  })

  test('renders three numbered sub-groups in order: Role, Universe, Art style', () => {
    renderLayout()
    // 022 — Role-category legend renamed from "Engineer role" to "Role"
    // (issue #50): the three new prefab options are not engineering roles
    // and the custom-role input below the grid widens the category beyond
    // engineering.
    expect(screen.getByText('Role')).toBeInTheDocument()
    expect(screen.getByText('Universe / Style')).toBeInTheDocument()
    expect(screen.getByText('Art style')).toBeInTheDocument()

    // Numbered data-step ordering: 1..3 after 020 removed Pose (step 1) and
    // Vibe (step 5).
    const groups = document.querySelectorAll('.setup-layout__numbered-group')
    expect(groups).toHaveLength(3)
    expect(groups[0]?.getAttribute('data-step')).toBe('1')
    expect(groups[1]?.getAttribute('data-step')).toBe('2')
    expect(groups[2]?.getAttribute('data-step')).toBe('3')
  })

  test('020 — no element labelled "Pose" or "Vibe" is reachable in the Setup tab', () => {
    renderLayout()
    expect(screen.queryByText('Pose')).not.toBeInTheDocument()
    expect(screen.queryByText(/vibe \(optional\)/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Pose')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Vibe')).not.toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: 'Heroic' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Rebel' })).not.toBeInTheDocument()
  })

  test('011 — when session.photoMode is "group", the name input renders with the "Group name" label', () => {
    render(
      <LiveRegionProvider>
        <AlterEgoProvider>
          <SetupLayout
            session={{ ...initialAlterEgoSession(), photoMode: 'group' }}
            dispatch={() => {}}
            isSubmitting={false}
            onSubmit={() => {}}
            onSurprise={() => {}}
          />
        </AlterEgoProvider>
      </LiveRegionProvider>,
    )
    expect(screen.getByLabelText('Group name')).toBeInTheDocument()
    expect(screen.queryByLabelText('First name')).not.toBeInTheDocument()
  })

  test('011 — Photo Mode toggle lives in the photo column, defaults to Single, dispatches PhotoModeSelected on flip', async () => {
    const dispatch = vi.fn()
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    render(
      <LiveRegionProvider>
        <AlterEgoProvider>
          <SetupLayout
            session={initialAlterEgoSession()}
            dispatch={dispatch}
            isSubmitting={false}
            onSubmit={() => {}}
            onSurprise={() => {}}
          />
        </AlterEgoProvider>
      </LiveRegionProvider>,
    )
    const sw = screen.getByRole('switch', { name: /photo mode/i })
    expect(sw).toHaveAttribute('aria-checked', 'false')
    expect(sw).toHaveAttribute('data-state', 'single')

    // The toggle is a descendant of the photo section, not the selections section.
    const photoSection = document.querySelector('.setup-layout__photo')
    const selectionsSection = document.querySelector('.setup-layout__selections')
    expect(photoSection?.contains(sw)).toBe(true)
    expect(selectionsSection?.contains(sw)).toBe(false)

    await user.click(sw)
    expect(dispatch).toHaveBeenCalledWith({ type: 'PhotoModeSelected', photoMode: 'group' })
  })

  test('Name input appears between Art style and Generate in the DOM order', () => {
    renderLayout()
    const nameInput = screen.getByLabelText('First name')
    const generate = screen.getByRole('button', { name: /generate my alter ego/i })
    const artStyleLegend = screen.getByText('Art style')
    // Document-order comparison: art style precedes name, name precedes generate.
    expect(
      artStyleLegend.compareDocumentPosition(nameInput) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    expect(
      nameInput.compareDocumentPosition(generate) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  })

  test('Generate button starts disabled on a fresh session (gate from selectors)', () => {
    renderLayout()
    expect(screen.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()
  })

  test('each sub-group click dispatches the matching reducer action', async () => {
    const dispatch = vi.fn()
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    render(
      <LiveRegionProvider>
        <AlterEgoProvider>
          <SetupLayout
            session={initialAlterEgoSession()}
            dispatch={dispatch}
            isSubmitting={false}
            onSubmit={() => {}}
            onSurprise={() => {}}
          />
        </AlterEgoProvider>
      </LiveRegionProvider>,
    )

    await user.click(screen.getByRole('radio', { name: 'Software Developer' }))
    expect(dispatch).toHaveBeenCalledWith({
      type: 'ArchetypeSelected',
      archetype: 'software-developer',
    })

    await user.click(screen.getByRole('radio', { name: 'Star Wars' }))
    expect(dispatch).toHaveBeenCalledWith({ type: 'UniverseSelected', universe: 'star-wars' })

    await user.click(screen.getByRole('radio', { name: 'Pop Art' }))
    expect(dispatch).toHaveBeenCalledWith({ type: 'ArtStyleSelected', artStyle: 'pop-art' })

    await user.type(screen.getByLabelText('First name'), 'P')
    expect(dispatch).toHaveBeenCalledWith({ type: 'FirstNameChanged', firstName: 'P' })

    // 020 — neither Pose nor Vibe is dispatchable from this layout any more.
    expect(dispatch).not.toHaveBeenCalledWith(expect.objectContaining({ type: 'PoseSelected' }))
    expect(dispatch).not.toHaveBeenCalledWith(expect.objectContaining({ type: 'VibeSelected' }))
  })

  test('009 — renders Surprise Me button next to Generate in a shared actions row', () => {
    renderLayout()
    const generate = screen.getByRole('button', { name: /generate my alter ego/i })
    const surprise = screen.getByRole('button', { name: /surprise me/i })
    expect(generate).toBeInTheDocument()
    expect(surprise).toBeInTheDocument()
    const actionsRow = document.querySelector('.setup-layout__actions')
    expect(actionsRow).not.toBeNull()
    expect(actionsRow!.contains(generate)).toBe(true)
    expect(actionsRow!.contains(surprise)).toBe(true)
  })

  test('009 — clicking Surprise Me (when enabled) fires onSurprise', async () => {
    const onSurprise = vi.fn()
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    render(
      <LiveRegionProvider>
        <AlterEgoProvider>
          <SetupLayout
            session={{
              ...initialAlterEgoSession(),
              photoBlob: new Blob([new Uint8Array([1])], { type: 'image/jpeg' }),
              firstName: 'Paula',
            }}
            dispatch={() => {}}
            isSubmitting={false}
            onSubmit={() => {}}
            onSurprise={onSurprise}
          />
        </AlterEgoProvider>
      </LiveRegionProvider>,
    )
    await user.click(screen.getByRole('button', { name: /surprise me/i }))
    expect(onSurprise).toHaveBeenCalledTimes(1)
  })

  test('023 — Email input is rendered between the numbered category groups and First name', () => {
    renderLayout()
    const emailInput = screen.getByLabelText('Email')
    const firstName = screen.getByLabelText('First name')
    const artStyleLegend = screen.getByText('Art style')
    // Document-order: art style precedes email, email precedes first name.
    expect(
      artStyleLegend.compareDocumentPosition(emailInput) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    expect(
      emailInput.compareDocumentPosition(firstName) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    // The Email input is NOT inside any numbered group — it is a sibling
    // of FirstNameInput at the section level (FR-2301).
    const numberedGroups = document.querySelectorAll('.setup-layout__numbered-group')
    for (const group of Array.from(numberedGroups)) {
      expect(group.contains(emailInput)).toBe(false)
    }
  })

  test('023 — typing in the Email input dispatches EmailChanged', async () => {
    const dispatch = vi.fn()
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    render(
      <LiveRegionProvider>
        <AlterEgoProvider>
          <SetupLayout
            session={initialAlterEgoSession()}
            dispatch={dispatch}
            isSubmitting={false}
            onSubmit={() => {}}
            onSurprise={() => {}}
          />
        </AlterEgoProvider>
      </LiveRegionProvider>,
    )
    await user.type(screen.getByLabelText('Email'), 'a')
    expect(dispatch).toHaveBeenCalledWith({ type: 'EmailChanged', email: 'a' })
  })

  test('submitting the form fires onSubmit (not a page navigation)', () => {
    const onSubmit = vi.fn()
    render(
      <LiveRegionProvider>
        <AlterEgoProvider>
          <SetupLayout
            session={initialAlterEgoSession()}
            dispatch={() => {}}
            isSubmitting={false}
            onSubmit={onSubmit}
            onSurprise={() => {}}
          />
        </AlterEgoProvider>
      </LiveRegionProvider>,
    )
    // The Generate button is disabled on a fresh session, so programmatic
    // form submission is the only path that reaches onSubmit. Asserts that
    // the layout's <form onSubmit={onSubmit}> handler is wired, not a page
    // navigation.
    const form = document.querySelector('form.setup-layout') as HTMLFormElement
    fireEvent.submit(form)
    expect(onSubmit).toHaveBeenCalled()
  })
})
