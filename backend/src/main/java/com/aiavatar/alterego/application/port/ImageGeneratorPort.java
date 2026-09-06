package com.aiavatar.alterego.application.port;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Provider;

/**
 * The single application-facing seam for image generation (Principle VIII).
 *
 * <p>All real providers (Gemini, fal.ai) and the always-on
 * {@code FallbackImageGenerator} implement this port. The application
 * orchestrator depends on this interface and only this interface; it MUST
 * NOT branch on {@link Provider} identity.
 *
 * @see com.aiavatar.alterego.application.port.GenerationFailure
 * @see <a href="../../../../../../../../specs/024-architecture-refactor/contracts/image-generator.spi.md">image-generator.spi.md</a>
 */
public interface ImageGeneratorPort {

    /**
     * Produce a raw, un-overlaid poster image for the given character and request.
     *
     * @throws GenerationFailure with a typed {@link com.aiavatar.alterego.domain.model.FallbackReason}
     *         when the provider cannot fulfil the request (network failure,
     *         malformed response, unsupported configuration). Any other
     *         {@link RuntimeException} that escapes this method is mapped
     *         to {@code FallbackReason.MALFORMED_RESPONSE} by the orchestrator.
     */
    PosterImage generate(GeneratedCharacter character,
                         AlterEgoRequest request,
                         PhotoPayload photo);

    /**
     * Stable identifier of this provider for telemetry. The orchestrator
     * MUST NOT pattern-match on this value to choose execution paths — its
     * only consumer is the structured-log {@code provider=} field.
     */
    Provider provider();

    /**
     * Opt-in flag: if {@code true}, the orchestrator wraps each call in
     * {@code RetryTemplate}. If {@code false}, the provider has its own
     * retry policy and the orchestrator must not double-retry.
     *
     * <p>Renamed from the legacy {@code wantsExternalRetry()} on the
     * predecessor {@code ImageGenerator} interface for clarity.
     */
    default boolean externalRetry() {
        return true;
    }
}
