package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Fictional setting framing the poster's visual context. Expanded and re-keyed
 * by 002-sleek-tabbed-ui to the six options shown in the mockup.
 */
public enum Universe {
    MARVEL("marvel", "Marvel"),
    STAR_WARS("star-wars", "Star Wars"),
    CYBERPUNK("cyberpunk", "Cyberpunk"),
    THE_OFFICE("the-office", "The Office"),
    INDIANA_JONES("indiana-jones", "Indiana Jones"),
    LORD_OF_THE_RINGS("lord-of-the-rings", "Lord of the Rings");

    private final String wire;
    private final String label;

    Universe(String wire, String label) {
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
    public static Universe fromWire(String value) {
        return Arrays.stream(values())
                .filter(u -> u.wire.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown universe: " + value));
    }
}
