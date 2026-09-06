package com.aiavatar.alterego.unit.infrastructure.provider.fallback;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Provider;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.infrastructure.provider.fallback.FallbackImageGenerator;
import com.aiavatar.alterego.infrastructure.provider.fallback.FallbackPosterProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T013 — FallbackImageGenerator returns a non-null PosterImage for every (Archetype, Universe) pair. */
class FallbackImageGeneratorTest {

    private final FallbackImageGenerator gen = new FallbackImageGenerator(new FallbackPosterProvider());
    private final GeneratedCharacter character = new GeneratedCharacter(
            "SAM", "fallback", "fb", List.of("a", "b", "c"), "q");
    private final PhotoPayload photo = new PhotoPayload(new byte[]{1}, "image/jpeg");

    @Test
    void portIdentitySaysStubAndDoesNotWantExternalRetry() {
        assertEquals(Provider.STUB, gen.provider());
        assertFalse(gen.externalRetry());
    }

    @Test
    void producesNonNullPosterForEveryArchetypeUniversePair() {
        for (Archetype a : Archetype.values()) {
            for (Universe u : Universe.values()) {
                AlterEgoRequest req = new AlterEgoRequest(
                        Pose.HEROIC, a, u, null, ArtStyle.OIL_PAINTING, "Sam", PhotoMode.SINGLE, null);
                PosterImage img = gen.generate(character, req, photo);
                assertNotNull(img);
                assertNotNull(img.bytes());
                assertTrue(img.bytes().length > 0,
                        "Fallback poster for archetype=" + a + " universe=" + u + " must produce non-empty bytes");
            }
        }
    }
}
