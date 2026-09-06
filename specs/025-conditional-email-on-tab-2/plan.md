# Implementation Plan: Conditional Email Input on the Alter Ego Tab

**Branch**: `025-conditional-email-on-tab-2` | **Date**: 2026-05-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/025-conditional-email-on-tab-2/spec.md`

## Summary

Render an inline email input on the Alter Ego tab (above the action-button row) **only when the session's captured email is not send-ready** — i.e. blank OR fails the existing format validator. The field writes to the same `session.email` slot the Setup-tab input writes to, so there is exactly one captured-email value per session. Once the captured email becomes send-ready, the inline field disappears and the existing `SendAsEmailButton` enables — exactly as it does today when the user comes from a successful Setup-tab fill.

**Technical approach**: pure frontend, no reducer change, one new selector (`isSendableEmail`), one new component (`InlineEmailFallback`) reused inside `AlterEgoPanel`, three tests-first additions (selector unit, component unit, panel integration). The send pipeline (`SendAsEmailButton` → `useSendAlterEgoEmail` → `POST /api/v1/alter-egos/email`) is unchanged. The backend, the validator, the wire contract, and the no-persistence posture are all untouched.

## Technical Context

**Language/Version**: TypeScript 5.7 (strict). Frontend only — no backend code change.
**Primary Dependencies**: React 19, Vite 8, Vitest + React Testing Library (existing). **No new runtime, test, or build dependency.**
**Storage**: N/A — inherits 001 FR-016 / FR-017 / FR-024 no-persistence posture. The captured email continues to live in browser session state only.
**Testing**: Vitest + RTL for the new selector + component + panel-integration test. The existing Playwright E2E suite already covers the send pipeline end-to-end; no new E2E needed.
**Target Platform**: Modern evergreen browsers (Chromium / Firefox / Safari) on desktop + mobile, same as 023.
**Project Type**: Web application (`frontend/` + `backend/`); this feature touches `frontend/` only.
**Performance Goals**: Selector + visibility flip MUST be perceptually instant — same keystroke (FR-2508/2509, SC-2503). No new render path; the field mounts/unmounts inside the existing actions region.
**Constraints**: No backend change. No new dependency. No wire-format change. No persistence. Captured-email state remains a single field on `AlterEgoSession`.
**Scale/Scope**: ≈ 1 new component (~50 LOC), 1 new selector (~10 LOC), 1 wire-up edit in `AlterEgoPanel.tsx`, 3 new test files. Surface area is the same order as 023's frontend slice.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Justification |
|---|---|---|
| I. Modern & Secure Technology Stack | ✅ | No new dep; reuses React 19 / Vite 8 / Vitest already pinned. |
| III. Test-First Development (TDD) | ✅ | Three new failing-first tests planned (selector, component, panel integration); ≥ 90% line coverage is preserved because the new code is small and fully covered by these tiers. Feature is FE-only so the backend integration-test clause is not engaged. |
| IV. Resilient HTTP Communication | ✅ | This feature adds no new HTTP call. The send path (POST `/api/v1/alter-egos/email`) is unchanged — its retry + RFC 7807 + alert-feedback policy from 023 is inherited verbatim. |
| V. Feature Branch Workflow | ✅ | Branch `025-conditional-email-on-tab-2` cut from `main` per `create-new-feature.sh` numbering. |
| VI. Zero Deprecated Dependencies | ✅ | No new dependency to audit. |
| VII. Layer Convention (backend) | N/A | Frontend-only feature — backend layers untouched. |
| VIII. Provider Seam | N/A | No provider code touched. |
| IX. Test Pyramid | ✅ | Backend pyramid untouched. Frontend additions are unit-tier (Vitest) — they exercise pure selector logic and a thin component, both isolated. No new Spring slice, no new contract. |

No gate violations. **Complexity Tracking** section is empty (see below).

## Project Structure

### Documentation (this feature)

```text
specs/025-conditional-email-on-tab-2/
├── plan.md              # This file
├── spec.md              # /speckit.specify output
├── research.md          # Phase 0 — design decisions
├── data-model.md        # Phase 1 — session-state delta (none) + visibility predicate
├── contracts/           # Phase 1 — frontend UI contract only (no HTTP)
│   └── ui-contract.md
├── quickstart.md        # Phase 1 — how to validate the feature locally
├── checklists/
│   └── requirements.md  # /speckit.specify validation checklist
└── tasks.md             # Phase 2 — /speckit.tasks output (NOT created here)
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── features/alterego/
│   │   ├── components/
│   │   │   ├── AlterEgoPanel.tsx          # MODIFIED — render <InlineEmailFallback> above actions row when !sendable
│   │   │   ├── AlterEgoPanel.test.tsx     # MODIFIED — three new cases for the visibility rule
│   │   │   ├── InlineEmailFallback.tsx    # NEW — thin wrapper that reuses EmailInput + dispatches EmailChanged
│   │   │   ├── InlineEmailFallback.test.tsx  # NEW — render + onChange + a11y
│   │   │   └── EmailInput.tsx             # UNCHANGED — reused as-is
│   │   ├── state/
│   │   │   ├── selectors.ts               # MODIFIED — adds isSendableEmail(state)
│   │   │   ├── selectors.test.ts          # MODIFIED — adds isSendableEmail cases
│   │   │   └── reducer.ts                 # UNCHANGED — no new action, no new field
│   │   ├── hooks/
│   │   │   └── useSendAlterEgoEmail.ts    # UNCHANGED — send pipeline reused
│   │   ├── validation/
│   │   │   └── email.ts                   # UNCHANGED — single validation rule
│   │   └── services/
│   │       └── emailClient.ts             # UNCHANGED — wire contract preserved
│   └── ...
└── ...

backend/                                   # UNCHANGED — no Java / Spring file touched
```

**Structure Decision**: The repository already follows the web-application split (`frontend/` + `backend/`) established by 002 and reaffirmed by 024. This feature is a pure frontend refinement on top of 023's captured-email state — no new top-level directory, no module boundary crossed.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified.

*(empty — no gate violations)*
