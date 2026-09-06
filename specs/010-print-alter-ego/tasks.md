---
description: "Task list for 010-print-alter-ego"
---

# Tasks: Print Alter Ego

**Input**: Design documents from `/specs/010-print-alter-ego/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅
**No `contracts/`**: feature introduces no API surface.

**Tests**: Test tasks are MANDATORY per Constitution Principle III (Test-First Development). Every new file has an accompanying `*.test.tsx` and every touched file has its test extended. The full suite MUST be red before any production code is written and MUST be green before merge. Unit-line coverage on the changed files MUST be ≥ 90 %.

**Organization**: Tasks are grouped by user story so US1 (MVP) can ship independently of US2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizable with other [P]-marked tasks in the same phase (different files, no dependency on an incomplete task).
- **[Story]**: `[US1]` / `[US2]` maps to spec.md's User Story 1 / User Story 2.
- Every task names an exact file path.

## Path Conventions

Feature 010 is frontend-only. All paths below are under the repo's existing React SPA layout:

- Components & tests: `frontend/src/features/alterego/components/`
- Pure helpers & tests: `frontend/src/features/alterego/lib/`
- Global styles: `frontend/src/index.css`
- Playwright E2E: `frontend/tests/e2e/` (if absent, create alongside existing specs)

No `backend/` changes.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the workspace is ready. No new dependency to install; no new config to edit — this phase is intentionally thin because the feature is purely additive frontend code under an already-scaffolded SPA.

- [X] T001 Confirm `frontend/` builds clean on `claude/speckit-implementation-dK3Mn` by running `npm install && npm run lint && npm run build` from `frontend/` — capture the baseline so later red/green transitions are unambiguous.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The pure, dependency-free helper that every downstream component consumes. Lives under `frontend/src/features/alterego/lib/` so it can be tree-shaken and unit-tested in isolation.

**⚠️ CRITICAL**: No user-story work can begin until this phase is complete, because both `<PrintArtefact>` and the E2E spec consume `humanizeSelection`.

- [X] T002 [P] Write failing unit test for the humanizer — `frontend/src/features/alterego/lib/humanizeSelection.test.ts` — covering: (a) every declared Pose/Archetype/Universe/Vibe/ArtStyle wire value returns the matching `label` from `options.ts`; (b) an unknown `as`-cast value falls back to Title-Cased kebab (e.g. `'made-up-value' → 'Made Up Value'`) rather than throwing.
- [X] T003 Implement the humanizer in `frontend/src/features/alterego/lib/humanizeSelection.ts` — five functions (`humanizePose`, `humanizeArchetype`, `humanizeUniverse`, `humanizeVibe`, `humanizeArtStyle`), each backed by a module-level `Record<WireValue, Label>` built from the matching `*_OPTIONS` array in `features/alterego/options.ts`, with a passthrough `titleCaseKebab` for unknown values. No React import.

**Checkpoint**: Helper green. US1 implementation can begin.

---

## Phase 3: User Story 1 — Print a physical keepsake of my alter ego (Priority: P1) 🎯 MVP

**Goal**: Pressing Print on the Alter Ego tab opens the browser's native print dialog with exactly two pages — image front, text back. Works for both real and fallback outcomes.

**Independent Test**: spec.md US1 Acceptance 1–4: generate end-to-end, click Print, confirm the print preview is a two-page layout (image page 1, text page 2) with no text on the front and no image on the back, cancel the dialog, confirm session state is unchanged and no network or storage activity fired. Re-press Print — preview is byte-identical.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T004 [P] [US1] Write failing component test for `<PrintArtefact>` at `frontend/src/features/alterego/components/PrintArtefact.test.tsx` — covering: (a) when passed a valid `AlterEgoSession` in the `succeeded` phase, the root is a `<section>` with class `print-artefact`; (b) the root has a `print-artefact__front` child containing exactly one `<img>` (src = `session.result.poster.dataUrl`, alt includes `heroTitleLine1`) and no `<h1|h2|h3|p|dl|ul>`; (c) it has a `print-artefact__back` child containing NO `<img>` but all FR-906 fields: hero title (line1+line2), first name row, tagline row, three superpowers in a list, quote blockquote, humanized Pose/Archetype/Universe/Art Style rows, and a "Printed" date row; (d) when `session.vibe === undefined`, no `<dt>` with text "Vibe" exists; (e) humanization is applied — asserting "Cloud Architect" appears, `'cloud-architect'` does not; (f) `meta.outcome === 'fallback'` produces identical rendered tree as `'real'` (no fallback reason text leaks).
- [X] T005 [P] [US1] Write failing component test for `<PrintButton>` at `frontend/src/features/alterego/components/PrintButton.test.tsx` — covering: (a) renders a `<button type="button">` with visible text containing "Print" and `aria-label="Print my alter ego"`; (b) calling it via `await user.click()` invokes the stubbed `window.print` exactly once; (c) Enter activation and Space activation each invoke `window.print` exactly once; (d) the click handler does NOT dispatch any reducer action (pass a spy into context, assert zero calls); (e) pressing it twice in a row invokes `window.print` twice and the component does not hold any local state (render output identical pre- and post-click).
- [X] T006 [P] [US1] Extend `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx` — assert: (a) when phase is `succeeded` and `result` is present, both `<PrintButton>` and `<PrintArtefact>` are in the rendered tree alongside `<PosterView>` + `<StartOverButton>`; (b) when phase is `failed_with_fallback`, same — plus the fallback banner is visible on screen but NOT inside the `.print-artefact` subtree; (c) when phase is `idle` (empty state), neither `<PrintButton>` nor `<PrintArtefact>` is mounted; (d) when phase is `generating`, neither is mounted.
- [X] T007 [P] [US1] Create failing Playwright E2E at `frontend/tests/e2e/print-alter-ego.spec.ts` — journey: (a) complete the Setup flow with a stubbed backend (reuse existing MSW / mock), (b) wait for poster render on the Alter Ego tab, (c) install a `page.evaluate(() => { window.__printCalls = 0; const orig = window.print; window.print = () => { window.__printCalls++; }; })` seam, (d) click the Print button, (e) assert `window.__printCalls === 1`, (f) assert the DOM contains both `.print-artefact__front img` and `.print-artefact__back` with the back-page humanized text visible (via `getComputedStyle` inspection since the print-only tree is `display:none` at screen — use a temporary `matchMedia('print')` override OR assert the DOM presence only), (g) re-click Print — `__printCalls === 2` and the Start Over button still works, (h) post-print, confirm `await page.evaluate(() => Object.keys(localStorage).length + Object.keys(sessionStorage).length) === 0`.

### Implementation for User Story 1

- [X] T008 [P] [US1] Implement `<PrintArtefact>` at `frontend/src/features/alterego/components/PrintArtefact.tsx` — `export function PrintArtefact({ session }: { session: AlterEgoSession })` — derives the `PrintArtefactView` described in `data-model.md §2` and renders `<section className="print-artefact"><section className="print-artefact__front"><img … /></section><section className="print-artefact__back"><header><h2>{heroTitleLine1}</h2><small>{heroTitleLine2}</small></header><dl>…</dl></section></section>`. Vibe row wrapped in `{session.vibe ? <> … </> : null}`. Reads `character`, `poster`, and the five selection fields straight from `session.result` / `session`. `meta.outcome` and `meta.reason` are NOT read anywhere (FR-908).
- [X] T009 [P] [US1] Implement `<PrintButton>` at `frontend/src/features/alterego/components/PrintButton.tsx` — one-liner `onClick={() => window.print()}`, `<button type="button" className="print-button" aria-label="Print my alter ego">Print</button>`. No `useState`, no `useRef`, no `useEffect`, no `dispatch`. The component is a pure JSX constant.
- [X] T010 [US1] Wire `<PrintButton>` + `<PrintArtefact>` into `frontend/src/features/alterego/components/AlterEgoPanel.tsx` — inside the existing `phase === 'succeeded' || phase === 'failed_with_fallback'` branch, render them alongside `<PosterView>` + `<StartOverButton>`. Preserve the existing DOM order: PosterView → StartOverButton → PrintButton (Print sits next to Start Over in the action row) → PrintArtefact (always last child of the poster panel). Depends on T008 + T009.
- [X] T011 [US1] Append the print + hide-on-screen CSS to `frontend/src/index.css` — (a) `.print-artefact { display: none; }` in the screen cascade; (b) `.print-button { … }` matching Start Over's visual weight (same padding, border-radius, font); (c) `@media print { body *  { visibility: hidden; } .print-artefact, .print-artefact * { visibility: visible; } .print-artefact { display: block; position: absolute; inset: 0; } .print-artefact__front { page-break-after: always; } .print-artefact__front img { width: auto; height: auto; max-width: 100%; max-height: 100vh; object-fit: contain; display: block; margin: auto; } .print-artefact__back { page-break-before: always; } @page { size: A4 portrait; margin: 1cm; } }`. Exactly one `@media print` block — no duplicates.

**Checkpoint**: T004–T007 were red before T008; now green. Dev runs quickstart.md Flow A and Flow B manually; automated suite passes. US1 ships independently.

---

## Phase 4: User Story 2 — Know when Print is available (Priority: P2)

**Goal**: The Print affordance NEVER appears in the empty-state or loading-state variants of the Alter Ego tab, and disappears together with the rest of the poster surface on Start Over.

**Independent Test**: spec.md US2 Acceptance 1–2: (a) fresh app, navigate to the Alter Ego tab (after 007 unlocks it — but it stays locked here because no generation), confirm no Print button anywhere in the DOM; (b) generate, confirm Print appears; (c) click Start Over, confirm Print disappears together with the poster.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T012 [US2] Extend `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx` with a `describe('Print gating (US2)')` block — asserts the assertions already drafted in T006 (c/d) plus a transition test: mount in `idle` phase, transition to `succeeded` via re-render, Print mounts; transition back to `idle` via `StartOverRequested`, Print unmounts.

### Implementation for User Story 2

- [X] T013 [US2] No production code change required — US2 is satisfied by construction because `<PrintButton>` and `<PrintArtefact>` are rendered INSIDE the existing `phase === 'succeeded' || phase === 'failed_with_fallback'` JSX branch in `AlterEgoPanel.tsx` (wired in T010). This task is confirmation that the T012 assertions pass without any additional production code — if they don't, revisit T010's placement. Document the decision in a comment above the branch if it isn't already obvious from T010.

**Checkpoint**: US1 + US2 both independently verifiable. Start Over also clears Print — same semantics as 007's tab-gating reset.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification gates and housekeeping.

- [X] T014 [P] Run `npm run lint` from `frontend/` — fix any ESLint findings in the new files.
- [X] T015 [P] Run `npm run test -- --coverage` from `frontend/` — confirm ≥ 90 % line coverage on `components/PrintButton.tsx`, `components/PrintArtefact.tsx`, `lib/humanizeSelection.ts`. Address any gaps by tightening assertions rather than lowering the bar (Constitution Principle III).
- [X] T016 [P] Run `npm run build` from `frontend/` — ensure the `@media print` block parses, no CSS duplication warnings, bundle size delta is negligible (< 2 KB gzipped expected).
- [ ] T017 Walk through `specs/010-print-alter-ego/quickstart.md` Flows A–E end-to-end on a real browser (Chromium AND Firefox minimum — Safari if available). Attach the print-preview screenshots (front + back) to the PR description for reviewer confirmation of FR-903 / FR-909 / FR-910 / FR-911.
- [ ] T018 Verify SC-904 and SC-905 on a live session: open DevTools → Network, clear, press Print, confirm zero requests fire. Storage snapshot before vs after the click: zero delta across `localStorage`, `sessionStorage`, `indexedDB`, cookies.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no blocker.
- **Phase 2 (Foundational)**: blocks all user stories — T008 and T007 both import `humanizeSelection`.
- **Phase 3 (US1)**: blocks Phase 4 (T012 asserts Print gating, which assumes Print exists).
- **Phase 4 (US2)**: blocks Phase 5 only insofar as the final coverage + walkthrough targets both stories.
- **Phase 5 (Polish)**: run after US1 and US2 green.

### Within Phase 3 (US1)

- T004, T005, T006, T007 can be written in parallel (all [P]) — they are in different files and RED before any production code exists.
- T008 and T009 can be written in parallel (both [P]) — different files, both depend only on T002/T003 and their own tests.
- T010 depends on T008 + T009 (it imports them).
- T011 can be done in parallel with T010 as long as the CSS class names agreed in research.md §R7 are respected.

### User Story Dependencies

- US1 has no dependency on US2.
- US2 is implemented by correctly placing US1's components; if US1 was regressed, US2 would regress automatically — intentional coupling (spec FR-901 / FR-904 pinned them to the same gate).

### Parallel Opportunities

Realistic parallel batches for a solo developer running tasks concurrently in their editor:

1. **Foundational**: T002 → T003 (sequential within, but the two files are small).
2. **US1 test pass**: T004, T005, T006, T007 in parallel.
3. **US1 implementation pass**: T008, T009, T011 in parallel; T010 after T008 + T009.
4. **US2 test pass**: T012 alone.
5. **Polish**: T014, T015, T016 in parallel; T017 and T018 sequentially after the build is green.

---

## Format Validation

Every task above follows the required checklist format: `- [ ] TxxxP? Story? description with exact file path`. Story labels are present on all US1 / US2 tasks and absent from Setup / Foundational / Polish tasks. File paths are absolute-relative-to-repo-root. No sample template lines remain.
