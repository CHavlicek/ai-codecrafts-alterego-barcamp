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
     * 022 / 029 — accent-key default. When the user picked a custom role (no
     * prefab archetype), {@code archetype} on the wire is {@code null} but the
     * accent resolver still needs a deterministic key. Defaulting to
     * {@link Archetype#SOFTWARE_DEVELOPER} keeps every custom-role generation on
     * a curated palette entry (rather than the hash-fallback's modular slot).
     * This is accent-key-only — the role-of-record displayed and prompted is
     * still the user's custom string. See research.md §R4.
     */
    private static final Archetype CUSTOM_ROLE_ACCENT_DEFAULT = Archetype.SOFTWARE_DEVELOPER;

    /**
     * 029 — accent-key default for a custom universe (no prefab). Keeps the
     * accent deterministic and on a curated-quality entry.
     */
    private static final Universe CUSTOM_UNIVERSE_ACCENT_DEFAULT = Universe.MARVEL;

    public static AccentTone deriveAccent(Archetype archetype, Universe universe) {
        Archetype key = archetype != null ? archetype : CUSTOM_ROLE_ACCENT_DEFAULT;
        Universe universeKey = universe != null ? universe : CUSTOM_UNIVERSE_ACCENT_DEFAULT;
        Map<Universe, AccentTone> byUniverse = CURATED.get(key);
        if (byUniverse != null) {
            AccentTone curated = byUniverse.get(universeKey);
            if (curated != null) {
                return curated;
            }
        }
        return hashFallback(key, universeKey);
    }

    private static AccentTone hashFallback(Archetype archetype, Universe universe) {
        AccentTone[] palette = AccentTone.palette();
        // 029: archetype is a 9-value enum and universe a 6-value enum. The
        // modular hash distributes uniformly across the palette — values
        // without a curated entry get their colour from this fallback.
        int idx = Math.floorMod(archetype.ordinal() * 6 + universe.ordinal(), palette.length);
        return palette[idx];
    }

    /**
     * Curated narrative-fit cells. Intentionally partial — the hash fallback
     * fills the rest. Kept small so tests over the full matrix don't need to
     * enumerate every combination here.
     */
    private static Map<Archetype, Map<Universe, AccentTone>> buildCuratedMap() {
        Map<Archetype, Map<Universe, AccentTone>> map = new EnumMap<>(Archetype.class);

        map.put(Archetype.SOFTWARE_DEVELOPER, Map.of(
                Universe.STAR_WARS, AccentTone.CYAN,
                Universe.RETRO_SYNTHWAVE, AccentTone.PURPLE,
                Universe.MARVEL, AccentTone.BLUE
        ));
        map.put(Archetype.DATA_ANALYST, Map.of(
                Universe.SPY_THRILLER, AccentTone.BLUE,
                Universe.RETRO_SYNTHWAVE, AccentTone.CYAN,
                Universe.MARVEL, AccentTone.PURPLE
        ));
        map.put(Archetype.MARKETING_SPECIALIST, Map.of(
                Universe.MARVEL, AccentTone.RED,
                Universe.RETRO_SYNTHWAVE, AccentTone.PURPLE,
                Universe.NINETIES_SITCOM, AccentTone.GOLD
        ));
        map.put(Archetype.SUSTAINABILITY_LEAD, Map.of(
                Universe.NINETIES_SITCOM, AccentTone.GREEN,
                Universe.GHOSTBUSTERS, AccentTone.GREEN,
                Universe.STAR_WARS, AccentTone.CYAN
        ));

        return map;
    }
}
