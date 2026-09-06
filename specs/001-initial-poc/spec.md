# Feature Specification: Initial POC — AI Alter Ego Generator

**Feature Branch**: `001-initial-poc`
**Created**: 2026-04-21
**Status**: Draft
**Input**: User description: "Initial setup. Take the design mock in root of the project and create an initial POC based on it. The third-party connections should be stubbed."

## Clarifications

### Session 2026-04-21

- Q: Universe vs. archetype — keep both as independent pickers, collapse to one, or hierarchical? → A: **Option A** — keep both as independent pickers. Archetype drives the hero title / tone; universe drives the visual setting. The stub samples combinations rather than covering all permutations.
- Q: Does the photo ever leave the browser? Where do stubs live? → A: **Option B** — the photo is POSTed to the Java backend over HTTP as part of a single generation request; the backend holds photo bytes only in process memory for the duration of that request, runs the stubbed character + image generators server-side, and returns the result. No persistence anywhere. Future real-provider credentials will live only on the backend.
- Q: Gender picker — binary, expanded, skip, or replace with pose/stance? → A: **Option D** — replace the gender picker with a "pose / stance" picker (e.g. heroic / stealthy / mystical / scholar). The selection drives the visual composition of the generated poster; no gender input is collected. Decouples the UX from biometric signalling while keeping the image-generation pipeline driven by explicit user intent.
- Q: Canonical term — "alter ego" vs. "avatar"? → A: **Option A** — "Alter Ego" is the canonical product/feature term and will be used across user-facing copy, entity names (`AlterEgoSession`), frontend/backend class naming (`AlterEgoController`, `AlterEgoService`, `AlterEgoRequest/Response`), and API paths (e.g. `/api/v1/alter-egos`). "AI-Avatar" remains the project/repo umbrella name only. **Follow-up flagged**: the constitution's product-description prose uses "avatar" — a PATCH amendment to align that sentence with this decision is recommended before `/speckit.plan`.
- Q: Accessibility baseline for the POC? → A: **Option B** — WCAG 2.1 Level AA. Keyboard-navigable controls with visible focus indicators, semantic HTML, AA contrast ratios, ARIA live-region announcements for loading/success/error state transitions, non-colour-only labels for the colour picker, and programmatically associated error messages. Testable via automated scans (axe-core or equivalent) plus a keyboard-only walkthrough. Enhanced items (prefers-reduced-motion, focus trap/restore, high-contrast-mode styles, fully keyboard-operable camera capture) are explicitly deferred to a follow-up feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Generate a personalised AI Alter Ego from a photo and a few selections (Priority: P1)

A participant at a booth or landing page takes (or uploads) a photo of themselves, picks a handful of identity attributes (their first name, an archetype, an accent colour, a fictional universe, and a pose / stance), and triggers generation. The system responds with a themed "alter ego" poster — a hero-style image built around the participant's actual face, plus a generated title, tagline, three superpowers, and a short quote. The participant can view the poster on screen and reset to start over.

**Why this priority**: This is the entire product in one flow. Without it there is no POC. Every other story (sharing, printing, persistence, real AI generation) is additive.

**Independent Test**: Open the app, complete the capture-and-select flow end-to-end, and verify a poster renders with the participant's face, the selected colour accent, the selected archetype theme, and generated text that is plausibly derived from the inputs. Success if a full poster appears within the target wait time and the reset button returns the user to an empty form.

**Acceptance Scenarios**:

1. **Given** the user is on the entry screen with no prior input, **When** they capture a photo via camera, pick a pose, colour, archetype, and universe, enter a first name, and press "Generate", **Then** the system shows a loading indicator and renders a poster with the user's face, the selected colour accent, a two-line hero title starting with the first name in caps, a tagline, three superpowers, and a quote.
2. **Given** the user is on the entry screen, **When** they upload a JPEG/PNG photo from disk instead of using the camera, **Then** the photo is accepted and the generation flow proceeds identically to the camera path.
3. **Given** the user has just generated a poster, **When** they press "Start over", **Then** the form clears, the generated poster is discarded, and they can generate a new alter ego from fresh inputs.
4. **Given** the user has selected only some of the required inputs, **When** they look at the Generate control, **Then** it is clearly disabled, and the user can see (or discover) which inputs are still missing.
5. **Given** third-party generation services are configured to fail or are unavailable, **When** the user presses Generate, **Then** the UI renders a recognisable fallback poster (canned content) rather than a blank or broken page, and a non-blocking error message acknowledges the degraded state.

---

### Edge Cases

- Camera access is denied or unavailable (no hardware, blocked permission) → the upload path remains usable and is obviously offered.
- Uploaded file is not a supported image type or exceeds a reasonable size limit → the system rejects the file with a clear, recoverable message; no generation is attempted.
- User's photo contains no detectable face, multiple faces, or a very low-quality face → the generated image may be low-fidelity; the flow still completes and produces a poster using the fallback/stubbed pipeline.
- Network is offline or the third-party stubs are slow → loading steps are shown; on timeout, the resilient fallback (canned poster) is used so the user never sees a blank screen.
- User navigates away mid-generation and returns → no partial state is resurrected; the user starts over from an empty form (no persistence in the POC).
- User tries to regenerate with identical inputs → a new generation runs; the POC does not cache or deduplicate.
- Browser does not support the camera API → the camera button is hidden or disabled, with the upload path presented as the primary option.

## Requirements *(mandatory)*

### Functional Requirements

**Photo intake**
- **FR-001**: The system MUST let a user supply a photo of themselves via either (a) an in-browser camera capture or (b) a file upload.
- **FR-002**: The system MUST let the user retake or replace the supplied photo before generation.
- **FR-003**: The system MUST show a preview of the currently selected photo before generation is triggered.

**Attribute selection**
- **FR-004**: The system MUST let the user pick exactly one pose / stance from a fixed set of at least four visually distinguishable options (e.g. heroic, stealthy, mystical, scholar). This selection drives the composition of the generated poster; no gender input is collected.
- **FR-005**: The system MUST let the user pick exactly one accent colour from a fixed palette (at least four distinguishable options).
- **FR-006**: The system MUST let the user pick exactly one archetype from a fixed set of developer-themed archetypes (at least six options).
- **FR-007**: The system MUST let the user pick exactly one fictional universe from a fixed set (at least four options, drawn from well-known fictional settings).
- **FR-008**: The system MUST let the user enter a first name, which will appear on the generated poster.

**Generation trigger and gating**
- **FR-009**: The system MUST disable the Generate action until all required inputs are present (photo, pose, colour, archetype, universe, first name).
- **FR-010**: The system MUST clearly indicate, when Generate is disabled, which inputs are still missing.

**Generation flow and output**
- **FR-011**: The system MUST, on Generate, transition to a generation state with a loading indicator that communicates the current step of the pipeline.
- **FR-012**: The system MUST render, on success, a poster that includes: the user's face as the central subject, an accent colour matching the selection, a two-line hero title whose first line is the user-supplied first name in uppercase, a short tagline, three short superpowers, and a one-line quote.
- **FR-013**: The system MUST let the user return to an empty form via a "Start over" action, discarding the current poster.

**Request boundary and stubbing (POC constraint)**
- **FR-014**: The frontend MUST POST the user's photo bytes together with all selection metadata (pose, colour, archetype, universe, first name) to the backend over HTTP as a single generation request, and receive back the generated character text plus a poster image (or image URL) in the response. The stubbed integrations for character generation and image generation MUST live on the backend, not in the browser. The end-to-end flow MUST be fully functional with no real third-party service reachable.
- **FR-015**: The stubbed integrations MUST return plausible, differentiated content for at least a few distinct archetype/colour/universe combinations so that demonstrations feel realistic rather than identical every time.
- **FR-016**: The backend MUST hold uploaded photo bytes only in process memory for the duration of a single generation request. Photos MUST NOT be persisted to disk, database, cache, or application logs. Request/response logs MUST redact photo payloads.
- **FR-017**: API credentials for any future real third-party provider MUST live on the backend only. The frontend MUST NOT transmit, store, or read such credentials.

**Resilience**
- **FR-018**: If a stubbed or real third-party call fails, the system MUST render a visibly degraded but complete poster (canned fallback content) and surface a non-blocking, recoverable error message.
- **FR-019**: HTTP calls between frontend and backend, and from the backend to third-party stubs, MUST follow the project's resilient-HTTP policy (multiple attempts with back-off and randomised jitter before falling back) per constitutional Principle IV.

**Accessibility**
- **FR-020**: The UI MUST meet WCAG 2.1 Level AA success criteria. Every interactive control (photo capture/upload, pose / colour / archetype / universe pickers, first-name field, Generate, Start over) MUST be reachable and operable by keyboard alone with a visible focus indicator. All text MUST meet AA contrast ratios.
- **FR-021**: The colour picker MUST expose a machine- and screen-reader-readable text label for each swatch in addition to the colour itself; selection MUST NOT rely on colour perception alone.
- **FR-022**: Generation-state changes (loading → success, loading → error/fallback) MUST be announced to assistive technologies via ARIA live regions so non-sighted users receive the same state transitions sighted users see.
- **FR-023**: Error messages (photo rejected, required input missing, generation failed with fallback) MUST be programmatically associated with the control they describe and be announced politely.

**Scope exclusions (POC)**
- **FR-024**: The POC MUST NOT require user accounts, login, or persistent storage of user photos, selections, or generated posters between sessions — neither in the browser nor on the backend.
- **FR-025**: The POC MUST NOT ship real API credentials for third-party services; all such calls remain stubbed until a later feature wires real providers in.
- **FR-026**: Accessibility features beyond WCAG 2.1 AA — specifically `prefers-reduced-motion` honouring, focus trap/restore during async generation, high-contrast-mode styling, and fully keyboard-operable in-browser camera capture — are explicitly deferred to a follow-up feature.

### Key Entities

- **AlterEgoSession** — the in-memory state of one user generating one alter ego. Lives in the browser for the user's interaction; its photo bytes + selection metadata briefly cross to the backend during a single generation request, where they are held in process memory only for that request's duration. Attributes: selected photo bytes, pose, accent colour, archetype, universe, first name, and the current generation state (idle, generating, succeeded, failed-with-fallback). No persistence anywhere in the POC.
- **GeneratedCharacter** — the textual output for one poster: hero title (two lines), tagline, three superpowers, quote. Derived from the selected archetype + universe + colour + photo. In the POC this is produced by the stub.
- **GeneratedPoster** — the visual output: an image URL or bytes for the poster, paired with the associated GeneratedCharacter. In the POC the image comes from the stub (a placeholder or a pre-rendered asset keyed by inputs).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: End-to-end time from pressing Generate to a visible poster, using the stubs, is under **3 seconds** on a typical laptop browser for 95% of runs.
- **SC-002**: A new user can complete the full flow (photo → all selections → generated poster) in **under 60 seconds** on first attempt without reading instructions.
- **SC-003**: 100% of Generate attempts with all required inputs present produce a complete poster — either the primary stubbed output or the canned fallback — with zero blank or half-rendered states.
- **SC-004**: When third-party stubs are forced to fail, the user still sees a complete poster within **5 seconds**, plus a recoverable error message.
- **SC-005**: The generated poster contains every required element (face, accent colour, two-line title, tagline, three superpowers, quote) in **100%** of successful runs across a test matrix covering every pose × every colour × every archetype × every universe combination.
- **SC-006**: The "Start over" action returns the UI to an empty form in **under 200 ms** with no residual state from the prior run.
- **SC-007**: An automated accessibility scan (axe-core or equivalent) across the entry screen, loading state, success state, and error/fallback state reports **zero "serious" or "critical" violations**. A keyboard-only walkthrough of the full flow (photo intake → all selections → Generate → view poster → Start over) succeeds end-to-end without mouse input.

## Assumptions

- The POC targets a modern desktop or tablet browser with camera and file-upload support. Mobile-first layout polish is out of scope for this feature.
- The design reference used as input is a throwaway artefact and will be removed after this POC is scaffolded. It is not a dependency of the running app.
- The design language observed in the reference (dark theme, pink/purple accents, serif display headings, hero-poster card layout) is carried forward as a visual starting point, but is not frozen — subsequent features may adjust it.
- Localisation is English-only for the POC.
- No analytics, telemetry, or A/B machinery is in scope; those are follow-up features.
- The list of fictional universes offered to the user (Star Wars, Harry Potter, Indiana Jones, etc.) is chosen pragmatically for the POC; licensing and legal review for public deployment are explicitly deferred to a later feature.
- The third-party providers that will eventually power generation (a text-generation model for character description and an image-generation model capable of face-reference) are not named in this spec. Provider selection is a planning-phase decision.
