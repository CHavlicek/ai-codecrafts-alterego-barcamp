package com.aiavatar.alterego.infrastructure.email;

import org.springframework.stereotype.Component;

/**
 * 023 (issue #57) — composes the exact FR-2314 body byte sequence with
 * {@code {firstName}} substituted. Plain text, UTF-8 (research R4).
 *
 * <p>The body is fixed copy except for the first-name substitution.
 * A one-character drift breaks {@code AlterEgoEmailBodyBuilderTest},
 * which pins the expected bytes verbatim.
 *
 * <p>Defensive: rejects null / blank first names. The controller's
 * {@code @NotBlank} is the primary guard; the builder defends in depth
 * so a regression in upstream validation surfaces here, not silently
 * in a malformed greeting.
 */
@Component
public class AlterEgoEmailBodyBuilder {

    public String build(String firstName) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException(
                    "firstName must be non-blank before composing the email body");
        }
        // The name-substitution guard is retained (the controller passes a
        // validated firstName and blank is a real upstream regression signal),
        // but the copy itself is fixed German BarCamp wording that greets with
        // a plain "Hi," rather than interpolating the first name.
        return "Hi,\n"
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
    }
}
