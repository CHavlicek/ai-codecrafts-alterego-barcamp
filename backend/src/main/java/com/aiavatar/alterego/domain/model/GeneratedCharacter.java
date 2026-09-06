package com.aiavatar.alterego.domain.model;

import java.util.List;

/**
 * Textual output of {@link com.aiavatar.alterego.service.CharacterGenerator}.
 * Shape mirrors the {@code GeneratedCharacter} schema in the OpenAPI contract.
 *
 * @param heroTitleLine1 First name in uppercase (substituted at generation time).
 * @param heroTitleLine2 Archetype-flavoured title line ("The Cloud Guardrail").
 * @param tagline        Punchy 4–6 word statement, typically uppercase.
 * @param superpowers    Exactly three short superpower descriptions.
 * @param quote          Short dev-flavoured quote (≤ ~12 words).
 */
public record GeneratedCharacter(
        String heroTitleLine1,
        String heroTitleLine2,
        String tagline,
        List<String> superpowers,
        String quote
) {
    public GeneratedCharacter {
        if (superpowers == null || superpowers.size() != 3) {
            throw new IllegalArgumentException("superpowers MUST contain exactly 3 entries");
        }
        // Defensive copy — record components are otherwise references.
        superpowers = List.copyOf(superpowers);
    }
}
