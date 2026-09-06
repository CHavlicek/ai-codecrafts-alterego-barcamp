package com.aiavatar.alterego.unit.infrastructure.provider.stub;

import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Provider;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.infrastructure.provider.stub.StubImageGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T014 — Post-024 behaviour: {@link StubImageGenerator#generate} always
 * throws {@link GenerationFailure} with reason {@code NOT_CONFIGURED}.
 * The orchestrator's normal catch path produces the
 * {@code outcome=fallback / reason=not_configured} response — no
 * provider-identity short-circuit required (Principle VIII / FR-2402).
 */
class StubImageGeneratorTest {

    @Test
    void portIdentitySaysStubAndDoesNotWantExternalRetry() {
        StubImageGenerator gen = new StubImageGenerator();
        assertEquals(Provider.STUB, gen.provider());
        assertFalse(gen.externalRetry(), "stub must not waste 5 retry attempts on a guaranteed failure");
    }

    @Test
    void generateAlwaysThrowsGenerationFailureWithNotConfigured() {
        StubImageGenerator gen = new StubImageGenerator();
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Sam", PhotoMode.SINGLE, null);
        GeneratedCharacter character = new GeneratedCharacter("SAM", "x", "y", List.of("a", "b", "c"), "q");
        PhotoPayload photo = new PhotoPayload(new byte[]{1}, "image/jpeg");

        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> gen.generate(character, req, photo));
        assertEquals(FallbackReason.NOT_CONFIGURED, ex.reason());
    }
}
