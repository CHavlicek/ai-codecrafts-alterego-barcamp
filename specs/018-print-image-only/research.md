# Phase 0 Research: Print only the alter ego image

**Feature**: `018-print-image-only`
**Date**: 2026-05-08
**Spec**: [./spec.md](./spec.md) | **Plan**: [./plan.md](./plan.md)
**Status**: All NEEDS CLARIFICATION resolved (the spec emitted none — the issue text is unambiguous).

This document records the design decisions made before any code change for issue [#47](https://github.com/squer-solutions/aiavatar/issues/47). Each section follows the **Decision / Rationale / Alternatives considered** template required by `/speckit.plan`. Decisions are anchored against the existing 010-print-alter-ego implementation (the print pipeline) and 015 / 017 (the poster bytes that get printed).

---

## R1 — Component-level seam: where to make the change

**Decision**: Modify `frontend/src/features/alterego/components/PrintArtefact.tsx` in place — keep the file, the `createPortal(…, document.body)` mount, the empty-session guard, and the `<section.print-artefact><section.print-artefact__front><img/></section></section>` shape. Delete only the second `<section.print-artefact__back>…</section>` subtree and its supporting imports.

**Rationale**:

- The component is the single seam in the codebase that owns "what gets printed." Every consumer (`AlterEgoPanel.tsx`, the `@media print` CSS rules, the unit tests, the E2E spec) refers to it by class name and import path. Narrowing the file is a strictly smaller diff than relocating or renaming it, and it preserves the existing 010 invariants: the artefact is portalled to `document.body` (so `body > *:not(.print-artefact) { display: none !important }` keeps working), the empty-session guard returns `null` (so the artefact never appears in the DOM during the `idle` / `generating` phases), and the root retains `aria-hidden="true"` (preserving the AT posture from 010 FR-913).
- Replacing `PrintArtefact` with a new file (e.g. `PrintImage.tsx`) would force `AlterEgoPanel.tsx`, the unit test file, and the E2E spec to all chase a renamed import and would invalidate the existing class-name selectors used by the CSS rules — pure churn for no semantic gain.
- Deleting the file outright (and inlining the front into `AlterEgoPanel`) would break the portal seam — the artefact must sit as a top-level child of `<body>` so the `body > *:not(.print-artefact)` print rule isolates it cleanly. Inlining would either re-introduce the portal at the call site (no simpler) or move the print-only `<img>` inside the on-screen panel's layout tree (where `position: absolute` ancestors silently invalidate `page-break-*` rules — see 010 research §R2 for the original incident).

**Alternatives considered**:

- **Rename `PrintArtefact` → `PrintImage`** — rejected: 8+ touchpoints across two test files, the CSS, and the E2E spec, plus the import in `AlterEgoPanel.tsx`, all for a name that's already accurate enough ("artefact" generalises to "the thing we print").
- **Delete the file and inline the `<img>` in `AlterEgoPanel`** — rejected: breaks the portal seam (see above), and `AlterEgoPanel` already has its own layout responsibilities (mount the action row, mount `PosterView`).
- **Split into `PrintArtefactFront` and `PrintArtefactBack`, then drop the back component** — rejected: pure refactor disguised as a deletion. The pre-deletion state is already a single component with two sections; collapsing it to one section needs no further decomposition.

---

## R2 — Fate of the now-orphan `humanize*` helpers

**Decision**: After the back is removed, delete `humanizePose`, `humanizeUniverse`, `humanizeVibe`, `humanizeArtStyle` from `lib/humanizeSelection.ts`, delete their underlying `*_LABELS` lookup maps, drop the `POSE_OPTIONS` / `UNIVERSE_OPTIONS` / `VIBE_OPTIONS` / `ART_STYLE_OPTIONS` imports they rely on, and delete every describe block in `humanizeSelection.test.ts` except the one for `humanizeArchetype`. Keep `humanizeArchetype` and `ARCHETYPE_LABELS` (still consumed at `AlterEgoPanel.tsx:52` for the on-screen role label and at `PrintArtefact.tsx` for the printable image's `alt`).

**Rationale**:

- Confirmed via repo-wide grep: post-deletion call-site count for each of the four helpers drops to **zero** outside of `humanizeSelection.test.ts` itself. They were introduced by 010 specifically for the back-side print view; with the back gone they have no remaining purpose.
- CLAUDE.md authoritative guidance: *"Avoid backwards-compatibility hacks like renaming unused `_vars`, re-exporting types, adding `// removed` comments for removed code, etc. If you are certain that something is unused, you can delete it completely."* Leaving four exported pure functions and ~32 lines of lookup tables behind is the exact pattern that guidance forbids — silent dead code that the next developer must rediscover.
- The deletion is mechanically safe: the `*_OPTIONS` arrays in `frontend/src/features/setup/data/options.ts` are the canonical source of truth and are also consumed by the Setup grids (`PoseGrid`, `UniverseGrid`, `VibeGrid`, `ArtStyleGrid`); only the *imports of those arrays from inside `humanizeSelection.ts`* go away. The arrays themselves stay.
- Coverage gate (Constitution III: ≥ 90 %) is *easier* to hold after deletion, not harder — there is less code to cover, and the surviving `humanizeArchetype` is exhaustively tested.

**Alternatives considered**:

- **Keep the helpers as exports for "future use"** — rejected: speculative retention is the dead-code pattern CLAUDE.md explicitly forbids, and there is no scheduled feature that needs them.
- **Move them to a shared `humanize.ts` registry** — rejected: same problem in a different file. They have no users.
- **Delete the entire `humanizeSelection.ts` file and inline `humanizeArchetype` into `AlterEgoPanel`** — rejected: the file still has one valid consumer (`humanizeArchetype`), and `AlterEgoPanel` and `PrintArtefact` would each end up with their own copy. A 1-helper utility file is fine.

---

## R3 — Alt text on the printable image

**Decision**: Keep `composePosterAlt(firstName, humanizeArchetype(session.archetype), character.quote)` as the `alt` for the printable `<img>`, identical to today. The associated imports stay: `composePosterAlt`, `humanizeArchetype`.

**Rationale**:

- The artefact root carries `aria-hidden="true"`, so screen readers walking the on-screen DOM will not announce the image. However, browser print-preview tooling (Chrome DevTools' "Print Preview" panel, Firefox's print preview, screen-reader print plug-ins) sometimes inspect the DOM independently of `aria-hidden`. Preserving a meaningful `alt` is a defence-in-depth choice that costs nothing and aligns with 017 FR-1714's refined alt format (`"{firstName} · {role}"` baked into the image is mirrored in the alt string).
- The same alt-text composition is already used by the on-screen `PosterView.tsx`, so removing it here would create an asymmetry between the on-screen and printable images of the same poster.
- It doesn't add any code — the call already exists at `PrintArtefact.tsx:65`. We're keeping it, not adding it.

**Alternatives considered**:

- **Use empty `alt=""` on the printable `<img>`** — rejected: weakens AT posture for any tool that bypasses `aria-hidden`; trivially small risk to leave the helpful string in.
- **Delete `composePosterAlt` entirely** — rejected: still used by `PosterView` for the on-screen image. Out of scope.

---

## R4 — CSS cleanup scope

**Decision**: In `frontend/src/index.css`'s `@media print` block, delete:

1. `.print-artefact__front { page-break-after: always; break-after: page; … }` — the page-break properties are the only thing that produced a second page; deleting them is what makes the print one-page.
2. `.print-artefact__back { padding: 2cm 1.8cm; overflow-wrap: anywhere; word-break: break-word; }`
3. `.print-artefact__back-title h2 { font-size: 28pt; margin: 0 0 4pt; }`
4. `.print-artefact__back-subtitle { font-size: 14pt; margin: 0 0 16pt; font-style: italic; }`
5. `.print-artefact__back-fields { font-size: 12pt; line-height: 1.45; }`
6. `.print-artefact__back-fields dt { font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; margin-top: 10pt; }`
7. `.print-artefact__back-fields dd { margin: 0 0 4pt; }`
8. `.print-artefact__back-fields blockquote { margin: 4pt 0 0; padding-left: 12pt; border-left: 2pt solid black; font-style: italic; }`
9. `.print-artefact__back-fields ul { margin: 0; padding-left: 20pt; }`

Keep:

- `.print-artefact { display: none }` (screen-side hide).
- `body > *:not(.print-artefact) { display: none !important }` (print-side isolation).
- `body { margin: 0; background: white; color: black }` (print-side body reset).
- `.print-artefact { display: block; background: white; color: black; font-family: …}` (print-side reveal).
- `.print-artefact__front { padding: 0; margin: 0; text-align: center }` (front-page edge-to-edge box, BUT drop the `page-break-after`/`break-after` declarations).
- `.print-artefact__front img { width: 100%; height: 100vh; object-fit: contain; display: block; margin: 0; padding: 0 }` (image fits the page, preserves aspect ratio).
- `@page { size: A4 portrait; margin: 0 }` (single page, zero CSS-side margin).

**Rationale**:

- The page-break property on `.print-artefact__front` is *the* mechanism that produces the second page in today's build — the front declares a page break after itself, and the back falls into a new page. Removing the page-break declarations makes the front page also the last page; with the back DOM gone there is nothing for a subsequent page to host, so the browser stops at one page.
- The `@page { size: A4 portrait; margin: 0 }` rule is preserved exactly. It applies once, to the single page, with zero CSS-side margin (so a borderless-photo printer can fill the sheet edge-to-edge if the user picks matching paper). The Chromium `@page :first` quirk noted in 010 — "first-page margin leaks to subsequent pages in same-document flows" — is irrelevant here, because there are no subsequent pages.
- The `body > *:not(.print-artefact) { display: none !important }` rule remains essential: the React app root, any GenerationLoading overlay, the `<header>`, the toast region, etc. all live as siblings of `.print-artefact` under `<body>`, and they must continue to be hidden during print. Removing this rule would re-leak the on-screen UI onto the printed page.
- `overflow-wrap: anywhere` and `word-break: break-word` on `.print-artefact__back` were defensive-wrap rules for long taglines / quotes on the back. With the back gone, both are dead.

**Alternatives considered**:

- **Comment out the back rules instead of deleting** — rejected: same dead-code anti-pattern as keeping the helpers (R2). Git history is the source of truth for "what used to be here."
- **Move the front sizing into the `.print-artefact` rule and drop `.print-artefact__front` entirely** — rejected: the `<section.print-artefact__front>` element survives the change (it's the wrapper around the `<img>`), and the existing front-rule split (front padding/margin/text-align separate from the `img`'s sizing) is exactly right for it. Collapsing them adds risk for no gain.

---

## R5 — Test-surface rewrite ordering (RED → GREEN → REFACTOR)

**Decision**: Tests change first. Concretely:

1. **RED phase commit**: Edit `PrintArtefact.test.tsx` and `print-alter-ego.spec.ts` to assert the *desired* behaviour (no back element, exactly one child of `.print-artefact`, single `<section.print-artefact__front>`, no text-rendering elements anywhere in the print artefact, no Pose/Archetype/Universe/Art Style/Vibe/Printed text in the artefact, fallback-parity compares front HTML only). Run `npm run test --filter print-artefact` and `npx playwright test print-alter-ego.spec.ts` — they MUST fail. Commit with subject `test(018): RED — assert print artefact is image-only`.

2. **GREEN phase commit**: Apply the JSX deletion (R1), CSS cleanup (R4), and helper deletion (R2) in one commit. Re-run both test suites — they MUST go green. Commit with subject `feat(018): print only the alter ego image (closes #47)`.

3. **REFACTOR phase commit** (optional, only if it leaves the code clearer): rename the surviving `describe('PrintArtefact — front face …')` block to `describe('PrintArtefact — image-only artefact (FR-1801..FR-1808)')` since the front/back distinction no longer exists. Coverage held at ≥ 90 %.

**Rationale**:

- Direct compliance with Constitution III (NON-NEGOTIABLE TDD: "Write tests; verify they FAIL before committing"). The bug-shape of writing the deletion first and then "updating tests after" is exactly what the RED requirement forbids.
- The two test files both have an existing `__printCalls` seam (Vitest stubs `window.print`; Playwright `addInitScript` shim). We re-aim assertions, we don't change the seam.
- The unit-test file's existing "structural gate" describe block already asserts a structural property (`children[0]` is the front, `children[1]` is the back). Tightening that block to "exactly one child = front, no back exists" is the natural inversion. The negative assertion is explicit (`expect(document.querySelector('.print-artefact__back')).toBeNull()`) so it stands alone if the structural test ever drifts.

**Alternatives considered**:

- **Implementation first, then update tests** — rejected: violates Constitution III. Hard NO.
- **Keep the old back-side describe blocks but modify each assertion individually** — rejected: the blocks describe a face that no longer exists; renaming them doesn't help readability. Deleting them is cleaner.
- **Snapshot-test the print artefact** — rejected: snapshots silently absorb regressions. Explicit assertions on structure (one child, no `.print-artefact__back`) are more robust and document intent.

---

## R6 — `@page` rule

**Decision**: Keep `@page { size: A4 portrait; margin: 0 }` exactly as-is. No changes to `size`, `margin`, or any `@page :first` selector.

**Rationale**:

- The rule already produces zero CSS-side page margin (the front-image's `width: 100%; height: 100vh; object-fit: contain` then fills the page edge-to-edge). With the back gone, there is exactly one page, so no per-page-class differentiation is needed.
- Browsers default the *paper* size to whatever the user picks in the print dialog (A4, Letter, 4×6 photo paper, etc.). Our `size: A4 portrait` is treated as a default suggestion — the user's dialog choice wins. This means the single-page outcome is robust across paper sizes (FR-1811 holds: image fits the printable area on whatever paper is chosen).
- Touching `@page` invites cross-browser surprises (Chromium's `@page :first` cascade leak, WebKit's stricter `margin` handling). Don't touch it.

**Alternatives considered**:

- **Drop the `size` directive and rely entirely on user dialog choice** — rejected: works in Chromium and Firefox; some WebKit print tools fall back to a vendor-specific default that is not always portrait. The directive is harmless.
- **Add `@page :first { … }`** — rejected: there *is* only the first page; a `:first` qualifier is redundant.

---

## R7 — Idempotency / re-print

**Decision**: No change. The print artefact is byte-identical across repeated invocations (carry-over from 010 FR-914 — "Re-press is idempotent: snapshot vs. snapshot of the print DOM is identical"). Spec FR-1808 inherits this behaviour for the image-only output.

**Rationale**:

- The component already returns `null` when there is no result (defence-in-depth guard). When `result` is present, the only dynamic value used to be the `Printed` date; with the back gone there are no time-varying values left in the artefact. The `<img>`'s `src` is a stable `data:` URL, the alt is a pure function of the session, and the wrapping `<section>` carries no state. Bit-exact equality across re-presses now holds *more* strongly than it did with the back.

**Alternatives considered**:

- **Add an explicit `key` to force re-render on each Print press** — rejected: defeats the idempotency property and provides no benefit.

---

## R8 — Backwards compatibility

**Decision**: None required. Public surface (Print button label / position / keyboard behaviour, on-screen panel, image bytes) is unchanged. No data migration, no feature flag, no compatibility shim.

**Rationale**:

- Issue #47 is explicit ("Print action should now be applied to the image only") — the change is intended to be visible and immediate to users on next deploy. No A/B gating is needed.
- No persisted user data is touched (no persistence exists at all in this app — 001 FR-016 / FR-017 / FR-024). There is nothing to migrate.
- Old printed papers / PDFs the user previously produced are unaffected — they are static artefacts of past prints.

**Alternatives considered**:

- **Feature-flag the change behind a setting** — rejected: spec FR-1801..FR-1811 are unconditional; we don't expose user-facing print-format toggles, and gating would only delay value with no risk being mitigated.

---

## R9 — Constitution-gate carry-over from 010

**Decision**: All Constitution gates pass with carry-over reasoning:

- **Principle I (Modern & Secure Stack)**: PASS. Zero additions. Stack identical to 002..017.
- **Principle III (TDD)**: PASS via the RED → GREEN → REFACTOR sequence in R5. Coverage gate held trivially (less code to cover, surviving lines exhaustively exercised). Integration test (Playwright E2E) preserved (Constitution III: "Every feature MUST include at least one integration test exercising the complete user journey end-to-end" — the existing E2E spec fits, just with re-aimed assertions).
- **Principle IV (Resilient HTTP)**: N/A. No HTTP call introduced or removed (FR-1810).
- **Principle V (Feature Branch Workflow)**: PASS. Branch `018-print-image-only` cut from `main` via `create-new-feature.sh`; PR review gates merge.
- **Principle VI (Zero Deprecated Dependencies)**: PASS. Zero additions. Net dependency footprint is unchanged (the helper deletions are within already-shipped local code; no `package.json` change).

**Rationale**:

- 010-print-alter-ego already established the gate posture for this seam. The 018 change is removal-only, so no new gate exposure exists. The only newly-introduced concern is the deletion of orphan helpers (R2), which is a code-hygiene improvement, not a gate concern.

**Alternatives considered**: None — the gates are non-negotiable; the only question is whether we pass or violate, and we pass.

---

## R10 — Composition with 015 (frame) and 017 (in-image text)

**Decision**: Nothing to coordinate. Both 015's poster frame and 017's hero-name / role / quote overlay are baked into the poster image bytes by the backend before the bytes ever reach the browser. The printable `<img src={poster.dataUrl}>` carries them automatically.

**Rationale**:

- 015's frame is composited by `PosterFrameOverlayService` into the response bytes; 017's text overlay is rasterised by the typography pipeline into the same bytes. The frontend treats the `data:` URL as opaque pixels.
- This is what makes "print only the image" a sound product decision in the first place: the user gets the framed, named, captioned poster on a single sheet because every textual element they cared about is already inside the image. The back page was redundant for the keepsake use-case it served.
- No code path in the print pipeline reads `session.firstName`, `session.archetype`, `character.heroTitleLine1`, etc. for *display* purposes any more (the `<img alt>` is the only consumer, and the alt text is for AT only — never rendered ink).

**Alternatives considered**:

- **Wait for 015 / 017 to ship before removing the back** — rejected: they shipped already (`f40ac68`, `8934155`, `b071b7c`, `53a22cb` on `main`). The pre-condition for 018 is satisfied.

---

## Summary

The change is removal-only. Decisions concentrate on **how** to remove cleanly (R1 component-in-place, R2 delete orphan helpers, R4 CSS cleanup) and on **process** (R5 RED→GREEN→REFACTOR ordering, R8 no compat shim, R9 gate carry-over). No new dependency, no new HTTP call, no persistence, no API change, no on-screen change. All NEEDS CLARIFICATION resolved (none were emitted by `/speckit.specify` — the issue text is unambiguous).
