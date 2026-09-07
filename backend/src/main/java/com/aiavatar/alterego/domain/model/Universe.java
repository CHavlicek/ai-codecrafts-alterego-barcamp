package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Fictional setting framing the poster's visual context. Re-keyed by
 * 029-verbund-rebrand from the nerd-leaning 002 set to a broadly recognisable
 * pop-culture mix spanning the 80s, 90s and 2000s (Marvel & Star Wars kept).
 * A free-form {@code customUniverse} on the request can override this entirely.
 */
public enum Universe {
    MARVEL("marvel", "Marvel"),
    STAR_WARS("star-wars", "Star Wars"),
    RETRO_SYNTHWAVE("retro-synthwave", "Miami Vice"),
    NINETIES_SITCOM("nineties-sitcom", "The Office"),
    SPY_THRILLER("spy-thriller", "James Bond"),
    GHOSTBUSTERS("ghostbusters", "Ghostbusters");

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
