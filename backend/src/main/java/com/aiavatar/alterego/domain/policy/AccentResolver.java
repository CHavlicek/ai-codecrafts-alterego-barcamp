package com.aiavatar.alterego.domain.policy;

import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.Universe;

import java.util.EnumMap;
import java.util.Map;

/**
 * Derives the poster accent {@link AccentTone} from the
 * {@code (archetype, universe)} pair per 002-sleek-tabbed-ui FR-132 and
 * research.md §R6.
 *
 * <p>Resolution is a curated map ("Star Wars × Cloud Architect feels cyan")
 * with a deterministic hash fallback over the full six-value palette. The
 * function is total: every possible {@code (archetype, universe)} pair
 * resolves to a non-null {@link AccentTone}.
 *
 * <p>Shared by {@link com.aiavatar.alterego.infrastructure.provider.stub.StubImageGenerator}
 * and {@link com.aiavatar.alterego.infrastructure.provider.fallback.FallbackPosterProvider}
 * so the primary and fallback posters use the same colour for identical
 * inputs.
 */
public final class AccentResolver {

    private static final Map<Archetype, Map<Universe, AccentTone>> CURATED = buildCuratedMap();

    private AccentResolver() {
    }

    /**
     * 022 — accent-key default. When the user picked a custom role (no
     * prefab archetype), {@code archetype} on the wire is {@code null} but
     * the accent resolver still needs a deterministic key. Defaulting to
     * {@link Archetype#BACKEND_DEV} keeps every custom-role generation on a
     * curated palette entry (rather than the hash-fallback's modular slot)
     * so the accent quality is identical to picking Backend Dev. This is
     * accent-key-only — the role-of-record displayed and prompted is still
     * the user's custom string. See research.md §R4.
     */
    private static final Archetype CUSTOM_ROLE_ACCENT_DEFAULT = Archetype.BACKEND_DEV;

    public static AccentTone deriveAccent(Archetype archetype, Universe universe) {
        Archetype key = archetype != null ? archetype : CUSTOM_ROLE_ACCENT_DEFAULT;
        Map<Universe, AccentTone> byUniverse = CURATED.get(key);
        if (byUniverse != null) {
            AccentTone curated = byUniverse.get(universe);
            if (curated != null) {
                return curated;
            }
        }
        return hashFallback(key, universe);
    }

    private static AccentTone hashFallback(Archetype archetype, Universe universe) {
        AccentTone[] palette = AccentTone.palette();
        // 022: archetype is now a 9-value enum (HR / Administration /
        // Customer Relations added). The modular hash still distributes
        // uniformly across the palette — no curated entries required for
        // the three new values; their colour comes from this fallback.
        int idx = Math.floorMod(archetype.ordinal() * 6 + universe.ordinal(), palette.length);
        return palette[idx];
    }

    /**
     * Curated narrative-fit cells. Intentionally partial — the hash fallback
     * fills the rest. Kept small so tests over the full 6×6 matrix don't need
     * to enumerate every combination here.
     */
    private static Map<Archetype, Map<Universe, AccentTone>> buildCuratedMap() {
        Map<Archetype, Map<Universe, AccentTone>> map = new EnumMap<>(Archetype.class);

        map.put(Archetype.CLOUD_ARCHITECT, Map.of(
                Universe.STAR_WARS, AccentTone.CYAN,
                Universe.CYBERPUNK, AccentTone.PURPLE,
                Universe.MARVEL, AccentTone.BLUE
        ));
        map.put(Archetype.BACKEND_DEV, Map.of(
                Universe.THE_OFFICE, AccentTone.GOLD,
                Universe.LORD_OF_THE_RINGS, AccentTone.GREEN,
                Universe.STAR_WARS, AccentTone.BLUE
        ));
        map.put(Archetype.FRONTEND_DEV, Map.of(
                Universe.MARVEL, AccentTone.RED,
                Universe.CYBERPUNK, AccentTone.PURPLE,
                Universe.THE_OFFICE, AccentTone.GOLD
        ));
        map.put(Archetype.AI_ENGINEER, Map.of(
                Universe.CYBERPUNK, AccentTone.CYAN,
                Universe.STAR_WARS, AccentTone.PURPLE,
                Universe.INDIANA_JONES, AccentTone.GOLD
        ));
        map.put(Archetype.PLATFORM_ENG, Map.of(
                Universe.LORD_OF_THE_RINGS, AccentTone.GREEN,
                Universe.INDIANA_JONES, AccentTone.GOLD,
                Universe.THE_OFFICE, AccentTone.BLUE
        ));
        map.put(Archetype.DATA_ENGINEER, Map.of(
                Universe.INDIANA_JONES, AccentTone.GOLD,
                Universe.MARVEL, AccentTone.BLUE,
                Universe.LORD_OF_THE_RINGS, AccentTone.RED
        ));

        return map;
    }
}
