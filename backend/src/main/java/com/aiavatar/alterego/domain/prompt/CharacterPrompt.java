package com.aiavatar.alterego.domain.prompt;

import java.util.Objects;

/**
 * Result of character-text prompt construction. Mirrors {@link ImagePrompt}
 * for the LLM call path. Pure value type (FR-2407 part a).
 */
public record CharacterPrompt(String text) {
    public CharacterPrompt {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("CharacterPrompt text MUST NOT be blank");
        }
    }
}
