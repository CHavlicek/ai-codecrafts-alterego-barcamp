package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.infrastructure.provider.stub.StubCharacterGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StubCharacterGenerator} contract tests. 002 delta: the hash drops
 * {@code colour} and picks up the optional {@code vibe}. Fixture keys are
 * the new archetype wire values.
 */
class StubCharacterGeneratorTest {

    private static StubCharacterGenerator generator;

    @BeforeAll
    static void init() {
        generator = new StubCharacterGenerator(new ObjectMapper());
    }

    @Test
    void sameInputProducesSameOutput() {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        GeneratedCharacter a = generator.generate(req);
        GeneratedCharacter b = generator.generate(req);
        assertEquals(a, b, "Stub MUST be deterministic for snapshot-style tests");
    }

    @Test
    void firstNameIsUppercasedIntoHeroTitleLine1() {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "paula", null);
        assertEquals("PAULA", generator.generate(req).heroTitleLine1());
    }

    @Test
    void firstNameIsTrimmedBeforeUppercasing() {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "  Paula  ", null);
        assertEquals("PAULA", generator.generate(req).heroTitleLine1());
    }

    @Test
    void differentFirstNamesPickDifferentVariants() {
        Set<String> distinctTitles = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                    Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "name" + i, null);
            distinctTitles.add(generator.generate(req).heroTitleLine2());
        }
        assertTrue(distinctTitles.size() > 1, "Expected variant differentiation across firstNames; "
                + "got only " + distinctTitles.size() + " unique title lines");
    }

    @Test
    void vibePresenceTiltsTitleLine2() {
        AlterEgoRequest withoutVibe = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);
        AlterEgoRequest withVibe = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        // The hash bucket MAY be the same (vibe changes the key), but in either case
        // the presence of a vibe should prepend its label to line 2.
        GeneratedCharacter withVibeChar = generator.generate(withVibe);
        assertTrue(withVibeChar.heroTitleLine2().startsWith("The Rebel "),
                () -> "Expected line 2 to start with 'The Rebel ', got: " + withVibeChar.heroTitleLine2());

        GeneratedCharacter plainChar = generator.generate(withoutVibe);
        assertTrue(plainChar.heroTitleLine2().startsWith("The "),
                () -> "Expected line 2 to start with 'The ', got: " + plainChar.heroTitleLine2());
    }

    @ParameterizedTest
    @EnumSource(Archetype.class)
    void everyArchetypeProducesWellFormedOutput(Archetype archetype) {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, archetype,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);
        GeneratedCharacter character = generator.generate(req);
        assertNotNull(character.heroTitleLine1());
        assertNotNull(character.heroTitleLine2());
        assertNotNull(character.tagline());
        assertEquals(3, character.superpowers().size());
        assertNotNull(character.quote());
        assertTrue(character.heroTitleLine1().length() > 0);
        assertTrue(character.heroTitleLine2().length() > 0);
    }

    @ParameterizedTest
    @EnumSource(Universe.class)
    void everyUniverseProducesWellFormedOutput(Universe universe) {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                universe, null, ArtStyle.OIL_PAINTING, "Paula", null);
        GeneratedCharacter character = generator.generate(req);
        assertNotNull(character.heroTitleLine2());
        assertEquals(3, character.superpowers().size());
    }
}
