package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Composition mode for the reference photo. Introduced by 011-group-photos.
 *
 * <p>{@link #SINGLE} — the photo contains a single subject; the generated
 * poster renders one alter-ego portrait (today's baseline, and the default).
 * {@link #GROUP} — the photo contains multiple subjects; the generated
 * poster renders every person visible in the reference photo as the same
 * alter-ego archetype, universe, art style, pose, and vibe.
 *
 * <p>Branches the prompt produced by
 * {@link com.aiavatar.alterego.infrastructure.provider.gemini.GeminiPromptBuilder}; the stub
 * image generator and the fallback poster path are mode-agnostic.
 *
 * <p>Wire values are lowercase kebab-case strings matching the frontend
 * {@code PhotoMode} union in {@code frontend/src/features/alterego/types.ts}
 * and are part of the public API contract from first merge
 * (spec FR-1005 / FR-1008) — renaming any value is a breaking change.
 */
public enum PhotoMode {
    SINGLE("single"),
    GROUP("group");

    private final String wire;

    PhotoMode(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static PhotoMode fromWire(String value) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown photoMode: " + value));
    }
}
