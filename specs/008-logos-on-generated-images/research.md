# Research — 008: Logo Branding Overlay on Generated Alter-Ego Images

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Date**: 2026-04-24

Every decision below resolves one "pinned at planning time" item left open by the spec or one "best-practice" question raised by the plan. Each entry follows the mandated format: **Decision / Rationale / Alternatives considered**.

---

## R-701 — Graphics stack: JDK built-in `Graphics2D` + `ImageIO`

- **Decision**: Use `javax.imageio.ImageIO` to read the logo PNGs and the generated poster bytes into `BufferedImage` instances, and use `java.awt.Graphics2D` (with `VALUE_INTERPOLATION_BICUBIC` + `VALUE_RENDER_QUALITY` + `VALUE_ANTIALIAS_ON`) to composite. Re-encode with `ImageIO.write(...)` preserving the input MIME type.
- **Rationale**:
  - Spec Assumption ("No new third-party image library is required") explicitly calls for the built-in stack.
  - Constitution Principle I (no new deps / no new CVEs) — zero new Maven coordinates.
  - The runtime (`eclipse-temurin:21-jre-alpine`) ships a fully featured headless JDK; `Graphics2D` on in-memory `BufferedImage` needs no X server. `FallbackPosterProvider` and `StubImageGenerator` already do exactly this in the same container.
  - Bicubic interpolation produces clean downscales from the ~900-px logo source to a ~135–180-px target (15% of 900–1200-px poster widths).
- **Alternatives considered**:
  - **Scrimage (`com.sksamuel.scrimage`)** — a Scala/Java image library with a higher-level DSL. Rejected: adds a new dependency (~3 MB + transitive), doesn't materially improve pixel quality for this use case, and brings CVE audit surface.
  - **ImgScalr (`org.imgscalr:imgscalr-lib`)** — thin Graphics2D wrapper. Rejected: maintenance is stagnant (last release 2011), no quality advantage over a correctly configured raw `Graphics2D`.
  - **Thumbnailator (`net.coobird:thumbnailator`)** — popular resize library. Rejected: overkill for one fixed resize + paste, adds dependency surface.

## R-702 — Resource location & naming

- **Decision**: Place the two logo PNGs at `backend/src/main/resources/branding/squer-logo.png` and `backend/src/main/resources/branding/codecrafts-logo.png`. Load via Spring's `ClassPathResource("branding/squer-logo.png")` (no leading slash; canonical for Spring).
- **Rationale**:
  - `src/main/resources/` is Spring Boot's standard classpath root — the files end up in the JAR at `BOOT-INF/classes/branding/…`, addressable by the built-in `ClassPathResource`.
  - Renaming to lowercase-hyphenated names (`squer-logo.png`, `codecrafts-logo.png`) matches the existing naming in `resources/stubs/characters.json` and is shell-safe across Linux / macOS / Windows.
  - The issue's attached filenames (`SQUER-Logo-without-font-white.png`, `codecrafts.png`) live at the repo root as uploaded; implementation copies their bytes into `backend/src/main/resources/branding/` under the canonical names. The uploaded files at the repo root are kept as-is (don't delete them — they were explicitly attached by the issue author).
- **Alternatives considered**:
  - **Serve as `static/` assets** — rejected: these are not user-facing HTTP endpoints, exposing them as `GET /branding/*.png` would invite direct use by the frontend (violating FR-705 which says the overlay happens backend-side).
  - **External volume mount** — rejected: FR-706 requires the assets be "bundled with the backend", explicitly excluding runtime fetch. A volume mount adds a deploy-time failure mode (missing mount) that FR-711 would then have to absorb at runtime.

## R-703 — Overlay geometry: widths, margins, gap

- **Decision**: Pinned values (all percentages of the poster's **width** — even on portrait posters, this keeps the logos readable and the SC-705 ranges satisfied):
  - **Logo rendered width**: `max(48, round(0.15 × posterWidthPx))` — 15% of image width, floored at 48 px for unusually small images.
  - **Top margin**: `round(0.04 × posterWidthPx)` — 4% of image width.
  - **Right margin**: `round(0.04 × posterWidthPx)` — 4% of image width.
  - **Inter-logo gap**: `round(0.02 × posterWidthPx)` — 2% of image width.
  - Each logo's height is derived by preserving its source aspect ratio: `logoHeight = round(logoWidth × sourceHeight / sourceWidth)`. Equal rendered width is the hard requirement (FR-703).
- **Rationale**:
  - All four values fall exactly in the middle of the spec's SC-705 ranges, the safest prior in the absence of a stakeholder signal pushing toward one edge.
  - Using image **width** (not shorter side, not diagonal) for all proportions keeps the geometry deterministic across aspect ratios and avoids landscape-vs-portrait branching logic. On a square 1024×1024 poster the two logos occupy ≤ 15% × (source_h/source_w × 2 + gap) of the height — comfortably in the top-right without crowding the subject in a typical full-body / half-body composition.
  - The 48-px floor handles the hypothetical "tiny poster" edge case — the ~512-px floor the Gemini provider emits in practice stays well clear of the floor (0.15 × 512 = 76.8 px).
- **Alternatives considered**:
  - **Absolute pixels** (e.g. "logo is always 150 px wide") — rejected by the 2026-04-23 clarification session (sizing is a proportion, not an absolute).
  - **Percent of shorter side** — rejected: on landscape posters this would make the logos disproportionately small; keeping a single axis ("percent of width") is simpler and still meets the spec range.
  - **Independent left/right/top margin values** — rejected as over-design for a symmetric top-right anchor.

## R-704 — Asset lifecycle: load-once, cache-forever

- **Decision**: Decode each logo PNG into a `BufferedImage` exactly once — on bean initialization of `LogoAssetLoader` (Spring `@PostConstruct` or constructor). Hold both `BufferedImage` instances as final fields; expose read-only accessors. Never re-read or re-decode on a Generate request.
- **Rationale**:
  - FR-707 + SC-704 require read-once-use-many. `@PostConstruct` guarantees the load happens exactly once per JVM, at a predictable moment, with errors bubbling through bean-context startup (failing fast in dev) or — if we prefer fail-soft at boot — logged and tolerated per FR-711.
  - The decoded `BufferedImage` is effectively immutable once compositing only reads from it (`Graphics2D.drawImage(source, ...)` never mutates `source`), so sharing across requests is thread-safe without synchronization.
- **Alternatives considered**:
  - **Lazy-load on first request** — equivalent behaviourally, but hides slow startup behind the first user request (worse experience at the booth). Rejected.
  - **Fail backend startup when a logo is missing** — rejected by FR-711's fail-soft posture. If a logo is absent or corrupt at startup, log a `WARN` and disable branding for that asset; Generate still returns (un-branded).

## R-705 — Fail-soft policy: per-request vs. per-boot

- **Decision**: Apply FR-711 at **two** layers:
  1. **Boot-time**: if `LogoAssetLoader` cannot load one or both logos, log a `WARN` per missing/corrupt asset and record the loader as "degraded". The application starts normally.
  2. **Per-request**: if `BrandingOverlayService.apply(PosterImage)` throws a `RuntimeException` anywhere in the composite path (decode failure on the input bytes, `Graphics2D` exception, `ImageIO.write` returning `false`), the method catches it, logs a `WARN` carrying the correlation ID and the throwable, and returns the **input** `PosterImage` unchanged.
- **Rationale**:
  - FR-711 / SC-706 require that users never see a broken poster because of a branding bug. A two-layer guard handles both "never-brandable" (asset missing at boot) and "branding broke for this one request" (decode error on an unusual output).
  - Catching `RuntimeException` (not `Throwable`) preserves the project's convention of letting `Error` (OOM, StackOverflow) bubble up — branding-code OOM is not something we want to swallow silently.
  - The correlation ID lets operators grep `generation.completed` and correlate a WARN with the outcome line.
- **Alternatives considered**:
  - **Single-layer (per-request only)** — rejected: if a logo is missing we would retry the failing decode on every request instead of degrading once at boot.
  - **Fail the whole run to 5xx on branding failure** — rejected: contradicts FR-711 and the project's "always return a poster" posture.
  - **Silent catch with no log** — rejected: SC-706 requires a `WARN`-level log per affected run.

## R-706 — MIME-type preservation

- **Decision**: Use the input `PosterImage.mediaType()` to pick the `ImageIO` writer: `"png"` for `"image/png"`, `"jpeg"` for `"image/jpeg"`. Use `BufferedImage.TYPE_INT_ARGB` as the working canvas **only** when the input is PNG (to preserve transparency if any). For JPEG output, use `TYPE_INT_RGB` (JPEG has no alpha) and flatten the composite onto an opaque canvas before writing; the logos' transparency is preserved as the underlying image showing through — but alpha is eliminated at write time. This satisfies FR-710 without introducing an alpha-in-JPEG footgun.
- **Rationale**:
  - FR-710 is a hard constraint: PNG stays PNG, JPEG stays JPEG. The existing frontend's data-URL prefix depends on this.
  - `BufferedImage.TYPE_INT_ARGB` is the only built-in type that preserves alpha through `drawImage`; using it for PNG is the low-risk default. For JPEG we must strip alpha because `ImageIO`'s default JPEG writer will produce a cyan-tinted or black-mask-artefacted image if given an ARGB source.
- **Alternatives considered**:
  - **Always convert to PNG** — rejected: violates FR-710; also increases payload size for JPEG-source Gemini outputs.
  - **`BufferedImage.getType()` on the decoded input** — rejected: Gemini sometimes returns types that round-trip poorly; it's safer to explicitly choose the canvas type based on MIME.

## R-707 — Testing strategy

- **Decision**: Four test layers, TDD order:
  1. **`LogoAssetLoaderTest`** (unit): classpath-load happy path, missing-resource fail-soft, bytes equal on repeated calls (cache).
  2. **`BrandingOverlayServiceTest`** (unit): synthesize a small `BufferedImage`, run `apply(PosterImage)`, then decode the result and **pixel-assert** the top-right corner contains the expected logo colours and the top-left contains the original content. Also cover: MIME preservation (PNG→PNG, JPEG→JPEG), dimension preservation, fail-soft on mutated-bytes decode failure.
  3. **`BrandingOverlayIT`** (`@SpringBootTest`): End-to-end on the real Spring context with the stub profile — hit `POST /api/alter-ego`, decode the base64 from `poster.dataUrl`, and verify pixel regions + dimensions + MIME, matching `AlterEgoResponse` wire shape. Also a negative path: with one logo resource temporarily overridden to a corrupt byte array (bean test override), assert the response still has a usable data URL and the log captures the WARN.
  4. **Extending `GenerateAlterEgoGeminiIT`**: assert the outbound WireMock-captured Gemini request body contains no `squer`, no `codecrafts`, no `logo`, no base64 blob larger than the input photo — guards FR-704 / SC-702.
- **Rationale**:
  - Constitution Principle III: unit + integration, failing tests first, `@SpringBootTest` with real wiring for integration, ≥90% line coverage. Pixel assertion on a small synthetic image keeps IT fast.
  - The negative-path IT is the only way to validate FR-711 under real Spring wiring — without it, coverage is a lie.
- **Alternatives considered**:
  - **Visual golden-image diff** — rejected: flaky across JVM minor versions (font rendering drift); pixel sampling at the four corners is more robust.
  - **Only unit tests** — rejected: would not catch Spring wiring bugs (bean not injected, logo resource path typo only hit at classpath scan).

## R-708 — No contracts/ artefact

- **Decision**: No new file under `contracts/` — the public HTTP contract is unchanged.
- **Rationale**: The overlay mutates only the bytes inside `AlterEgoResponse.Poster.dataUrl`; the response schema (field names, types, `mediaType` enumeration, `widthPx`/`heightPx` semantics) is unchanged from 003.
- **Alternatives considered**:
  - A "visual-contract" snapshot — the closest thing to a contract here. Rejected: folded into `quickstart.md` as a manual verification step instead, because automated visual contracts are test-layer concerns (see R-707).
