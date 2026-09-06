package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Optional mood / disposition hint introduced by 002-sleek-tabbed-ui.
 *
 * <p>Vibe is not required for generation — the request's {@code vibe} field is
 * nullable. When present, the stubbed character generator gives the hero title
 * / tagline a light tonal tilt; when absent, generation proceeds unchanged.
 *
 * <p>Wire values are kebab-case strings matching the OpenAPI {@code Vibe} enum
 * in {@code specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml}.
 */
public enum Vibe {
    BUILDER("builder", "Builder"),
    THINKER("thinker", "Thinker"),
    REBEL("rebel", "Rebel"),
    ARCHITECT("architect", "Architect");

    private final String wire;
    private final String label;

    Vibe(String wire, String label) {
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
    public static Vibe fromWire(String value) {
        return Arrays.stream(values())
                .filter(v -> v.wire.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown vibe: " + value));
    }
}
