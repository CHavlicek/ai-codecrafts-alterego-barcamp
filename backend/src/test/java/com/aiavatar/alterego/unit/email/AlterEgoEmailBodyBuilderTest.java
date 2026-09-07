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

    private static final String EXPECTED_BODY =
            "Hi,\n"
                    + "\n"
                    + "dein persönliches AI Alter Ego ist fertig und wartet auf dich.\n"
                    + "\n"
                    + "Du kannst es direkt speichern, weiterverwenden oder einfach als Erinnerung an deinen Barcamp-Tag mitnehmen.\n"
                    + "\n"
                    + "Vielleicht hilft es dir ja auch dabei, dich an deine ganz persönlichen Superkräfte zu erinnern. 😉\n"
                    + "\n"
                    + "Viel Spaß damit – und weiterhin eine gute Mission!\n"
                    + "\n"
                    + "Dein AI@VERBUND Barcamp Team\n";

    @Test
    void buildsExactBodyByteSequence() {
        assertEquals(EXPECTED_BODY, builder.build("Dmytro"));
    }

    @Test
    void bodyCopyIsFixedRegardlessOfFirstName() {
        // The BarCamp copy greets with a plain "Hi," and no longer
        // interpolates the first name, so any non-blank name yields the
        // identical body.
        assertEquals(EXPECTED_BODY, builder.build("Paula"));
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
    void nonBlankFirstNameWithSurroundingWhitespaceStillProducesBody() {
        // Controller passes the trimmed value, but defend in depth — a
        // padded-but-non-blank name must still pass the guard and yield
        // the fixed copy.
        assertEquals(EXPECTED_BODY, builder.build("  Dmytro  "));
    }
}
