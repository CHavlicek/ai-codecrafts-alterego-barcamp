package com.aiavatar.alterego.domain.policy;

import java.awt.Color;

/**
 * Poster accent colour used by the image generators. Replaces the public
 * {@code Colour} wire enum that 001 exposed — 002 derives the poster accent
 * from {@code (archetype, universe)} rather than asking the user.
 *
 * <p>Internal to the service layer; not serialised and not part of any public
 * contract. The six constants below are the full palette available to
 * {@link AccentResolver#deriveAccent(com.aiavatar.alterego.domain.model.Archetype,
 * com.aiavatar.alterego.domain.model.Universe)}.
 */
public record AccentTone(String name, Color awt) {
    public static final AccentTone BLUE = new AccentTone("blue", new Color(0x1A, 0x8A, 0xAA));
    public static final AccentTone PURPLE = new AccentTone("purple", new Color(0xA8, 0x55, 0xF7));
    public static final AccentTone RED = new AccentTone("red", new Color(0xE0, 0x58, 0x58));
    public static final AccentTone CYAN = new AccentTone("cyan", new Color(0x00, 0xBF, 0xFF));
    public static final AccentTone GOLD = new AccentTone("gold", new Color(0xF0, 0xA0, 0x30));
    public static final AccentTone GREEN = new AccentTone("green", new Color(0x3D, 0xD6, 0x8A));

    public static AccentTone[] palette() {
        return new AccentTone[]{BLUE, PURPLE, RED, CYAN, GOLD, GREEN};
    }
}
