package com.aiavatar.alterego.service.text;

import com.aiavatar.alterego.infrastructure.overlay.text.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link PosterTextLines} value record (017 T006,
 * refined 2026-05-08 for the three-slot name + role + quote layout).
 */
class PosterTextLinesTest {

    @Test
    void whitespaceIsStrippedOnEachField() {
        PosterTextLines lines = new PosterTextLines(
                "  Ada\t",
                "\nCloud Architect\r\n",
                "  It is always DNS.  ");

        assertEquals("Ada", lines.name());
        assertEquals("Cloud Architect", lines.role());
        assertEquals("It is always DNS.", lines.quote());
    }

    @Test
    void nullsAreCoercedToEmptyStrings() {
        PosterTextLines lines = new PosterTextLines(null, null, null);

        assertEquals("", lines.name());
        assertEquals("", lines.role());
        assertEquals("", lines.quote());
    }

    @Test
    void mixedNullAndStringInputsAreNormalised() {
        PosterTextLines lines = new PosterTextLines(null, "  Cloud Architect  ", null);

        assertEquals("", lines.name());
        assertEquals("Cloud Architect", lines.role());
        assertEquals("", lines.quote());
    }

    @Test
    void allWhitespaceFieldsBecomeEmptyAndIsEmptyReportsTrue() {
        PosterTextLines lines = new PosterTextLines("   ", "\t\t", "\n");
        assertTrue(lines.name().isEmpty());
        assertTrue(lines.role().isEmpty());
        assertTrue(lines.quote().isEmpty());
        assertTrue(lines.isEmpty());
    }

    @Test
    void anyNonEmptyFieldMakesIsEmptyFalse() {
        assertFalse(new PosterTextLines("Ada", "", "").isEmpty());
        assertFalse(new PosterTextLines("", "Cloud Architect", "").isEmpty());
        assertFalse(new PosterTextLines("", "", "It is always DNS.").isEmpty());
    }
}
