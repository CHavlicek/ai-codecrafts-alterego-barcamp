package com.aiavatar.alterego.infrastructure.provider.stub;

import org.springframework.context.annotation.Primary;


import com.aiavatar.alterego.application.port.ImageGeneratorPort;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Provider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Replaces {@link StubImageGenerator} under the {@code force-stub-failure}
 * profile. Always throws — drives the {@code AlterEgoUseCase} fallback
 * path so SC-004 is end-to-end testable. Reports {@link Provider#GEMINI}
 * so the orchestrator's catch-all {@code RuntimeException} branch is the
 * one exercised (no special-case for stub identity — Principle VIII).
 */
@Component
@Profile("force-stub-failure")
@Primary
public class ForceFailureImageGenerator implements ImageGeneratorPort {

    @Override
    public Provider provider() {
        return Provider.GEMINI;
    }

    @Override
    public boolean externalRetry() {
        return false;
    }

    @Override
    public PosterImage generate(GeneratedCharacter character, AlterEgoRequest request, PhotoPayload photo) {
        throw new StubGenerationException(
                "Forced image-generator failure (force-stub-failure profile active)");
    }
}
