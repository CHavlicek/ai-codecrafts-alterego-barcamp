# Implementation Plan: Print only the alter ego image

**Branch**: `018-print-image-only`
**Date**: 2026-05-08
**Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `specs/018-print-image-only/spec.md` (issue [#47](https://github.com/squer-solutions/aiavatar/issues/47))

## Summary

Drop the second printed page. The Print action keeps its current entry points (the in-app **Print my alter ego** button on the Alter Ego tab and the browser's native shortcut), but the printable artefact is reduced to a single front-page **`<img>`** showing the generated poster. The existing back face — hero title block + definition list of First name / Tagline / Superpowers / Quote / Pose / Archetype / Universe / Art Style / Vibe / Printed date — is **removed in full** from the printable DOM.

Scope:

- **Component** — `PrintArtefact.tsx`: keep the file, the portal, the empty-session guard, and the `<section.print-artefact><section.print-artefact__front><img …/></section></section>` shape. Delete the entire `<section.print-artefact__back>…</section>` subtree. Drop the now-unused imports of `humanizePose` / `humanizeUniverse` / `humanizeVibe` / `humanizeArtStyle`. Keep `composePosterAlt(firstName, role, quote)` and `humanizeArchetype(session.archetype)` for the `<img alt>` — preserves alt-text parity with the on-screen `PosterView` (cf. 017 FR-1714).
- **CSS** — `frontend/src/index.css` `@media print` block: drop every `.print-artefact__back*` rule, drop `.print-artefact__front { page-break-after: always; break-after: page }`, and drop the back-only `overflow-wrap` / `word-break` helpers. Keep `.print-artefact { display: none }` at screen sizes; keep `body > *:not(.print-artefact) { display: none !important }` and `body { margin: 0; … }` at print; keep `.print-artefact__front` sizing (`width: 100%; height: 100vh; object-fit: contain`). Keep `@page { size: A4 portrait; margin: 0 }` exactly as-is — that produces the single-page output with zero CSS-side margin.
- **Helpers** — `lib/humanizeSelection.ts`: delete `humanizePose`, `humanizeUniverse`, `humanizeVibe`, `humanizeArtStyle` and their tests. They have **zero remaining call sites** after the back is removed, so leaving them in place is dead code (CLAUDE.md: "If you are certain that something is unused, you can delete it completely."). Keep `humanizeArchetype` — still consumed by `AlterEgoPanel.tsx` for the on-screen role-label and by `PrintArtefact.tsx` for the alt text. Keep `humanizeSelection.test.ts` for the surviving helper.
- **Tests** — `PrintArtefact.test.tsx`: delete every "back face" / "fallback parity (back-side normalization)" / "long text wrapping safety" describe block; tighten "structural gate" to assert **only one front section, no back section, single child of `.print-artefact`**; add a positive-failure assertion that no element with class `print-artefact__back` exists; restate fallback-parity against the front face only (real vs. fallback render byte-identical front HTML); keep the portal test and the `null` guard test. The existing front-face tests (single `<img>` with the right `src` + `alt`, no text-rendering elements) are preserved unchanged.
- **E2E** — `frontend/tests/e2e/print-alter-ego.spec.ts`: delete every `.print-artefact__back` assertion; replace with `await expect(page.locator('.print-artefact__back')).toHaveCount(0)`; keep the `__printCalls` seam, the front-image presence assertion, the idempotency-of-re-press assertion, the no-network-after-print assertion, the no-storage assertion, and the Start-Over teardown assertion.

The **on-screen** "Your Alter Ego" panel (PosterView, AlterEgoPanel actions row, etc.) is **not touched**. The Print button affordance, label, position, and keyboard behaviour are unchanged. No backend file changes. No new HTTP, no new dependency, no persistence.

Gating is unchanged (carry-over from 010 + 007): the Print button and the print artefact mount only when `phase === 'succeeded' || phase === 'failed_with_fallback'`; both unmount under Start Over and during `generating`.

## Technical Context

**Language/Version**: TypeScript 5.x strict (frontend) — unchanged from 002..017.
**Primary Dependencies**: React 19, Vite 8, Vitest, React Testing Library, Playwright. No new runtime or test dependency. Printing still uses only `window.print()` and CSS `@media print` — both native-browser, no library.
**Storage**: N/A (no persistence — extends 001 FR-016 / FR-017 / FR-024 unchanged; spec FR-1810 restates).
**Testing**: Vitest + RTL (unit/component); Playwright (E2E). The `window.print` seam from 010 (`page.addInitScript` shim that increments `__printCalls`) is reused unchanged — we only re-aim the assertions.
**Target Platform**: Evergreen desktop browsers (Chromium, Firefox, WebKit) — same boundary as 010. Mobile printing remains out of scope.
**Project Type**: Web application (React SPA under `frontend/`).
**Performance Goals**: Time-from-click to native print dialog ≤ 2000 ms (carry-over from 010 SC-901 — held trivially: a single `window.print()` call). The print-only DOM shrinks (one `<section>` instead of two), so first-paint of the print preview is strictly faster than today.
**Constraints**:
- Removal-only — no new feature code; the only code added are inverted/tightened test assertions.
- No new HTTP call (FR-1810 inheritance from 010 FR-908 / SC-904).
- No persistence (FR-1810 inheritance from 001 FR-016 / FR-017 / FR-024 / SC-905).
- No regression to the on-screen panel (FR-1809 / SC-1806).
- Single-page outcome must hold across Chromium / Firefox / WebKit (FR-1811 / SC-1805).
- Unit-test line coverage ≥ 90 % on changed files (Constitution Principle III) — held trivially: every surviving line of `PrintArtefact.tsx` is exercised by the structural / front-face / fallback-parity / null-guard tests.
- Must compose with 005 (entrance animation), 007 (tab gating), 015 (frame), 017 (in-image text). All four are screen-side or image-bytes concerns; printing the image bytes alone preserves them automatically.

**Scale/Scope**: Removal-only feature touching ≈ 5 frontend files:

- `frontend/src/features/alterego/components/PrintArtefact.tsx` — drop the back JSX block + 4 imports.
- `frontend/src/features/alterego/components/PrintArtefact.test.tsx` — drop ~6 describe blocks; tighten 1; add 1 negative assertion (`.print-artefact__back` count = 0).
- `frontend/src/features/alterego/lib/humanizeSelection.ts` — delete 4 of 5 exported functions + their lookup maps + their option-table imports (`POSE_OPTIONS`, `UNIVERSE_OPTIONS`, `VIBE_OPTIONS`, `ART_STYLE_OPTIONS`). Keep `humanizeArchetype` and `ARCHETYPE_LABELS`.
- `frontend/src/features/alterego/lib/humanizeSelection.test.ts` — delete every describe block except the one for `humanizeArchetype`.
- `frontend/src/index.css` — delete ~13 print-media rules under `.print-artefact__back*` and the front's `page-break-after`/`break-after`. Net diff is negative (≈ −50 LOC).
- `frontend/tests/e2e/print-alter-ego.spec.ts` — replace ~8 back-content assertions with one negative assertion and tighten one structural assertion.

No backend file changes. No `options.ts` change. No new dependency. No new contract.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Modern & Secure Technology Stack | PASS | Zero additions. React 19 / TS strict / Vite / Vitest / Playwright unchanged. The change is a code/CSS deletion plus an inverted test assertion. |
| III. Test-First Development (TDD) | PASS | Red → Green → Refactor: (a) **RED** — invert `PrintArtefact.test.tsx` to assert no `.print-artefact__back` exists, the artefact has exactly one child (`.print-artefact__front`), and structural fallback-parity is computed against the front HTML only — these assertions fail against the current two-section component; mirror in the E2E spec; commit and confirm RED locally. (b) **GREEN** — delete the back JSX block, drop the now-unused imports, delete the orphan helpers + their tests, prune the CSS. (c) **REFACTOR** — under green, simplify the surviving describe-block headings to drop the now-misleading "front face / back face" split into a single "image-only artefact (FR-1801..FR-1808)" group. Coverage gate ≥ 90 % holds — the surviving component file shrinks more than its test surface, and every surviving line is exercised. The integration test (E2E) is preserved (Constitution III: "Every feature MUST include at least one integration test"). |
| IV. Resilient HTTP Communication | N/A | No HTTP call introduced or removed. The print pipeline is local only (FR-1810). |
| V. Feature Branch Workflow | PASS | Branch `018-print-image-only` cut from `main` via `.specify/scripts/bash/create-new-feature.sh`. Merge gated on PR review (Constitution V). |
| VI. Zero Deprecated Dependencies | PASS | Zero additions → zero `npm audit` delta. Net dependency footprint shrinks: removing the `humanizePose`/`Universe`/`Vibe`/`ArtStyle` helpers retires their imports of the corresponding `*_OPTIONS` tables from `setup/data/options.ts` (the option tables themselves stay — they are also consumed by the Setup grids). |

**Gate verdict**: **PASS**. No unjustified violations. No deviations.

## Project Structure

### Documentation (this feature)

```text
specs/018-print-image-only/
├── plan.md              # This file
├── research.md          # Phase 0: R1 component-level seam (keep PrintArtefact.tsx, drop back); R2 fate of orphan humanize* helpers; R3 alt-text on the printable image; R4 CSS cleanup scope; R5 test-surface rewrite (RED→GREEN); R6 @page rule unchanged; R7 idempotency / re-print carry-over; R8 backwards compatibility; R9 constitution-gate carry-over from 010; R10 composition with 015 / 017 (image bytes carry frame + text)
├── data-model.md        # Phase 1: trimmed PrintArtefactView (front-only); screen-vs-print visibility contract after back removal; no session/reducer shape change
├── quickstart.md        # Phase 1: how to run locally + manual validation across Chrome / Firefox / Safari / Edge (paper print + Save-as-PDF) + on-screen-unchanged side-by-side check
├── checklists/
│   └── requirements.md  # Spec-quality checklist (from /speckit.specify) — all items pass
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

No `contracts/` directory is produced for this feature — there is no API surface, no OpenAPI change, no new wire type. The 002 / 003 / 006 / 014 / 016 OpenAPI surface is byte-identical.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── features/alterego/
│   │   ├── components/
│   │   │   ├── PrintArtefact.tsx                    # MODIFIED: delete the <section.print-artefact__back>{…}</section> subtree;
│   │   │   │                                         # delete imports of humanizePose / humanizeUniverse / humanizeVibe / humanizeArtStyle;
│   │   │   │                                         # keep portal, null guard, composePosterAlt, humanizeArchetype, the front <section><img/></section>;
│   │   │   │                                         # keep aria-hidden="true" on the root.
│   │   │   ├── PrintArtefact.test.tsx               # MODIFIED: delete "back face" + "fallback parity (back)" + "long text wrapping" describe blocks;
│   │   │   │                                         # tighten "structural gate" → root has exactly one child = .print-artefact__front;
│   │   │   │                                         # add explicit negative assertion: querySelectorAll('.print-artefact__back').length === 0;
│   │   │   │                                         # restate fallback-parity against front HTML only.
│   │   │   ├── PrintButton.tsx                      # UNCHANGED.
│   │   │   ├── PrintButton.test.tsx                 # UNCHANGED.
│   │   │   ├── AlterEgoPanel.tsx                    # UNCHANGED — still mounts <PrintArtefact session={session} /> in the poster phase.
│   │   │   ├── AlterEgoPanel.test.tsx               # UNCHANGED — still asserts artefact mount/unmount tracking phase.
│   │   │   └── PosterView.tsx                       # UNCHANGED — on-screen poster image is unaffected.
│   │   └── lib/
│   │       ├── humanizeSelection.ts                 # MODIFIED: keep humanizeArchetype + ARCHETYPE_LABELS (and the imported ARCHETYPE_OPTIONS);
│   │       │                                         # delete humanizePose, humanizeUniverse, humanizeVibe, humanizeArtStyle and their *_LABELS maps;
│   │       │                                         # delete the no-longer-used POSE_OPTIONS / UNIVERSE_OPTIONS / VIBE_OPTIONS / ART_STYLE_OPTIONS imports.
│   │       ├── humanizeSelection.test.ts            # MODIFIED: delete every describe block except humanizeArchetype's; the option-table-driven round-trip stays for that one helper.
│   │       └── composePosterAlt.ts                  # UNCHANGED — still consumed by PosterView and PrintArtefact for alt text.
│   └── index.css                                    # MODIFIED: delete .print-artefact__back / __back-title h2 / __back-subtitle / __back-fields / __back-fields dt|dd|blockquote|ul rules (≈ 13 declarations);
│                                                     # delete .print-artefact__front { page-break-after: always; break-after: page };
│                                                     # keep .print-artefact { display: none } (screen);
│                                                     # keep body > *:not(.print-artefact) { display: none !important } + body { margin: 0; …; color: black } (print);
│                                                     # keep .print-artefact__front padding/margin/text-align and .print-artefact__front img sizing rules;
│                                                     # keep @page { size: A4 portrait; margin: 0 }.
└── tests/
    └── e2e/
        └── print-alter-ego.spec.ts                  # MODIFIED: delete back-content text assertions (Tagline / Pose / Cloud Architect / Star Wars / Pixel Art / Heroic / Printed / kebab non-leak);
                                                      # replace with `await expect(page.locator('.print-artefact__back')).toHaveCount(0)`;
                                                      # keep the print-call counter, the front image presence, the idempotency-of-re-press, the no-network-after-print, the storage-zero, and the Start-Over teardown.
```

**Structure Decision**: Single web-app layout under the existing `frontend/` tree, identical to 010..017. The feature follows the same seams established by 010 — `PrintArtefact` is the only component that knows about the printable artefact's shape, and CSS `@media print` is the only place that knows about screen-vs-print visibility. Both seams are preserved; we narrow them (delete the back subtree, delete the back rules) rather than relocate them. No new file is created, and no file is deleted (the orphan-helper deletions happen *inside* `humanizeSelection.ts`, not at the file level).

The decision to **delete** the orphan `humanize*` helpers rather than leaving them in place is deliberate (research.md §R2): they were introduced specifically for the back-side print view, they have zero remaining call sites once the back is gone, and CLAUDE.md's repo-wide guidance forbids backwards-compatibility shims for unused code. Leaving them would create silent dead code that the next developer would have to re-discover.

## Complexity Tracking

*No Constitution violations to justify.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *(none)* | — | — |

## Phase Outputs

- **Phase 0 Research**: [./research.md](./research.md) — decisions on (R1) component-level seam (modify `PrintArtefact.tsx` rather than delete the file or split a new component), (R2) fate of the now-orphan `humanizePose` / `humanizeUniverse` / `humanizeVibe` / `humanizeArtStyle` helpers (delete), (R3) alt-text on the printable image (keep `composePosterAlt`-driven alt for AT-aware print preview tooling), (R4) CSS cleanup scope (delete back rules + page-break; keep front sizing + `@page`), (R5) test-surface rewrite ordering (RED first by inverting assertions; GREEN by deleting JSX/CSS/helpers; REFACTOR by simplifying describe-block names), (R6) `@page` rule unchanged (`A4 portrait`, `margin: 0`), (R7) idempotency / re-print carry-over (no change — already idempotent under 010 FR-914), (R8) backwards compatibility (none required — Print button signature, on-screen panel, and image bytes are all unchanged), (R9) constitution-gate carry-over from 010 (Principle IV remains N/A; coverage gate held trivially), (R10) composition with 015 (frame) and 017 (in-image text) — both are pixel-baked into the poster bytes, so printing the image alone preserves them.
- **Phase 1 Design**: [./data-model.md](./data-model.md) — documents the trimmed `PrintArtefactView` shape (`{ posterDataUrl, mediaType, widthPx, heightPx, altText }`) and the screen-vs-print visibility contract after the back is removed. [./quickstart.md](./quickstart.md) — how to run the feature locally, the manual validation flow (single-page paper print, single-page Save-as-PDF, on-screen unchanged side-by-side, with/without Vibe, fallback parity, browser-matrix checklist).

No `contracts/` produced; `update-agent-context.sh` adds the 018-print-image-only note to `CLAUDE.md` (no new tech — stack identical to 010..017).
