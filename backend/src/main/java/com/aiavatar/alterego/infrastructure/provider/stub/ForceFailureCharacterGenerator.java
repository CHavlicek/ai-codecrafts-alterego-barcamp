package com.aiavatar.alterego.infrastructure.provider.stub;

import org.springframework.context.annotation.Primary;


import com.aiavatar.alterego.application.port.CharacterGeneratorPort;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.Provider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Replaces {@link StubCharacterGenerator} under the {@code force-stub-failure}
 * profile. Always throws — drives the {@code AlterEgoUseCase} fallback path
 * so SC-004 is end-to-end testable from a Playwright run.
 */
@Component
@Profile("force-stub-failure")
@Primary
public class ForceFailureCharacterGenerator implements CharacterGeneratorPort {

    @Override
    public Provider provider() {
        return Provider.GEMINI;
    }

    @Override
    public boolean externalRetry() {
        return false;
    }

    @Override
    public GeneratedCharacter generate(AlterEgoRequest request) {
        throw new StubGenerationException(
                "Forced character-generator failure (force-stub-failure profile active)");
    }
}
