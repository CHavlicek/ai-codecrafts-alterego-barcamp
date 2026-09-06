# Phase 1 — Data Model

**Date**: 2026-05-15

This is a refactor, not a feature that adds data. The **wire model is invariant** (FR-2413, FR-2425). What changes is *where* each existing type lives in the source tree and which layer it belongs to. This document is the canonical mapping.

## Invariants

- No type's name, package-visible field, or JSON serialisation changes.
- No new persisted entity. The no-persistence posture is preserved (FR-2414).
- The 5 entities introduced by the **spec** are mapped here to concrete code locations after the refactor.

---

## Entity 1 — Generation Provider

**Spec name**: Generation Provider
**Post-refactor home**: `infrastructure/provider/<vendor>/`, implementing the port `application/port/ImageGeneratorPort`.

**Members** (one per vendor; new `FallbackImageGenerator` joins the set):

| Class | Package | Role | Today's location |
|---|---|---|---|
| `GeminiImageGenerator` | `infrastructure.provider.gemini` | real provider | `service.gemini` |
| `FalAiImageGenerator` | `infrastructure.provider.falai` | real provider | `service.falai` |
| `StubImageGenerator` | `infrastructure.provider.stub` | profile-default; throws `GenerationFailure(NOT_CONFIGURED)` post-refactor | `service.stub` |
| `ForceFailureImageGenerator` | `infrastructure.provider.stub` | test-only; unchanged | `service.stub` |
| `FallbackImageGenerator` | `infrastructure.provider.fallback` | **NEW** — thin adapter wrapping the existing `FallbackPosterProvider`; selected by the orchestrator's failure path; satisfies FR-2403 | n/a |

**Contract**: `application/port/ImageGeneratorPort` (see `contracts/image-generator.spi.md`).

**Lifecycle**: All provider beans are profile-gated via `ProviderProfileGuard`. Each is instantiated once at application start; none holds per-request mutable state. The `FallbackImageGenerator` is **always** instantiated regardless of profile, because the orchestrator's failure path always exists.

**Validation rules**: Each provider's outputs must satisfy the `PosterImage` domain invariants (non-empty bytes, declared MIME type, non-negative width/height). The orchestrator does not re-validate; the provider is trusted within its own port contract.

---

## Entity 2 — Overlay Stage

**Spec name**: Overlay Stage
**Post-refactor home**: `application/pipeline/`. Implementations may delegate to `infrastructure/overlay/...`.

**Members**:

| Class | Package | Role | Today's location |
|---|---|---|---|
| `PosterStage` | `application.pipeline` | sealed interface — `permits FrameStage, TextStage` | new |
| `FrameStage` | `application.pipeline` | wraps `infrastructure.overlay.frame.PosterFrameOverlayService` | inline call site |
| `TextStage` | `application.pipeline` | wraps `infrastructure.overlay.text.PosterTextOverlayService` | inline call site |
| `StageContext` | `application.pipeline` | record of `firstName`, `roleOfRecord`, `quote` (the only inputs the text stage needs beyond the image) | n/a |
| `PosterPipeline` | `application.pipeline` | final class holding `List<PosterStage> stages` | n/a |

**Order**: `[FrameStage, TextStage]`. Bean-of-list ordering is given by the Spring `List<PosterStage>` injection respecting `@Order(0)` on `FrameStage` and `@Order(1)` on `TextStage`. Tests assert the list size is 2 and the implementing classes match (FR-2423).

**Lifecycle**: Singletons. Static overlay assets (frame PNG, fonts, logo PNGs) are loaded once at start-up by `PosterFrameAssetLoader` and `PosterTextFonts` (existing `@PostConstruct` beans, unchanged — FR-2410).

**Validation rules**: Each stage takes a non-null `PosterImage` and a non-null `StageContext`, and returns a non-null `PosterImage`. The stage MUST NOT mutate its inputs (defensive copy where needed by JDK Graphics2D APIs).

---

## Entity 3 — Session State (frontend)

**Spec name**: Session State
**Post-refactor home**: **Unchanged** — `frontend/src/features/alterego/state/reducer.ts` + `AlterEgoProvider.tsx` + `context.ts`.

**Shape** (`SessionState` — names below are existing; no field changes):

| Field | Type | Notes |
|---|---|---|
| `firstName` | `string` | trimmed; bounded by `validation/firstName.ts` |
| `photo` | `PhotoSnapshot \| null` | only in-memory Blob + object URL |
| `photoMode` | `'camera' \| 'upload'` | |
| `pose` | `Pose \| null` | hidden in UI per 020; surfaced internally |
| `archetype` | `Archetype \| null` | nullable iff `customRole` non-empty |
| `customRole` | `string` | trimmed ≤ 100 chars |
| `universe` | `Universe \| null` | |
| `vibe` | `Vibe \| null` | hidden in UI per 020 |
| `artStyle` | `ArtStyle \| null` | |
| `recipientEmail` | `string` | for 023 email flow |
| `activeTab` | `'setup' \| 'result'` | per 007 gating |
| `phase` | `'idle' \| 'generating' \| 'succeeded' \| 'failed_with_fallback'` | tab gating signal |
| `generationResult` | `AlterEgoResponse \| null` | the last successful response |
| `emailPhase` | `'idle' \| 'sending' \| 'sent' \| 'failed'` | 023 |
| `generateAutoSwitchNonce` | `number` | 005 entrance animation signal |

**Action union** (closed; remains closed per FR-2418):

`FirstNameChanged | PhotoCaptured | PhotoCleared | PhotoModeChanged | ArchetypeSelected | CustomRoleChanged | UniverseSelected | ArtStyleSelected | SurpriseMePicked | StartOverPressed | GenerateSubmitted | GenerateResolved | GenerateFailed | ActiveTabChanged | EmailRecipientChanged | EmailSubmitted | EmailResolved | EmailFailed`

**Transitions**: documented per-action in `reducer.test.ts`. Transitions are deterministic, atomic, and preserve referential equality where possible (FR-2417, FR-2419).

**Lifecycle**: Created on first mount of `AlterEgoProvider`. Discarded on `StartOverPressed` (reset to initial). **Never persisted** (FR-2414).

---

## Entity 4 — Server State (frontend)

**Spec name**: Server State
**Post-refactor home**: **Unchanged** — `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts` + `useSendAlterEgoEmail.ts`. Documented as the server-state seam in the new `frontend/STATE.md`.

**Shape**: governed by `@tanstack/react-query` v5. Each hook exposes `{ data, error, isPending, isError, isSuccess, mutate, reset }`. No new wrapper type is introduced.

**Lifecycle**: TanStack QueryClient lives in `frontend/src/main.tsx`. Queries are not cached across reloads (no persister wired). Mutations are fire-and-await — no optimistic update because the response is the only source of truth for the result tab.

**Validation rules**: Inputs go through `frontend/src/features/alterego/validation/` before the mutation is invoked. The mutation body is the unmodified output of the existing `alterEgoClient.ts` / `emailClient.ts`.

---

## Entity 5 — Correlation Identifier

**Spec name**: Correlation Identifier
**Post-refactor home**: `boundary/http/CorrelationIdFilter.java` (new) generates one per inbound request, places it in SLF4J `MDC`, and writes it as a response header (`X-Correlation-Id`).

**Shape**: 12-char base32 random string (e.g. `K7P3F2H9TM2X`). One per request. Not persisted.

**Producers**:

| Site | What it produces |
|---|---|
| `CorrelationIdFilter` (new) | generates id if request has no `X-Correlation-Id` header; uses inbound id otherwise |
| `ProblemDetailAdvice` (moved) | reads MDC, sets `properties.correlationId` on the RFC 7807 body |
| structured log MDC | every log line in the request's thread carries the id |
| `AlterEgoResponse.ResponseMeta` | already has a `correlationId` field; the boundary now ensures it matches the MDC value |

**Validation rules**: Inbound `X-Correlation-Id` values longer than 64 chars or containing non-alphanumeric chars are silently replaced with a freshly generated id (security hygiene; never trust client-supplied identifiers for log correlation without bounding).

**Lifecycle**: Lives for the duration of one HTTP request thread. Cleared from MDC by the filter's `finally` block.

---

## Entity 6 — Email Delivery Failure

**Spec name**: implicit in FR-2404 (cross-cutting concerns) and `EmailSenderPort`.
**Post-refactor home**: `application/port/EmailDeliveryFailure.java`.

A typed checked exception thrown by `EmailSenderPort.send(...)`. Replaces the existing `EmailNotConfiguredException` and consolidates the email flow's failure modes.

**Reasons** (closed enum, parallel to `FallbackReason`):

| Reason | When |
|---|---|
| `NOT_CONFIGURED` | `spring.mail.host` is blank / SMTP starter not active (existing behaviour of `EmailNotConfiguredException`) |
| `SMTP_REFUSED` | SMTP server rejects the message (5xx) |
| `MALFORMED_RECIPIENT` | recipient address fails strict re-validation at the port boundary |
| `ATTACHMENT_TOO_LARGE` | composed `MimeMessage` exceeds provider's accepted size |
| `TIMEOUT` | SMTP connect / send exceeds configured timeout |

**Wire mapping** (preserved from feature 023): the boundary controller maps each `EmailDeliveryFailure.reason()` to the same HTTP status + Problem-Detail body that the corresponding code path returns today.

**Lifecycle**: instantiated on the request thread; carries an optional `Throwable` cause; never persisted.

---

## Domain types — verbatim moves (no semantic change)

For completeness, the following existing types move from `model/` to `domain/model/` with **zero semantic changes**. They are not "new entities"; they are pre-existing data structures whose package path is updated. Listed here so tasks.md can be specific.

`AlterEgoRequest`, `AlterEgoResponse`, `AlterEgoUserSelections`, `Archetype`, `ArtStyle`, `FallbackReason`, `GeneratedCharacter`, `PhotoMode`, `PhotoPayload`, `Pose`, `PosterImage`, `Provider`, `SendAlterEgoEmailRequest`, `SendAlterEgoEmailResponse`, `Universe`, `Vibe`, and the `validation/` sub-package.

Wire format (JSON field names, enum string values, validation messages) is unchanged. Contract tests (`contracts/alter-egos.openapi.yaml`, `contracts/email.openapi.yaml`) are the oracle.
