package com.aiavatar.alterego.application.port;

import com.aiavatar.alterego.domain.model.FallbackReason;

import java.util.Objects;

/**
 * Typed runtime exception raised by an {@link ImageGeneratorPort} or
 * {@link CharacterGeneratorPort} implementation to signal that the real
 * provider path could not complete, carrying a typed {@link FallbackReason}
 * so the orchestrator can populate
 * {@code AlterEgoResponse.ResponseMeta.reason} on the fallback response
 * (003 FR-218).
 *
 * <p>Extends {@link RuntimeException} so Spring's
 * {@link org.springframework.retry.support.RetryTemplate} retries it under
 * the default policy (no custom retry policy needed for the provider
 * boundary).
 */
public class GenerationFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final FallbackReason reason;

    public GenerationFailure(FallbackReason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public GenerationFailure(FallbackReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public FallbackReason reason() {
        return reason;
    }
}
