package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Universe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Record-compact-constructor invariants + trivial helpers that
 * otherwise sit uncovered. Cheap to maintain and pushes the bundle
 * above the Principle III 90% line-coverage gate.
 */
class RecordInvariantsTest {

    @Test
    void photoPayloadRejectsNullOrEmptyBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoPayload(null, "image/jpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoPayload(new byte[0], "image/jpeg"));
    }

    @Test
    void photoPayloadRejectsUnknownMediaType() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoPayload(new byte[]{1}, "image/gif"));
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoPayload(new byte[]{1}, null));
    }

    @Test
    void posterImageRejectsNullOrEmptyBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new PosterImage(null, "image/png", 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PosterImage(new byte[0], "image/png", 1, 1));
    }

    @Test
    void posterImageRejectsUnknownMediaType() {
        assertThrows(IllegalArgumentException.class,
                () -> new PosterImage(new byte[]{1}, "image/svg+xml", 1, 1));
    }

    @Test
    void posterImageRejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new PosterImage(new byte[]{1}, "image/png", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PosterImage(new byte[]{1}, "image/png", 1, -1));
    }

    @Test
    void posterImageToDataUrlCarriesBase64AndMediaType() {
        PosterImage image = new PosterImage(new byte[]{1, 2, 3}, "image/png", 10, 10);
        String url = image.toDataUrl();
        // "AQID" is base64 of [1, 2, 3]
        assertEquals("data:image/png;base64,AQID", url);
    }

    @Test
    void generatedCharacterRejectsSuperpowersNotOfLengthThree() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeneratedCharacter("A", "B", "C", List.of("one"), "q"));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneratedCharacter("A", "B", "C", List.of("a", "b", "c", "d"), "q"));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneratedCharacter("A", "B", "C", null, "q"));
    }

    @Test
    void alterEgoRequestWithTrimmedFirstNameReturnsIdentityWhenAlreadyTrimmed() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);
        assertSame(req, req.withTrimmedFirstName());
    }

    @Test
    void alterEgoRequestWithTrimmedFirstNamePreservesNullSafelyOnNonStringFields() {
        // Null firstName short-circuits before touching trim().
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, null, null);
        assertSame(req, req.withTrimmedFirstName());
    }

    @Test
    void alterEgoRequestEffectivePhotoModeDefaultsToSingleWhenNull() {
        // 011 FR-1009 / FR-1010 — nullable photoMode at the Bean Validation
        // layer; domain default for downstream consumers is SINGLE. All
        // prompt-builder / integration code MUST funnel through this accessor.
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);
        assertEquals(PhotoMode.SINGLE, req.effectivePhotoMode());
    }

    @Test
    void alterEgoRequestEffectivePhotoModeEchoesFieldWhenSet() {
        AlterEgoRequest single = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", PhotoMode.SINGLE);
        AlterEgoRequest group = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "The Architects", PhotoMode.GROUP);
        assertEquals(PhotoMode.SINGLE, single.effectivePhotoMode());
        assertEquals(PhotoMode.GROUP, group.effectivePhotoMode());
    }

    @Test
    void alterEgoRequestWithTrimmedFirstNamePreservesPhotoMode() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "  Paula  ", PhotoMode.GROUP);
        AlterEgoRequest trimmed = req.withTrimmedFirstName();
        assertEquals("Paula", trimmed.firstName());
        assertEquals(PhotoMode.GROUP, trimmed.photoMode());
    }
}
