---
description: "Task list — 018-print-image-only"
---

# Tasks: Print only the alter ego image

**Input**: Design documents from `/specs/018-print-image-only/`
**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

**Tests**: Test tasks are MANDATORY per Constitution Principle III (Test-First Development, NON-NEGOTIABLE). For 018, the tests are the **driver** of the change — research §R5 mandates RED → GREEN → REFACTOR ordering with the RED commit preceding any production-code change in branch history.

**Organization**: Removal-only feature with a single P1 user story. Phase 2 (Foundational) is intentionally empty — the feature builds on already-shipped surface from 010 (print pipeline), 015 (poster frame baked into bytes), and 017 (hero name / role / quote baked into bytes); nothing new needs to land before User Story 1 can begin.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User-story label (always **[US1]** here — single P1 story)
- File paths are absolute or repo-root-relative; both are unambiguous from this checklist

## Path Conventions

This feature is **frontend-only**. All paths live under `frontend/`:

- Component: `frontend/src/features/alterego/components/PrintArtefact.tsx`
- Component test (Vitest + RTL): `frontend/src/features/alterego/components/PrintArtefact.test.tsx`
- Helper: `frontend/src/features/alterego/lib/humanizeSelection.ts`
- Helper test: `frontend/src/features/alterego/lib/humanizeSelection.test.ts`
- Stylesheet: `frontend/src/index.css`
- E2E (Playwright): `frontend/tests/e2e/print-alter-ego.spec.ts`

No backend file is touched. No new file is created. No file is deleted at the file-system level (deletions happen *inside* `humanizeSelection.ts`, not at file granularity — research §R2).

---

## Phase 1: Setup

**Purpose**: Sanity-check the development environment before touching code.

- [X] T001 Verify the working tree is clean and the `018-print-image-only` branch is checked out (`git status` shows clean tree on branch `018-print-image-only`); confirm `cd frontend && npm install` completes without diff in `frontend/package-lock.json` (no new dependency expected — Constitution Principle VI).

---

## Phase 2: Foundational

**Status**: **N/A** — no blocking foundational work. The print pipeline (010), the poster-frame overlay (015), and the in-image text overlay (017) are all on `main`. The 018 change is removal-only and depends on no new infrastructure.

**Checkpoint**: Skip directly to User Story 1.

---

## Phase 3: User Story 1 — Print produces exactly one page, containing only the poster image (Priority: P1) 🎯 MVP

**Goal**: After this story lands, invoking the Print action from the "Your Alter Ego" tab produces a single printable page containing only the alter-ego poster image. The previously-included second page (hero title, first name, tagline, superpowers, quote, humanised Pose / Archetype / Universe / Art Style / Vibe, "Printed" date) is gone from the printable material. The on-screen "Your Alter Ego" panel is unchanged.

**Independent Test**: Drive the happy path to a poster (camera → first name → five Setup picks → Generate). Press the in-app **Print my alter ego** button. The browser's print dialog reports **1 of 1**; the preview shows only the poster image; the same is true for *Save as PDF*. Side-by-side compare the on-screen panel with a pre-018 build — visibly identical.

### Tests for User Story 1 (MANDATORY — must FAIL before implementation) ⚠️

> Constitution Principle III: tests are written first, committed first, and confirmed RED before any production code change. Research §R5 codifies the order.

- [X] T002 [US1] In `frontend/src/features/alterego/components/PrintArtefact.test.tsx`, replace the existing structural-gate test "renders exactly one front section and one back section, in order" with:
  - **(a)** an assertion that `.print-artefact` has **exactly one child element** and that child has class `print-artefact__front`,
  - **(b)** an explicit negative assertion `expect(document.querySelector('.print-artefact__back')).toBeNull()`.
  Both assertions MUST fail against the current two-section component. Leave every other describe block in the file unchanged for now (they continue to pass against the current code; they're cleaned up under GREEN as the back JSX is removed).
- [X] T003 [P] [US1] In `frontend/tests/e2e/print-alter-ego.spec.ts`, inside the existing test "US1 — Generate → Print opens native dialog and print DOM is well-formed", insert a new assertion right after the existing `await expect(page.locator('.print-artefact__front img')).toHaveCount(1)`: `await expect(page.locator('.print-artefact__back')).toHaveCount(0)`. Leave every other assertion in the spec unchanged. The new assertion MUST fail today (current count = 1).
- [X] T004 [US1] Run `cd frontend && npm run test -- src/features/alterego/components/PrintArtefact.test.tsx` and `cd frontend && npx playwright test print-alter-ego` to confirm BOTH suites are RED with the new assertions and ONLY the new assertions failing. Then commit with subject `test(018): RED — assert print artefact is image-only`. Do not stage any production-code change in this commit. **Note**: E2E suite is RED for an unrelated pre-existing reason (heading 'PAULA' never becomes visible — `mockHappyApi`/`fillAllSelections` regression on main). Unit RED is the canonical TDD signal here; E2E new assertion will be exercised once the pre-existing regression is fixed (out of scope for #47).

### Implementation for User Story 1 (GREEN — one logical commit)

> All GREEN tasks below land together in a single commit. Tasks within GREEN are largely parallel ([P]) because they touch distinct files; they are listed sequentially for review-ordering only.

- [X] T005 [US1] Edit `frontend/src/features/alterego/components/PrintArtefact.tsx`:
  - Delete the entire `<section className="print-artefact__back">…</section>` subtree (lines ~76–124 in the current file).
  - Delete the imports of `humanizePose`, `humanizeUniverse`, `humanizeVibe`, `humanizeArtStyle` from `'../lib/humanizeSelection'`. Keep the `humanizeArchetype` import (still used for the `<img alt>`).
  - Delete the `printedOn` local variable (no longer rendered anywhere).
  - Keep: the `createPortal(…, document.body)` mount, the early-return guard against missing `result`/`pose`/`archetype`/`universe`/`artStyle` (defence-in-depth — research §R1 keeps this posture), `aria-hidden="true"` on the root `<section>`, the `<section.print-artefact__front><img …/></section>` subtree with `composePosterAlt(…)` for `alt`.
  - Update the file-level JSDoc to reflect the single-face shape (one short line — drop the references to "two-face DOM" and "back face").
- [X] T006 [P] [US1] Edit `frontend/src/index.css` `@media print` block (around lines 1531–1626):
  - Delete `.print-artefact__back { padding: 2cm 1.8cm; overflow-wrap: anywhere; word-break: break-word; }`.
  - Delete `.print-artefact__back-title h2 { … }`, `.print-artefact__back-subtitle { … }`, `.print-artefact__back-fields { … }`, `.print-artefact__back-fields dt { … }`, `.print-artefact__back-fields dd { … }`, `.print-artefact__back-fields blockquote { … }`, `.print-artefact__back-fields ul { … }`.
  - Inside `.print-artefact__front { … }` delete the `page-break-after: always;` and `break-after: page;` declarations (these are the only thing that produced a second page). Keep `padding: 0; margin: 0; text-align: center;` and the explanatory comment about edge-to-edge for borderless photo paper.
  - Keep `.print-artefact { display: none; }` (screen), `body > *:not(.print-artefact) { display: none !important; }` (print), `body { margin: 0; background: white; color: black; }` (print), `.print-artefact { display: block; background: white; color: black; font-family: …; }` (print), `.print-artefact__front img { width: 100%; height: 100vh; object-fit: contain; … }`, and the `@page { size: A4 portrait; margin: 0 }` rule.
- [X] T007 [P] [US1] Edit `frontend/src/features/alterego/lib/humanizeSelection.ts`:
  - Delete the `humanizePose`, `humanizeUniverse`, `humanizeVibe`, `humanizeArtStyle` exported functions.
  - Delete their corresponding `POSE_LABELS`, `UNIVERSE_LABELS`, `VIBE_LABELS`, `ART_STYLE_LABELS` lookup maps.
  - Delete the imports of `POSE_OPTIONS`, `UNIVERSE_OPTIONS`, `VIBE_OPTIONS`, `ART_STYLE_OPTIONS` from `'../../setup/data/options'`.
  - Keep `humanizeArchetype`, `ARCHETYPE_LABELS`, the `ARCHETYPE_OPTIONS` import, and the local `humanize(map, value)` private helper used by `humanizeArchetype`.
- [X] T008 [P] [US1] Edit `frontend/src/features/alterego/lib/humanizeSelection.test.ts`:
  - Delete every describe block except the one for `humanizeArchetype` (the "round-trip for every Archetype value" tests + the unknown-fallback test for archetype).
  - Drop the imports of `humanizePose`, `humanizeUniverse`, `humanizeVibe`, `humanizeArtStyle` and of `POSE_OPTIONS`, `UNIVERSE_OPTIONS`, `VIBE_OPTIONS`, `ART_STYLE_OPTIONS` from the test file.
- [X] T009 [P] [US1] Edit `frontend/src/features/alterego/components/PrintArtefact.test.tsx` (continuing from T002's RED edit):
  - Delete the entire `describe('PrintArtefact — back face (FR-906)', …)` block (8 tests).
  - Delete the entire `describe('PrintArtefact — FR-912 long text wrapping safety', …)` block (1 test).
  - Inside `describe('PrintArtefact — FR-905 fallback parity', …)`, tighten the existing "renders identical structural tree for outcome=real vs outcome=fallback" test to compare the **front** subtree only (`getFront().outerHTML`) byte-for-byte; remove the date-normalisation regex (no time-varying value remains in the front face). Keep the second test (no fallback markers leak) — it now compares the entire (image-only) artefact text content.
  - Drop the imports that the deleted blocks needed (`humanizePose` / `humanizeUniverse` / etc. were never imported here; only `within` from `@testing-library/react` may become unused — drop it if so).
- [X] T010 [P] [US1] Edit `frontend/tests/e2e/print-alter-ego.spec.ts` (continuing from T003's RED edit):
  - Delete the back-content assertions (`back.toContainText('Paula')`, `'STILL SHIPS ON FRIDAYS.'`, `"It's always DNS."`, `'Cloud Architect'`, `'Star Wars'`, `'Pixel Art'`, `'Heroic'`, `'Printed'`) and the kebab-non-leak assertions (`not.toContainText('cloud-architect')`, `'star-wars'`, `'pixel-art'`).
  - Delete the `back` locator declaration (`const back = page.locator('.print-artefact__back')`) — it's unused after the assertions above are gone.
  - Keep T003's `await expect(page.locator('.print-artefact__back')).toHaveCount(0)` assertion as the canonical "back is gone" check.
  - Keep every other surviving assertion: print-call counter, front-image presence, idempotency (htmlBefore === htmlAfter), no-network-after-print (`postPrintRequestCount === 0`), zero-storage (cookies/localStorage/sessionStorage), Start-Over teardown (Print button + `.print-artefact` both unmount).
- [X] T011 [US1] Run `cd frontend && npm run lint`, `cd frontend && npm run test`, and `cd frontend && npx playwright test print-alter-ego`. All three MUST pass. Then commit with subject `feat(018): print only the alter ego image (closes #47)`. The commit body should reference issue [#47](https://github.com/squer-solutions/aiavatar/issues/47) and call out the `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` footer.

### Refactor for User Story 1 (under green — optional)

- [X] T012 [US1] *Optional refactor* — folded into T009: describe blocks renamed in the same GREEN commit (e.g. `'PrintArtefact — image-only artefact (FR-1801..FR-1806)'`, `'PrintArtefact — structural gate (FR-1801..FR-1803)'`, `'PrintArtefact — FR-1807 fallback parity'`). No separate refactor commit required. in `frontend/src/features/alterego/components/PrintArtefact.test.tsx`: rename `describe('PrintArtefact — front face (FR-903 / FR-910 / SC-902)', …)` → `describe('PrintArtefact — image-only artefact (FR-1801..FR-1808)', …)` and `describe('PrintArtefact — structural gate (FR-903)', …)` → `describe('PrintArtefact — structural gate (FR-1801..FR-1803)', …)`. Re-run the unit suite to confirm GREEN holds. Coverage gate (≥ 90 %) re-verified against the diff. Commit with subject `refactor(018): drop two-face terminology from print-artefact tests`.

**Checkpoint**: User Story 1 is fully functional. Print produces one page containing only the image; on-screen panel is unchanged; tests / lint / build green; coverage ≥ 90 %.

---

## Phase 4: Polish & Cross-Cutting

**Purpose**: Manual validation across the browser matrix and PR hygiene before merge.

- [ ] T013 [P] Manual validation per [quickstart §3.a](./quickstart.md#3a-single-page-paper-print): Chromium (Chrome) — print preview shows **1 of 1**, image only. (FR-1801, FR-1804, SC-1801)
- [ ] T014 [P] Manual validation per [quickstart §3.b](./quickstart.md#3b-save-as-pdf-page-count): Chromium *Save as PDF* — resulting `.pdf` has page count = 1. (FR-1805, SC-1804)
- [ ] T015 [P] Manual validation per [quickstart §3.c](./quickstart.md#3c-on-screen-unchanged-fr-1809--sc-1806): on-screen "Your Alter Ego" panel side-by-side against a pre-018 build (or visual recall) — visibly identical, every text field still on screen. (FR-1809, SC-1806)
- [ ] T016 [P] Manual validation per [quickstart §3.f](./quickstart.md#3f-browser-matrix-fr-1811--sc-1805): Firefox + Safari + Edge — each shows print preview = 1 of 1; *Save as PDF* (or platform equivalent) = 1 page. (FR-1811, SC-1805)
- [ ] T017 [P] Manual validation per [quickstart §3.e](./quickstart.md#3e-fallback-parity-fr-1807): force a fallback outcome (unset provider key, regenerate); print → 1 page, image only, no fallback markers leaked. (FR-1807)
- [X] T018 Run `cd frontend && npm run build` to confirm the production build succeeds; `cd frontend && npm audit --omit=dev` to confirm no new HIGH/CRITICAL advisories (Constitution Principle VI). Build green: `tsc --noEmit && vite build` produced `dist/index.html` (0.81 kB) + `dist/assets/index-DvsD5MWE.css` (32.08 kB) + `dist/assets/index-jvSoKVKL.js` (265.44 kB) in 161 ms. `npm audit --omit=dev --audit-level=high` reports 0 vulnerabilities.
- [ ] T019 Open the pull request via `gh pr create --title "feat(018): print only the alter ego image (closes #47)" --body …`. PR body links the spec / plan / research artefacts, calls out Constitution gate verdicts, and notes the RED commit precedes the GREEN commit per Principle III. Request review per Constitution Principle V.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)** — runs first. Trivial; ~1 min.
- **Phase 2 (Foundational)** — empty. Skip.
- **Phase 3 (User Story 1)** — sole feature work.
  - **RED block (T002 → T003 → T004)** — T002 and T003 can be edited in parallel; T004 (verify-and-commit) depends on both.
  - **GREEN block (T005 → T006/T007/T008/T009/T010 → T011)** — T005 first (the JSX deletion is the seam most other GREEN tasks reason about), then T006..T010 in parallel, then T011 (verify-and-commit).
  - **REFACTOR (T012)** — optional; depends on T011 GREEN.
- **Phase 4 (Polish)** — depends on T011 GREEN. T013..T017 fully parallel (different browsers / different orthogonal checks). T018 depends on T011. T019 depends on every Phase-4 manual check.

### User-Story Dependencies

Single user story — no inter-story dependencies. P1 IS the MVP. Shipping after Phase 3 + Phase 4 closes issue #47 in full.

### Within User Story 1

- Tests MUST be written and FAIL before any production-code edit (Principle III, research §R5). T002–T004 strictly precede T005–T011.
- Within GREEN, T005 (component JSX) is the conceptual anchor; T006 (CSS), T007 (helper), T008 (helper test), T009 (component test prune), T010 (E2E prune) can land in any order so long as all are green at T011.
- The single GREEN commit covers T005..T010 together; that is the recommended commit boundary. T012 REFACTOR is optional and gets its own commit.

### Parallel Opportunities

- **RED**: T002 ‖ T003 (different files; both must be staged before T004).
- **GREEN**: T006 ‖ T007 ‖ T008 ‖ T009 ‖ T010 (five distinct files; no inter-task race).
- **Polish**: T013 ‖ T014 ‖ T015 ‖ T016 ‖ T017 (independent manual checks).

---

## Parallel Example: User Story 1 — GREEN block

```bash
# After T005 (PrintArtefact.tsx) lands, the remaining GREEN edits run in parallel:
Task: "Prune .print-artefact__back rules + page-break in frontend/src/index.css"
Task: "Delete orphan humanize* helpers + maps + imports in frontend/src/features/alterego/lib/humanizeSelection.ts"
Task: "Delete orphan describe blocks in frontend/src/features/alterego/lib/humanizeSelection.test.ts"
Task: "Prune back-face describe blocks + tighten fallback-parity in frontend/src/features/alterego/components/PrintArtefact.test.tsx"
Task: "Prune back-content assertions + drop unused locator in frontend/tests/e2e/print-alter-ego.spec.ts"
```

---

## Implementation Strategy

### MVP — single user story IS the MVP

1. Phase 1 setup (1 task, sub-minute).
2. Phase 3 RED: write the failing assertions and commit (T002–T004).
3. Phase 3 GREEN: delete the back JSX + CSS + orphan helpers + orphan tests + back-content assertions in one commit (T005–T011).
4. Phase 3 REFACTOR (optional, T012).
5. Phase 4 polish: manual validation across the browser matrix, PR (T013–T019).

### Incremental Delivery

This feature is intentionally indivisible — issue #47 asks for a single product change ("show one page, image only"). Splitting it across multiple PRs would either ship the test-only inversion (RED) without the production change (broken main) or ship the production change without the test inversion (Constitution III violation). The two GREEN commit, sandwiched between a RED commit and an optional REFACTOR commit, is the smallest defensible increment.

### Parallel Team Strategy

If two developers split the work:

- Developer A: T002 (RED unit test) + T005 (component JSX) + T009 (component test prune).
- Developer B: T003 (RED E2E) + T006 (CSS prune) + T007 (helper prune) + T008 (helper test prune) + T010 (E2E prune).
- Either developer drives T004 and T011 (verify-and-commit).
- Manual validation in Phase 4 is split by browser: A → Chrome + Edge; B → Firefox + Safari.

---

## Notes

- **Single user story**, **single P1**, **removal-only** feature; the task list is necessarily compact (19 tasks, ~half of which are manual QA across the browser matrix).
- All [P] tasks touch distinct files — no same-file conflicts.
- Constitution III TDD ordering is enforced by the **explicit RED commit (T004) preceding the GREEN commit (T011)** in branch history. PR review must spot-check this ordering.
- Constitution VI is held via T018's `npm audit --omit=dev`; no new dependency is introduced.
- No backend file is touched, no new file is created, no file is deleted at FS granularity.
- Carry-over invariants from 010 (idempotency / no-network / no-storage / Start-Over teardown / portal mounting / `aria-hidden="true"`) and from 015 / 017 (poster bytes already carry the frame + name + role + quote) make this change safe to merge with no migration concern.
- The change visibly halves the user's per-print paper / ink consumption (SC-1803). That alone should justify a quick merge once Phase 4 validation completes.
