# Internal SPI — `ImageGeneratorPort`

**Layer**: `application.port`
**Implemented by**: any class under `infrastructure.provider.*` that wires itself as the active `ImageGeneratorPort` bean for a given Spring profile, plus the always-on `FallbackImageGenerator`.

This is the seam US1 stabilises and FR-2402 / FR-2403 enforce. The orchestrator (`AlterEgoUseCase`) depends on this port and only this port for image generation.

## Java signature

```java
package com.aiavatar.alterego.application.port;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Provider;

public interface ImageGeneratorPort {

    /**
     * Produce a raw, un-overlaid poster image for the given character and request.
     *
     * @throws GenerationFailure with a typed {@link FallbackReason} when the
     *         provider cannot fulfil the request (network failure, malformed
     *         response, unsupported configuration, etc.). Any other
     *         {@link RuntimeException} thrown by an implementation is mapped
     *         to {@link FallbackReason#MALFORMED_RESPONSE} by the orchestrator.
     */
    PosterImage generate(GeneratedCharacter character,
                         AlterEgoRequest request,
                         PhotoPayload photo) throws GenerationFailure;

    /**
     * Stable identifier of this provider for telemetry. One of the values in
     * the {@link Provider} enum. The orchestrator MUST NOT pattern-match on
     * this string to choose execution paths — its only consumer is the
     * structured-log {@code provider=} field.
     */
    Provider provider();

    /**
     * Opt-in flag: if {@code true}, the orchestrator wraps each call in
     * {@code RetryTemplate}. If {@code false}, the provider has its own
     * retry policy and the orchestrator must not double-retry.
     *
     * <p>Today: Gemini and fal.ai return {@code true} (rely on shared
     * RetryTemplate); the legacy {@code wantsExternalRetry()} method on
     * the previous interface is renamed to this for clarity.
     */
    boolean externalRetry();
}
```

## Failure contract

`GenerationFailure` (moved from `service/`) is the **only** checked exception a provider may throw across the port. It carries:

- a typed `FallbackReason` (closed enum: `NOT_CONFIGURED`, `MALFORMED_RESPONSE`, `RATE_LIMITED`, `TIMEOUT`, `UNAUTHORIZED`, …),
- an optional cause,
- the `attemptedProvider` identifier (set by the throwing implementation, not the orchestrator).

Any other `RuntimeException` that escapes the port is caught by the orchestrator and mapped to `FallbackReason.MALFORMED_RESPONSE` (preserving 003's existing semantics).

## Orchestrator usage contract

The orchestrator (`application/AlterEgoUseCase`) uses **exactly one** sequence:

```java
try {
    raw = retry.execute(primary::generate);  // or primary.generate(...) if !externalRetry
} catch (GenerationFailure | RuntimeException e) {
    raw = fallback.generate(character, request, photo);  // FallbackImageGenerator
}
poster = posterPipeline.apply(raw, ctx);
```

**Invariants enforced by ArchUnit**:

1. `application/AlterEgoUseCase` MUST NOT import any class from `infrastructure.provider.*`. It depends on `ImageGeneratorPort` only.
2. `application/AlterEgoUseCase` MUST NOT contain a string-comparison branch against `Provider` values or the string `"stub"` / `"gemini"` / `"falai"`.
3. Every class under `infrastructure/provider/{gemini,falai,stub,fallback}/` implementing `ImageGeneratorPort` MUST be a `@Component` or `@Service` with profile-gated wiring.

## Parallel: `CharacterGeneratorPort`

Same shape, same failure contract. Lives under `application.port`. Implemented by `infrastructure/provider/{gemini,stub}/`. Today there is no fal.ai character generator; that is unchanged.

## Parallel: `EmailSenderPort`

Single method `send(SendAlterEgoEmailRequest, AlterEgoResponse) → SendAlterEgoEmailResponse`, throws `EmailDeliveryFailure` with one of the five closed reasons defined in `data-model.md` Entity 6. Implementations: one — `infrastructure/email/JavaMailEmailSender` (the existing `AlterEgoEmailService`, moved). The boundary controller depends on the port; no `application` layer call site exists for the email flow today (the controller invokes the service directly), and the refactor introduces a `SendAlterEgoEmailUseCase` in `application/` for symmetry with `AlterEgoUseCase`. This is in scope for US2 (layered map).
