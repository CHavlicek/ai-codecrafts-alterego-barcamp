package com.aiavatar.alterego.infrastructure.provider.fallback;

import com.aiavatar.alterego.application.port.ImageGeneratorPort;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Provider;
import org.springframework.stereotype.Component;

/**
 * The always-on fallback {@link ImageGeneratorPort}, reached by
 * {@code AlterEgoUseCase} when the primary port throws. Thin adapter over
 * the existing {@link FallbackPosterProvider} — preserves the user-visible
 * fallback poster bytes byte-for-byte (FR-2425, SC-004).
 *
 * <p>Active in all profiles. Wired by bean-name {@code "fallbackImageGenerator"}
 * (Spring's default name for this class) so {@code AlterEgoUseCase} can
 * inject it via {@code @Qualifier("fallbackImageGenerator")} without
 * interfering with the {@code @Primary}-disambiguation of the active
 * primary {@link ImageGeneratorPort} bean.
 */
@Component
public class FallbackImageGenerator implements ImageGeneratorPort {

    private final FallbackPosterProvider delegate;

    public FallbackImageGenerator(FallbackPosterProvider delegate) {
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
    public PosterImage generate(GeneratedCharacter character,
                                AlterEgoRequest request,
                                PhotoPayload photo) {
        return delegate.poster(request.archetype(), request.universe());
    }
}
