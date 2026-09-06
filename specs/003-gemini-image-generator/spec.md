# Feature Specification: Gemini Image Generator — Real AI-Generated Alter-Ego Poster

**Feature Branch**: `003-gemini-image-generator`
**Created**: 2026-04-22
**Status**: Draft
**Input**: GitHub issue [#6 — Gemini Image Generator](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/6). Exact request: *"Google's gemini-3.1-flash-image-preview image generating AI should be wired to the app. Users should have the ability to generate their alter-ego via this AI generator. All the information that user enters on the UI during setup phase should be passed on to the AI-generator for processing. Before sending user's image to image generator's API, the size of the image should be reduced (if excessive). The generated image should be accessible on the Your Alter Ego tab."*

> **Relationship to earlier features.** This feature replaces the stubbed image generator from 001 (001 FR-014, FR-015 — image side only) with a real call to Google's Gemini image-generation API, while inheriting every other behaviour that 001 and 002 set in place (tabbed layout, Setup form, Generate button gating, "Your Alter Ego" tab rendering, resilient-HTTP policy, fallback-on-failure, no-persistence, accessibility). The generated **character text** (title, tagline, superpowers, quote) continues to come from the 001 text stub — this feature changes only the image source. 001 FR-025 ("POC MUST NOT ship real API credentials") is superseded for the image provider specifically; credentials remain backend-only per 001 FR-017.

## Clarifications

### Session 2026-04-22

- Q: How granular should the user-visible fallback notice be when Gemini doesn't run? → A: **Option A** — generic preview framing, no reason disclosed, no provider name. One single user-visible message is used for every fallback trigger (not configured, network error, rate limit, timeout, malformed response). Reason codes still live in backend logs (FR-217) and in the response metadata for operator visibility (see next Q); they are not surfaced to the end user. Canonical copy: *"Showing a preview image — live AI generation isn't available right now."* (Exact wording is a plan-level UX detail; the constraint is "generic, non-blaming, single-variant".)
- Q: How do operators/demo runners confirm whether a given run used real Gemini or the fallback stub? → A: **Option A** — response metadata field + backend log line, no new UI. The backend response carries a discrete `outcome` field with values `"real"` or `"fallback"`, and when `outcome == "fallback"` a `reason` field with one of a fixed set of codes (`"not_configured" | "network_error" | "rate_limited" | "timeout" | "malformed_response" | "safety_refused"`). Operators inspect the response in browser devtools or read a per-run backend log line. No user-visible badge, no debug overlay. This enables SC-205's fault-injection matrix to assert on the metadata field rather than on UI copy.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Generate a real AI-rendered alter-ego from my Setup inputs (Priority: P1)

A user who has completed every required Setup input (photo, pose, role, universe, name; vibe optional) presses **Generate**. The tab auto-switches to "2 Your Alter Ego" (per 002 FR-108), a loading indicator shows that the system is working, and — after the image generation completes — a poster appears whose central image was produced by Google's Gemini image-generation model from the user's photo, pose, role, universe, vibe, and name. The poster image is noticeably different across different Setup combinations: the same user with different roles/universes gets visibly different alter-egos.

**Why this priority**: The whole point of this feature is to replace the stub image with a real AI-generated one. Everything else is supporting scaffolding.

**Independent Test**: Complete the Setup form end-to-end, press Generate, wait for the run to complete, and observe a Gemini-generated image rendered as the poster's central visual on the "Your Alter Ego" tab. Repeating the flow with a *different* role or universe selection MUST produce a visibly different image — confirming that Setup inputs actually reach the image generator rather than being ignored.

**Acceptance Scenarios**:

1. **Given** the user has completed every required Setup input and a configured Gemini API key is available to the backend, **When** they press **Generate**, **Then** the app auto-switches to the "2 Your Alter Ego" tab, renders the loading indicator, and — on a successful run — replaces the loading indicator with a poster whose central image was returned by the Gemini image-generation API for this specific (photo, pose, role, universe, vibe, name) combination.
2. **Given** the user has generated a poster, **When** they press **Start over**, adjust one Setup input (e.g. change role from "Backend Dev" to "AI Engineer"), and press **Generate** again, **Then** the poster's central image is visibly different from the previous run — reflecting the changed input — rather than being identical.
3. **Given** the user has generated a poster, **When** they leave "Your Alter Ego" and return later in the same session (without pressing Start over), **Then** the same Gemini-generated poster is still visible — no re-generation occurs on tab switch (per 002 FR-106).
4. **Given** the user's photo is very large (for example a modern phone camera capture exceeding several megabytes), **When** they press Generate, **Then** the image is reduced to a size acceptable to the Gemini API before the API call is made, so the request succeeds rather than being rejected for exceeding provider payload limits.

---

### User Story 2 — All my Setup inputs shape the generated image (Priority: P1)

The user's pose, role, universe, vibe (when selected), name, and photo all visibly influence the generated image. The user is not merely getting a generic stylised portrait — the role dictates props / attire hints, the universe dictates setting / aesthetic, the pose dictates stance, the vibe (if any) modulates tone, and the photo anchors the face so the generated image recognisably resembles them.

**Why this priority**: Without this, the feature degenerates into "any AI image with a face". The issue explicitly requires *"all the information that user enters on the UI during setup phase should be passed on to the AI-generator for processing"*, so each input must demonstrably contribute.

**Independent Test**: Run a matrix of four Setup combinations that vary only one axis at a time (role, universe, pose, vibe) and confirm pairwise visible differences in the generated images. Repeat with a second user's photo and confirm the generated image now anchors to that user's face rather than the first user's.

**Acceptance Scenarios**:

1. **Given** two Setup runs that differ only in **role** (e.g. Backend Dev vs. Cloud Architect) with all other inputs identical, **When** both runs complete successfully, **Then** the two generated images show role-appropriate differences (props, attire, setting cues) rather than being identical or indistinguishable.
2. **Given** two Setup runs that differ only in **universe** (e.g. Cyberpunk vs. The Office) with all other inputs identical, **When** both runs complete successfully, **Then** the two generated images show universe-appropriate aesthetic differences.
3. **Given** two Setup runs that differ only in **pose** (e.g. Heroic vs. Scholar), **When** both runs complete successfully, **Then** the two generated images show visibly different stances consistent with the selected pose.
4. **Given** two Setup runs with the same role/universe/pose but different users' photos, **When** both runs complete successfully, **Then** each generated image's face reference anchors to its own user's photo (the two outputs are not interchangeable and do not depict the same person).
5. **Given** a Setup run with **vibe** selected and one without (same other inputs), **When** both runs complete successfully, **Then** the vibe's presence modulates the generated image (tone, mood, or accent) — the two outputs are not identical. *(When vibe is absent, the image remains coherent; vibe is a modulator, not a gate.)*

---

### User Story 3 — Graceful degradation when the real provider is unavailable (Priority: P2)

When the Gemini API is unreachable, rate-limited, times out, returns an error, or is simply not configured (no API key on the backend), the user still sees a complete poster — not a broken page. The system transparently falls back to the stub image from 001 and surfaces a non-blocking, recoverable message. The flow never leaves the user stranded on a spinner or an error screen.

**Why this priority**: This is resilience behaviour, explicitly required by 001 FR-018 and by constitutional Principle IV. It is P2 rather than P1 because the baseline already exists from 001; this story is about confirming the new real-provider integration *preserves* that baseline rather than regressing it. For a POC that will also be demoed offline (booth, no-Wi-Fi), it is effectively load-bearing.

**Independent Test**: Simulate three failure modes one at a time — (a) no API key configured, (b) network error reaching Gemini, (c) Gemini returns an explicit error response — and confirm in each case that the user receives a complete fallback poster and a non-blocking error message, with the flow remaining usable (Start over, Generate again both continue to work).

**Acceptance Scenarios**:

1. **Given** the backend is started with **no Gemini API key** configured, **When** the user presses Generate, **Then** the poster renders successfully using the 001 stub image, and a non-blocking indication (e.g. a small notice or banner) tells the user the result is a degraded fallback — the run MUST NOT surface as an error that blocks further use.
2. **Given** the Gemini API call **times out or returns an error** after exhausting the configured retry/back-off policy, **When** the client receives the response, **Then** the poster still renders with the stub image and the same non-blocking fallback notice as scenario 1.
3. **Given** a fallback poster has just been rendered, **When** the user presses Start over and Generates again under healthy conditions, **Then** a real Gemini-generated poster appears on the successful re-run (no persistent error state is carried forward).
4. **Given** any run (successful or fallback), **When** the run completes, **Then** the transition from loading-indicator to poster (or loading-indicator to fallback poster + notice) is announced to assistive technologies via ARIA live regions per 001 FR-022.

---

### Edge Cases

- **Very large photo (e.g. 12-megapixel phone capture, > 10 MB).** The system reduces the photo before sending it to Gemini so the provider accepts the request. The user-visible behaviour (loading indicator, poster, Start over) is unchanged.
- **Very small or already-small photo.** The system sends it as-is (no up-scaling); the image is not needlessly re-encoded in a way that visibly degrades it.
- **Photo intentionally resized below a sensible minimum.** The request still proceeds — the face reference may be lower-fidelity, but the feature does not reject the photo solely because it is small (consistent with 001's "low-quality face still completes with a poster" edge case).
- **Gemini returns an image that appears to violate content safety filters** (e.g. a blank / placeholder response from the provider because it refused the prompt). Treated as a provider error — the system falls back to the stub poster per User Story 3.
- **Gemini returns successfully but with an unexpected payload shape** (unknown content type, truncated bytes, non-image content). Treated as a provider error — fall back to the stub poster.
- **Network latency spike makes the run take much longer than typical.** The loading indicator remains visible; the resilience policy (retries + back-off + jitter) applies; the user's overall wait is bounded by that policy. Per 002 FR-124 a second Generate press does not cancel an in-flight run — it starts a fresh one.
- **User starts a second Generate run while the first is still in flight.** Per 002 FR-124, a new run begins immediately; the previous in-flight run's result is discarded when it arrives. The in-flight Gemini call MAY be allowed to complete-and-discard or be cancelled — either is acceptable so long as no stale image replaces the newer run's output.
- **Backend process is killed / restarted mid-run.** The in-flight request is lost; the user's next Generate starts a fresh run. Consistent with 001's no-persistence posture.
- **User reloads the page after a successful generation.** Per 002 FR-106 inherited from 001 FR-024, no state is persisted: the poster is lost and the user returns to an empty Setup form. This is unchanged.
- **Gemini's rate / quota limit is exhausted for the configured key.** Treated as a provider error — fall back to the stub poster with a non-blocking notice.

## Requirements *(mandatory)*

> **Carried over unchanged**: 001 FR-001/002/003 (photo intake), 001 FR-008 (first name), 001 FR-009/010 (Generate gating — already re-stated by 002 FR-122), 001 FR-011 (loading indicator), 001 FR-012 (poster composition), 001 FR-013 (Start over), 001 FR-016 (no-persistence — extended below), 001 FR-017 (credentials on backend only — extended below), 001 FR-018 (fallback on failure — binds Gemini), 001 FR-019 (resilient-HTTP policy — binds Gemini), 001 FR-020–023 (WCAG 2.1 AA), 001 FR-024 (no persistence), 002 FR-101–124 (tabbed layout, Setup form, Generate placement, auto-switch, tab preservation), 002 FR-130 (`GenerateRequest` wire shape).
>
> **Superseded / replaced by this feature**:
> - **001 FR-014 (image side) and 001 FR-015 (image side)** — the image generation stub is replaced by a real Gemini API call on the backend. The character-text stub behind 001 FR-014/FR-015 remains in place (this feature does not touch text generation).
> - **001 FR-025** — "POC MUST NOT ship real API credentials" is superseded *for the image provider only*. A real Gemini API key is now an accepted backend configuration input. Credentials remain backend-only per 001 FR-017 (the frontend MUST NOT see the key).
> - **002 FR-131** (stubs updated for new enum values) — the image-generation stub must still accept the new enums as a **fallback path**, but is no longer the primary image source.

### Functional Requirements

**Real image generation via Gemini**

- **FR-201**: The backend MUST, on every successful `Generate` run, send the user's photo plus every Setup-form field (pose, archetype/role, universe, first name, and vibe when provided) to Google's Gemini image-generation model and use the returned image as the central image of the generated poster. The text payload passed to Gemini MUST include the user's first name so personalisation can reach the image. *(Exact model identifier and prompt shape are planning-phase decisions driven by FR-210; the spec requires only that all Setup inputs are used.)*
- **FR-202**: The system MUST NOT alter the user-visible generation flow established by 002: pressing Generate still auto-switches to "2 Your Alter Ego", the loading indicator still renders inside that tab, the poster still replaces the loading state, and Start over still returns to an empty Setup form.
- **FR-203**: Each of the following Setup inputs — pose, role, universe, vibe (when present), first name, and the photo — MUST measurably influence the generated image. "Measurably influence" means: changing one input while holding the others constant produces a visibly different image across typical runs (not just a trivial re-draw of the same face). Pose, role, and universe MUST all contribute; vibe, when present, MUST contribute.
- **FR-204**: The generated image MUST use the user's supplied photo as a face reference so the depicted subject recognisably resembles them. The poster MUST NOT render as a generic placeholder image when a valid Gemini response is available.
- **FR-205**: The generated image, once received, MUST be rendered on the "Your Alter Ego" tab in the same slot currently occupied by the 001 stub image, with no additional user-visible chrome introduced by this feature. (The fallback notice from FR-214 is the only permitted user-visible addition.)

**Photo size reduction before upload**

- **FR-206**: Before sending the user's photo to the Gemini API, the system MUST reduce the photo's size (pixel dimensions and/or encoded byte size) when it exceeds a sensible threshold appropriate for the Gemini model, so the API call is not rejected for payload-size reasons. The threshold MUST be chosen to stay safely inside the model's documented limits.
- **FR-207**: The photo-size reduction MUST preserve enough visual fidelity for the Gemini model to anchor the face (reasonable resolution + quality, not a thumbnail). If the photo is already within the threshold, it MUST be sent as-is — the system MUST NOT re-encode unnecessarily.
- **FR-208**: The photo-size reduction MUST NOT change the user's visible Setup-form preview: the reduction is an internal transport-time step, not a change to the user's stored selection. (Consistent with 001 FR-003.)
- **FR-209**: The photo-size reduction MUST be performed on the backend, after the browser uploads the photo and before the backend calls the Gemini API. *(Rationale captured in Assumptions. If a later feature adds client-side reduction for bandwidth reasons, that is a non-regressing addition.)*

**Configuration, credentials, and provider policy**

- **FR-210**: The Gemini model identifier, along with any request parameters needed to reach it (endpoint URL, model version), MUST be a backend configuration input rather than a hard-coded value — so the same codepath can run against the specific model named in the issue (`gemini-3.1-flash-image-preview` per Issue #6) and, if that identifier changes upstream, can be re-pointed without a code change. The default identifier MUST match the one in the issue unless that identifier is not reachable at the provider, in which case a currently-reachable Gemini image-generation model is substituted and the substitution recorded in the planning documents.
- **FR-211**: The Gemini API key MUST be read by the backend from a runtime configuration source (environment variable or equivalent). The frontend MUST NOT transmit, read, or store this key, and the key MUST NOT appear in logs, responses, or error messages surfaced to the browser. (Extends 001 FR-017 to the concrete provider.)
- **FR-212**: When no Gemini API key is configured on the backend at process start, the backend MUST continue to run — every Generate request falls back to the 001 stub image and the response MUST include an indication that a fallback was used, so the frontend can surface the non-blocking fallback notice (FR-214). The backend MUST NOT silently hang, 500-error, or refuse to start merely because Gemini is not configured.
- **FR-213**: Outbound calls from the backend to Gemini MUST follow the project's resilient-HTTP policy (multiple attempts with back-off and randomised jitter) per 001 FR-019 / constitutional Principle IV. The number of attempts and back-off bounds MUST be configurable.
- **FR-214**: When the Gemini call ultimately fails (after retries), times out, is rate-limited, returns a malformed / non-image response, or is not configured (FR-212), the response MUST render a complete fallback poster using the 001 stub image, and the UI MUST surface a non-blocking, recoverable message indicating that the displayed result is a preview / stand-in. The notice MUST be **generic** — a single user-visible message is used for every fallback trigger, it MUST NOT name the provider (Google / Gemini) to the end user, and it MUST NOT disclose the specific reason (not configured, rate limit, timeout, network error, malformed response) to the end user. Reason codes live in backend logs (FR-217) for operator use only. The message MUST be programmatically announced per 001 FR-022 / FR-023 and MUST NOT block subsequent interactions (Start over, Generate-again).

**No-persistence and privacy**

- **FR-215**: The backend MUST hold the user's photo bytes (original + any reduced variant) only in process memory for the duration of a single generation request, consistent with 001 FR-016. Neither the original, the reduced photo, nor the Gemini-generated image MUST be persisted to disk, database, cache, or application logs. Request/response logs MUST continue to redact photo payloads.
- **FR-216**: Sending the photo to Google's Gemini API is inherent to the feature and is accepted as an explicit scope change from 001's "no photo leaves the trusted system" posture. The backend MUST NOT send any user-identifying data to Gemini beyond what is needed for generation — specifically, the photo bytes and the user's first name (which the user typed into the Setup form expecting it to appear on the poster) together with the selection enums. No IP, email, account identifier, or analytics identifier MUST be attached to the Gemini request. *(Assumptions section records that the user is not prompted for renewed consent at generation time because the Setup form already collects these inputs for the explicit purpose of generation.)*
- **FR-217**: Any error payload returned by Gemini (including quota, safety-filter, or authentication errors) MUST be logged on the backend in a form that does not include the photo bytes or the API key. The frontend-visible error MUST be the generic fallback notice (FR-214) — not the raw provider error.

**Operator visibility of provider path**

- **FR-218**: The backend response for every Generate run MUST include structured metadata that lets an operator distinguish a real-provider success from a fallback. Specifically: an `outcome` discriminator with values `"real"` or `"fallback"`, and — when `outcome == "fallback"` — a `reason` field whose value is one of a fixed, enumerated set: `"not_configured"`, `"network_error"`, `"rate_limited"`, `"timeout"`, `"malformed_response"`, `"safety_refused"`. This metadata is for operator / test visibility only: the frontend MUST NOT surface the `outcome` or `reason` values to the end user in the rendered UI (the user-visible notice is the generic single-variant message from FR-214). The metadata MUST be inspectable via browser devtools (i.e. present in the HTTP response body).
- **FR-219**: The backend MUST emit exactly one structured log line per Generate run that records, at minimum, the `outcome` value and — on fallback — the `reason` code, so operators tailing server logs can distinguish a healthy real-provider run from a silent fallback. The log line MUST NOT include the photo bytes, the API key, or the raw Gemini error payload (the latter may be logged separately per FR-217). No new debug UI, URL parameter, or keyboard shortcut is introduced by this feature for this purpose.

### Key Entities

- **GeneratedPoster** — (inherited from 001) the visual output for one poster. Its central image source changes from "stub" to "Gemini response" when available, falling back to "stub" on failure.
- **GenerationOutcome** — a new structured value on the backend response indicating whether the image came from the real provider or from the fallback stub. Shape: an `outcome` discriminator (`"real"` or `"fallback"`) plus, when fallback, a `reason` code drawn from the fixed set defined by FR-218. Consumed by the frontend to drive the (generic, single-variant) fallback notice per FR-214; consumed by operators / automated tests directly (via devtools or parsing) for provider-path visibility per FR-218. Not persisted.
- **PhotoReductionResult** — the transient in-memory reduced photo used for the Gemini call. Lives only for the duration of one request; never persisted. Not surfaced to the user.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-201**: In a **manual walkthrough of 5 consecutive successful runs** with distinct Setup input combinations, **100%** of the rendered posters show a visually unique central image anchored to the supplied photo's face. *(Validates FR-201, FR-203, FR-204.)*
- **SC-202**: In a **role-axis A/B comparison** (same photo, same universe, same pose, two different roles), observers identify the two generated images as different without prior knowledge of the change in **at least 4 of 5 trials**. The same threshold applies to the universe-axis and pose-axis A/B comparisons. *(Validates FR-203.)*
- **SC-203**: With a photo of at least **8 megapixels** (simulating a modern phone capture), the end-to-end run MUST complete successfully and the outbound Gemini API request's payload MUST stay under the provider's documented per-request size limit in **100%** of attempts. *(Validates FR-206, FR-207, FR-209.)*
- **SC-204**: The backend MUST be startable and every Generate request MUST return a renderable poster even when **no Gemini API key is configured**, falling back to the 001 stub. **100%** of such runs render a fallback poster + the non-blocking fallback notice, with no blocked-spinner or error-screen outcome. *(Validates FR-212, FR-214.)*
- **SC-205**: In a **fault-injection test matrix** that includes (a) no API key, (b) forced network error, (c) forced HTTP 5xx from provider, (d) forced timeout, (e) malformed image response, **100%** of runs across the matrix render a fallback poster with the non-blocking fallback notice AND return `outcome: "fallback"` in the response metadata with a `reason` code from the FR-218 enumeration matching the injected fault (`not_configured`, `network_error`, `rate_limited` / covered by forced 5xx or 429, `timeout`, `malformed_response`). All matrix runs remain usable for a subsequent Start-over + Generate-again cycle. *(Validates FR-213, FR-214, FR-218, FR-219, User Story 3.)*
- **SC-206**: Neither the Gemini API key nor the user's photo bytes (original or reduced) appear in **any** backend log line, response body surfaced to the browser, or persisted artefact in **100%** of the runs covered by SC-201 and SC-205. *(Validates FR-211, FR-215, FR-217.)*
- **SC-207**: On a typical broadband connection, median wall-clock time from **Generate press** to **poster rendered** in the successful case is **under 15 seconds**, and 95th-percentile time is **under 30 seconds**, across 20 consecutive runs. *(This is a qualitative target for the POC — the exact bound is held by FR-213's retry/back-off configuration rather than by hard code paths.)*
- **SC-208**: Automated accessibility scan (axe-core or equivalent) across the "Your Alter Ego" tab in each of three states — loading, real-provider success, fallback — reports **zero "serious" or "critical" violations**, and the state transitions loading→success and loading→fallback are announced via ARIA live regions per 001 FR-022. *(Validates FR-205, FR-214.)*

## Assumptions

- The model identifier `gemini-3.1-flash-image-preview` named in Issue #6 is treated as the intended target. If that exact identifier is not reachable at Google's Gemini endpoint at implementation time (previews roll forward), the planning phase selects the closest currently-reachable image-generation model (e.g. the current `gemini-*-flash-image*` preview/GA variant) and records the substitution in `plan.md`. The spec is not invalidated by such a substitution because FR-210 makes the identifier configurable.
- Character **text** generation (title, tagline, superpowers, quote) continues to come from the 001 text stub in this feature. Only the **image** side moves to Gemini. If/when the text side is also wired to a real model, that is a follow-up feature.
- Photo size reduction happens on the **backend**, after upload. Rationale: (a) the backend is where the trusted Gemini call is made and where 001 FR-016 already places photo bytes for the duration of a request; (b) a single consistent reduction pipeline is easier to test than one spread across heterogeneous browsers; (c) if future work wants to add client-side reduction as a bandwidth optimisation, nothing in this feature prevents that — the backend reduction becomes the "final gate" rather than the "only gate".
- Reasonable default reduction threshold: the backend keeps the photo within Gemini's documented per-request and per-image limits (current published limits are in the low-single-digit megabytes and around 1024×1024 for reference-image inputs, but the concrete number is a planning detail tied to the chosen model identifier). The exact number is therefore deferred to `plan.md` and validated by SC-203 rather than pinned here.
- The Gemini API is reached directly from the backend over HTTPS using Google's public endpoint. No intermediate proxy or broker is assumed. (Planning may introduce one if the chosen runtime requires it.)
- Sending the user's photo to Google's Gemini API is an explicit scope change from 001's "no photo leaves the trusted system" posture. The feature accepts Google's standard API-terms data-handling posture for the photo during the call; no additional user-visible consent dialogue is added at generation time because the Setup form is itself the act of consent for generation. A later feature may add a one-time consent toggle if compliance requires; that is out of scope here.
- The generated image's accent colour (per 002 FR-132, derived from archetype+universe by the stub) continues to be decided by the 001/002 stub logic — the Gemini image is the **central visual**, not the entire poster frame. Title, tagline, superpowers, quote, and any accent colouring around the image remain driven by the text stub and 002's poster composition.
- No real text-generation, OCR, or face-detection model is added by this feature. The Gemini request is a single image-generation call; no multi-step orchestration is required by the spec.
- The project's existing resilient-HTTP policy (constitutional Principle IV) is the retry mechanism binding the Gemini call; no new retry framework is introduced.
- Browsers and the frontend build from 002 continue to serve as the client. No new frontend libraries are strictly required by this feature (the image slot already exists); any added libraries are planning-phase decisions.

---

## Dependencies

- **Depends on**: 002-sleek-tabbed-ui (tabbed layout, Setup form, Generate placement/gating, "Your Alter Ego" panel); 001-initial-poc (photo intake, character-text stub, fallback-on-failure, resilient-HTTP policy, no-persistence, accessibility baseline).
- **Depends on**: access to Google's Gemini image-generation model endpoint from the environment where the backend runs (network egress, a valid API key). When unavailable, the fallback path (FR-212/FR-214) keeps the app usable.
- **Upstream constraint**: The Constitution's Technology Standards (Java 21 / Spring Boot 3 on the backend, React 18+ TS strict on the frontend) and Principle-level accessibility + resilience requirements continue to apply.
- **Does not depend on**: any user-account system, any persistence layer, any real text-generation provider (text stays stubbed), or any third-party content-moderation service (handled via FR-214's fallback on provider-side refusals).
