package com.aiavatar.alterego.domain.prompt;

import java.util.Objects;

/**
 * Result of provider-agnostic image-prompt construction. Pure value type
 * (FR-2407 part a) — no Spring, no HTTP, no I/O. Carries the raw prompt
 * string the provider client serializes into its request body.
 *
 * <p>The two real provider builders ({@code GeminiPromptBuilder},
 * {@code FalAiPromptBuilder}) return instances of this type so the seam
 * between "what to ask the model" (domain) and "how to talk to the model"
 * (infrastructure) is explicit.
 */
public record ImagePrompt(String text) {
    public ImagePrompt {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("ImagePrompt text MUST NOT be blank");
        }
    }
}
