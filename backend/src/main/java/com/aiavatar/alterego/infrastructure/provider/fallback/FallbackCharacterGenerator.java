package com.aiavatar.alterego.infrastructure.provider.fallback;

import com.aiavatar.alterego.application.port.CharacterGeneratorPort;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.Provider;
import org.springframework.stereotype.Component;

/**
 * Always-on fallback {@link CharacterGeneratorPort} reached by
 * {@code AlterEgoUseCase} when image generation fails (the orchestrator
 * replaces the primary character with this one — preserves 003's
 * {@code AlterEgoService.handleFallback} behaviour byte-for-byte).
 *
 * <p>Wired by bean-name {@code "fallbackCharacterGenerator"}.
 */
@Component
public class FallbackCharacterGenerator implements CharacterGeneratorPort {

    private final FallbackPosterProvider delegate;

    public FallbackCharacterGenerator(FallbackPosterProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Provider provider() {
        return Provider.STUB;
    }

    @Override
    public boolean externalRetry() {
        return false;
    }

    @Override
    public GeneratedCharacter generate(AlterEgoRequest request) {
        return delegate.character(request.firstName());
    }
}
