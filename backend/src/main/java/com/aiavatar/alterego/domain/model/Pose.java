package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Visual composition / stance for the generated poster. Replaces the
 * biometric gender cue per /speckit.clarify Q3 (see spec Clarifications).
 *
 * Wire values are kebab-case strings matching the OpenAPI {@code Pose} enum
 * in {@code contracts/alter-egos.openapi.yaml}.
 */
public enum Pose {
    HEROIC("heroic"),
    STEALTHY("stealthy"),
    MYSTICAL("mystical"),
    SCHOLAR("scholar");

    private final String wire;

    Pose(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static Pose fromWire(String value) {
        return Arrays.stream(values())
                .filter(p -> p.wire.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown pose: " + value));
    }
}
