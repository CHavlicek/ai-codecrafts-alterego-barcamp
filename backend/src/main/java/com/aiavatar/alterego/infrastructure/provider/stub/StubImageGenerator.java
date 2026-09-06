package com.aiavatar.alterego.infrastructure.provider.stub;

import org.springframework.context.annotation.Primary;


import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.application.port.ImageGeneratorPort;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Provider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Default-profile image port impl. Always throws
 * {@link GenerationFailure}{@code (NOT_CONFIGURED)} so the orchestrator
 * routes uniformly into the fallback path — no provider-identity branch
 * in {@code AlterEgoUseCase} (Constitution Principle VIII).
 *
 * <p>The actual fallback poster bytes come from
 * {@code FallbackImageGenerator} (which wraps {@code FallbackPosterProvider}),
 * reached via the orchestrator's catch path.
 *
 * <p>{@link #externalRetry()} returns {@code false} so the orchestrator
 * does not waste 5 retry attempts on a provider that is guaranteed to fail.
 */
@Component
@Profile("default")
@Primary
public class StubImageGenerator implements ImageGeneratorPort {

    @Override
    public Provider provider() {
        return Provider.STUB;
    }

    @Override
    public boolean externalRetry() {
        return false;
    }

    @Override
    public PosterImage generate(GeneratedCharacter character,
                                AlterEgoRequest request,
                                PhotoPayload photo) {
        throw new GenerationFailure(FallbackReason.NOT_CONFIGURED,
                "stub provider; no real image provider configured");
    }
}
