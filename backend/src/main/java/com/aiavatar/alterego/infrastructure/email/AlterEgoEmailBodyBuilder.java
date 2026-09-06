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
        String trimmed = firstName.trim();
        return "Hey, " + trimmed + "!\n"
                + "\n"
                + "Thank you, for being a part of CodeCrafts 2026!\n"
                + "\n"
                + "Find your AI Generated Alter Ego attached to this letter.\n"
                + "\n"
                + "Happy times!\n";
    }
}
