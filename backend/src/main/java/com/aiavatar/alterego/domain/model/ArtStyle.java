package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Required rendering style for the generated poster. Introduced by
 * 006-art-style-category; cardinality reduced in 019-remove-art-styles
 * (closes #49) — see specs/019-remove-art-styles/contracts/alter-egos.openapi.yaml
 * for the wire-spec delta.
 *
 * <p>Six mutually-exclusive values. The {@link GeminiPromptBuilder} maps
 * each value to a natural-language description that conditions the real
 * image-generation provider; the stub fallback path is unaffected (FR-309).
 *
 * <p>Wire values are kebab-case strings matching the OpenAPI {@code ArtStyle}
 * enum and are part of the public API contract from first merge (FR-310) —
 * renaming any surviving value is a breaking change. The retired values
 * (`pixel-art`, `low-poly-3d`, `line-art`) are rejected by
 * {@link #fromWire(String)} with the same "Unknown artStyle" message that
 * any other unrecognised string produces.
 */
public enum ArtStyle {
    OIL_PAINTING("oil-painting", "Oil Painting"),
    WATERCOLOR("watercolor", "Watercolor"),
    POP_ART("pop-art", "Pop Art"),
    RENAISSANCE_PORTRAIT("renaissance-portrait", "Renaissance Portrait"),
    JAPANESE_WOODBLOCK("japanese-woodblock", "Japanese Woodblock"),
    CEL_SHADED("cel-shaded", "Cel-Shaded");

    private final String wire;
    private final String label;

    ArtStyle(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public String label() {
        return label;
    }

    @JsonCreator
    public static ArtStyle fromWire(String value) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown artStyle: " + value));
    }
}
