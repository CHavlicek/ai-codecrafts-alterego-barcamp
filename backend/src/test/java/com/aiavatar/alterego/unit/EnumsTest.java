package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Wire-value contract for the four enums. Asserts that every declared
 * constant exposes the exact wire string declared in the OpenAPI contract
 * and that {@code fromWire} is a perfect inverse of {@code wire()}.
 *
 * <p>002 delta: Colour is no longer a public enum (accent derivation moved
 * to {@code AccentResolver}); Archetype + Universe value sets were replaced;
 * Vibe is a new optional enum.
 */
class EnumsTest {

    @Test
    void poseExposesExpectedWireValues() {
        Set<String> wires = Set.of(Pose.HEROIC.wire(), Pose.STEALTHY.wire(),
                Pose.MYSTICAL.wire(), Pose.SCHOLAR.wire());
        assertEquals(Set.of("heroic", "stealthy", "mystical", "scholar"), wires);
    }

    @Test
    void archetypeExposesExpectedWireValues() {
        // 022 (issue #50): three non-engineering prefab options added —
        // HR / ADMINISTRATION / CUSTOMER_RELATIONS. Existing six engineering
        // wire values are unchanged.
        Set<String> wires = Set.of(Archetype.CLOUD_ARCHITECT.wire(), Archetype.BACKEND_DEV.wire(),
                Archetype.FRONTEND_DEV.wire(), Archetype.AI_ENGINEER.wire(),
                Archetype.PLATFORM_ENG.wire(), Archetype.DATA_ENGINEER.wire(),
                Archetype.HR.wire(), Archetype.ADMINISTRATION.wire(),
                Archetype.CUSTOMER_RELATIONS.wire());
        assertEquals(Set.of("cloud-architect", "backend-dev", "frontend-dev",
                "ai-engineer", "platform-eng", "data-engineer",
                "hr", "administration", "customer-relations"), wires);
        assertEquals(9, Archetype.values().length, "022: Archetype is a nine-member closed enum");
    }

    @Test
    void archetypeNewOptionsRoundTripAndExposeLabels() {
        // 022 — wire round-trip + UI label for the three new prefab options.
        // 026 (issue #62): the HR option's guest-facing label is now
        // "People Operations". The provider-side grounding string in
        // GeminiPromptBuilder.ROLE_LABELS remains "Human Resources" by
        // intent (see specs/026-role-label-wording/research.md §R2).
        assertSame(Archetype.HR, Archetype.fromWire("hr"));
        assertSame(Archetype.ADMINISTRATION, Archetype.fromWire("administration"));
        assertSame(Archetype.CUSTOMER_RELATIONS, Archetype.fromWire("customer-relations"));
        assertEquals("People Operations", Archetype.HR.label());
        assertEquals("Administration", Archetype.ADMINISTRATION.label());
        assertEquals("Customer Relations", Archetype.CUSTOMER_RELATIONS.label());
    }

    @Test
    void universeExposesExpectedWireValues() {
        Set<String> wires = Set.of(Universe.MARVEL.wire(), Universe.STAR_WARS.wire(),
                Universe.CYBERPUNK.wire(), Universe.THE_OFFICE.wire(),
                Universe.INDIANA_JONES.wire(), Universe.LORD_OF_THE_RINGS.wire());
        assertEquals(Set.of("marvel", "star-wars", "cyberpunk", "the-office",
                "indiana-jones", "lord-of-the-rings"), wires);
    }

    @Test
    void vibeExposesExpectedWireValues() {
        Set<String> wires = Set.of(Vibe.BUILDER.wire(), Vibe.THINKER.wire(),
                Vibe.REBEL.wire(), Vibe.ARCHITECT.wire());
        assertEquals(Set.of("builder", "thinker", "rebel", "architect"), wires);
    }

    @Test
    void poseFromWireRoundTrips() {
        for (Pose p : Pose.values()) {
            assertSame(p, Pose.fromWire(p.wire()));
        }
    }

    @Test
    void archetypeFromWireRoundTripsAndExposesLabel() {
        for (Archetype a : Archetype.values()) {
            assertSame(a, Archetype.fromWire(a.wire()));
        }
        assertEquals("Cloud Architect", Archetype.CLOUD_ARCHITECT.label());
        // 026 (issue #62): three engineering Role labels expand to their
        // full form; "Backend Dev" → "Backend Developer", "Frontend Dev"
        // → "Frontend Developer", "Platform Eng." → "Platform Engineer".
        assertEquals("Backend Developer", Archetype.BACKEND_DEV.label());
        assertEquals("Frontend Developer", Archetype.FRONTEND_DEV.label());
        assertEquals("Platform Engineer", Archetype.PLATFORM_ENG.label());
        assertEquals("Data Engineer", Archetype.DATA_ENGINEER.label());
    }

    @Test
    void universeFromWireRoundTripsAndExposesLabel() {
        for (Universe u : Universe.values()) {
            assertSame(u, Universe.fromWire(u.wire()));
        }
        assertEquals("Marvel", Universe.MARVEL.label());
        assertEquals("Lord of the Rings", Universe.LORD_OF_THE_RINGS.label());
    }

    @Test
    void vibeFromWireRoundTripsAndExposesLabel() {
        for (Vibe v : Vibe.values()) {
            assertSame(v, Vibe.fromWire(v.wire()));
        }
        assertEquals("Builder", Vibe.BUILDER.label());
        assertEquals("Rebel", Vibe.REBEL.label());
    }

    @Test
    void artStyleExposesExpectedWireValues() {
        // 019 delta (closes #49): `pixel-art`, `low-poly-3d`, `line-art`
        // retired. Cardinality now six.
        Set<String> wires = Set.of(
                ArtStyle.OIL_PAINTING.wire(),
                ArtStyle.WATERCOLOR.wire(),
                ArtStyle.POP_ART.wire(),
                ArtStyle.RENAISSANCE_PORTRAIT.wire(),
                ArtStyle.JAPANESE_WOODBLOCK.wire(),
                ArtStyle.CEL_SHADED.wire());
        assertEquals(Set.of(
                "oil-painting", "watercolor",
                "pop-art", "renaissance-portrait", "japanese-woodblock", "cel-shaded"), wires);
        assertEquals(6, ArtStyle.values().length, "019 FR-1901: ArtStyle is a six-member closed enum");
    }

    @Test
    void artStyleFromWireRoundTripsAndExposesLabel() {
        for (ArtStyle a : ArtStyle.values()) {
            assertSame(a, ArtStyle.fromWire(a.wire()));
        }
        // Surviving labels are unchanged from 006 (019 FR-1904).
        assertEquals("Oil Painting", ArtStyle.OIL_PAINTING.label());
        assertEquals("Watercolor", ArtStyle.WATERCOLOR.label());
        assertEquals("Pop Art", ArtStyle.POP_ART.label());
        assertEquals("Renaissance Portrait", ArtStyle.RENAISSANCE_PORTRAIT.label());
        assertEquals("Japanese Woodblock", ArtStyle.JAPANESE_WOODBLOCK.label());
        assertEquals("Cel-Shaded", ArtStyle.CEL_SHADED.label());
    }

    @Test
    void artStyleFromWireRejectsRetiredValues() {
        // 019 FR-1903 / FR-1907 — retired wire values from feature 006 are
        // no longer accepted by the deserialiser. There is no "deprecated
        // but accepted" period; a stale tab posting one of these gets the
        // same IllegalArgumentException any other unknown wire value would.
        assertThrows(IllegalArgumentException.class, () -> ArtStyle.fromWire("pixel-art"));
        assertThrows(IllegalArgumentException.class, () -> ArtStyle.fromWire("low-poly-3d"));
        assertThrows(IllegalArgumentException.class, () -> ArtStyle.fromWire("line-art"));
    }

    @Test
    void fromWireRejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> Pose.fromWire("crouching"));
        assertThrows(IllegalArgumentException.class, () -> Archetype.fromWire("bug-hunter"));
        assertThrows(IllegalArgumentException.class, () -> Universe.fromWire("harry-potter"));
        assertThrows(IllegalArgumentException.class, () -> Vibe.fromWire("chaotic"));
        assertThrows(IllegalArgumentException.class, () -> ArtStyle.fromWire("stick-figure"));
        // 011 FR-1005 / FR-1008 — reject unknown photoMode wire values.
        assertThrows(IllegalArgumentException.class, () -> PhotoMode.fromWire("team"));
    }

    @Test
    void photoModeExposesExpectedWireValues() {
        // 011 FR-1005 / FR-1008: wire values are lowercase, pinned from first
        // merge, and renaming either is a breaking change.
        Set<String> wires = Set.of(PhotoMode.SINGLE.wire(), PhotoMode.GROUP.wire());
        assertEquals(Set.of("single", "group"), wires);
    }

    @Test
    void photoModeFromWireRoundTrips() {
        for (PhotoMode m : PhotoMode.values()) {
            assertSame(m, PhotoMode.fromWire(m.wire()));
        }
    }
}
