package com.aiavatar.alterego.unit.email;

import com.aiavatar.alterego.infrastructure.email.AlterEgoEmailBodyBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 023 (issue #57) — pins the EXACT byte sequence FR-2314 specifies for
 * the outgoing email body, with {@code {firstName}} substituted. A
 * one-character drift fails the suite.
 */
class AlterEgoEmailBodyBuilderTest {

    private final AlterEgoEmailBodyBuilder builder = new AlterEgoEmailBodyBuilder();

    private static final String EXPECTED_BODY_DMYTRO =
            "Hey, Dmytro!\n"
                    + "\n"
                    + "Thank you, for being a part of AI @ Verbund 2026!\n"
                    + "\n"
                    + "Find your AI Generated Alter Ego attached to this letter.\n"
                    + "\n"
                    + "Happy times!\n";

    @Test
    void buildsExactFr2314ByteSequenceWithFirstNameSubstituted() {
        assertEquals(EXPECTED_BODY_DMYTRO, builder.build("Dmytro"));
    }

    @Test
    void substitutesAnyFirstNameVerbatim() {
        String expected =
                "Hey, Paula!\n"
                        + "\n"
                        + "Thank you, for being a part of AI @ Verbund 2026!\n"
                        + "\n"
                        + "Find your AI Generated Alter Ego attached to this letter.\n"
                        + "\n"
                        + "Happy times!\n";
        assertEquals(expected, builder.build("Paula"));
    }

    @Test
    void blankFirstNameThrowsDefensively() {
        // Controller-level @NotBlank is the primary guard; the builder
        // defends in depth (R5 / data-model.md).
        assertThrows(IllegalArgumentException.class, () -> builder.build(""));
        assertThrows(IllegalArgumentException.class, () -> builder.build("   "));
    }

    @Test
    void nullFirstNameThrowsDefensively() {
        assertThrows(IllegalArgumentException.class, () -> builder.build(null));
    }

    @Test
    void firstNameWithSurroundingWhitespaceIsTrimmedBeforeSubstitution() {
        // Controller passes the trimmed value, but defend in depth.
        assertEquals(EXPECTED_BODY_DMYTRO, builder.build("  Dmytro  "));
    }
}
