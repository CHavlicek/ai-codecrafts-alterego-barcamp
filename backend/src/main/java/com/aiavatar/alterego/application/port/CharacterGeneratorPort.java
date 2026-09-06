package com.aiavatar.alterego.application.port;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.Provider;

/**
 * The single application-facing seam for character (text) generation
 * (Principle VIII). Today implemented by the Gemini character adapter
 * (real) and the stub character generator (no-op in default profile).
 *
 * <p>Failure contract mirrors {@link ImageGeneratorPort}: a
 * {@link GenerationFailure} is the only typed exception across the port.
 * Other unchecked exceptions are mapped to {@code MALFORMED_RESPONSE}
 * by the orchestrator.
 */
public interface CharacterGeneratorPort {

    GeneratedCharacter generate(AlterEgoRequest request);

    /**
     * Stable provider identifier for telemetry. See
     * {@link ImageGeneratorPort#provider()}.
     */
    Provider provider();

    default boolean externalRetry() {
        return true;
    }
}
