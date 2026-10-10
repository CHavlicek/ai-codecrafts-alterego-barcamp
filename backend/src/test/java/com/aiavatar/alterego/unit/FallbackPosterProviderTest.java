package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.policy.AccentResolver;
import com.aiavatar.alterego.domain.policy.AccentTone;
import com.aiavatar.alterego.infrastructure.provider.fallback.FallbackPosterProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FallbackPosterProvider} returns a complete, schema-valid character +
 * poster pair regardless of input. Drives FR-018's "always return a poster"
 * guarantee at unit-test level.
 *
 * <p>002 delta: {@code poster(...)} now takes {@code (archetype, universe)}
 * and derives its accent via {@link AccentResolver} so identical inputs
 * yield the same accent colour whether the primary stub succeeded or the
 * fallback fired (FR-132).
 */
class FallbackPosterProviderTest {

    private final FallbackPosterProvider provider = new FallbackPosterProvider();

    @Test
    void characterUsesUppercasedFirstNameWhenProvided() {
        GeneratedCharacter c = provider.character("paula");
        assertEquals("PAULA", c.heroTitleLine1());
        assertEquals("The Resilient", c.heroTitleLine2());
        assertEquals("DEGRADED, NOT DEFEATED.", c.tagline());
        assertEquals(3, c.superpowers().size());
    }

    @Test
    void characterTrimsFirstNameBeforeUppercasing() {
        GeneratedCharacter c = provider.character("  paula  ");
        assertEquals("PAULA", c.heroTitleLine1());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void characterFallsBackToHeroPlaceholderWhenFirstNameIsAbsent(String firstName) {
        GeneratedCharacter c = provider.character(firstName);
        assertEquals("HERO", c.heroTitleLine1());
    }

    @Test
    void posterReturnsValidPng() {
        PosterImage poster = provider.poster(Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS);
        assertEquals("image/png", poster.mediaType());
        // Fallback emits at 3:4 portrait — matching the frame asset's
        // transparent inner cutout (PosterFrameOverlayService.TARGET_ASPECT)
        // so the framing step never letterboxes on the fallback path.
        assertEquals(1024, poster.widthPx());
        assertEquals(1365, poster.heightPx());
        assertTrue(poster.bytes().length > 0);
        // PNG magic
        assertEquals((byte) 0x89, poster.bytes()[0]);
        assertEquals((byte) 0x50, poster.bytes()[1]);
        assertEquals((byte) 0x4E, poster.bytes()[2]);
        assertEquals((byte) 0x47, poster.bytes()[3]);
    }

    @Test
    void posterRatioIsThreeToFourPortrait() {
        PosterImage poster = provider.poster(Archetype.DATA_ANALYST, Universe.RETRO_SYNTHWAVE);
        double ratio = poster.heightPx() / (double) poster.widthPx();
        assertTrue(Math.abs(ratio - (4.0 / 3.0)) <= 0.015,
                "fallback poster MUST be 3:4 portrait within ±1%, got " + ratio);
    }

    @Test
    void accentDerivationIsConsistentWithPrimaryPath() {
        // The fallback poster uses AccentResolver.deriveAccent for its border
        // colour; assert here that the resolver itself returns something in
        // the six-value palette for every (archetype, universe) pair.
        for (Archetype archetype : Archetype.values()) {
            for (Universe universe : Universe.values()) {
                AccentTone accent = AccentResolver.deriveAccent(archetype, universe);
                assertNotNull(accent, () -> "Null accent for " + archetype + "×" + universe);
                assertTrue(Stream.of(AccentTone.palette()).anyMatch(p -> p.equals(accent)),
                        () -> "Accent " + accent + " is not in the six-value palette");
            }
        }
    }

    @Test
    void accentDerivationIsDeterministic() {
        AccentTone first = AccentResolver.deriveAccent(Archetype.OPERATIONS_MANAGER, Universe.RETRO_SYNTHWAVE);
        AccentTone second = AccentResolver.deriveAccent(Archetype.OPERATIONS_MANAGER, Universe.RETRO_SYNTHWAVE);
        assertEquals(first, second);
    }
}
