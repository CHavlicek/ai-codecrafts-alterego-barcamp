package com.aiavatar.alterego.domain.model;

import com.aiavatar.alterego.domain.model.validation.RoleOfRecordPresent;
import com.aiavatar.alterego.domain.model.validation.UniverseOfRecordPresent;
import com.aiavatar.alterego.domain.model.validation.ValidFirstName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The {@code selections} part of the multipart request body, after the
 * server has rolled fresh {@link Pose} and {@link Vibe} values (020). Bean
 * Validation runs at the controller boundary; failures are translated into
 * RFC 7807 {@code 400 Bad Request} responses.
 *
 * <p>Delta from 001: {@code colour} field removed (002 supersedes 001 FR-005).
 * {@code vibe} added as an optional field (no {@code @NotNull}); see
 * 002 FR-118. The {@code archetype} and {@code universe} fields retain their
 * wire names but their enum domains were re-themed by 002.
 *
 * <p>Delta from 002: {@code artStyle} added as a REQUIRED field (FR-304);
 * placed between {@code vibe} and {@code firstName} so optional and required
 * fields stay grouped.
 *
 * <p>Delta from 006: {@code photoMode} added as an OPTIONAL field at the Bean
 * Validation layer with a domain default of {@link PhotoMode#SINGLE} — see
 * {@link #effectivePhotoMode()} and spec 011 FR-1009.
 *
 * <p>Delta from 011: {@code firstName} length cap relaxed from 40 to 50 and
 * the {@link ValidFirstName} constraint enforces the canonical rule list.
 *
 * <p>Delta from 022 (issue #50):
 * <ul>
 *   <li>{@code archetype} is no longer {@code @NotNull} — it may be null
 *       when a custom role is supplied. The OR-invariant is enforced by
 *       the class-level {@link RoleOfRecordPresent} constraint.</li>
 *   <li>{@code customRole} added — optional, {@code @Size(max = 100)}, free-
 *       form string. When non-blank, takes precedence over {@code archetype}
 *       as the role-of-record for prompt composition and the poster text
 *       overlay (see {@link #roleLabel()}).</li>
 *   <li>A secondary 7-arg constructor delegates to the canonical 8-arg one
 *       with {@code customRole = null} so pre-022 call sites compile
 *       unchanged.</li>
 * </ul>
 */
@RoleOfRecordPresent
@UniverseOfRecordPresent
public record AlterEgoRequest(
        @NotNull Pose pose,
        Archetype archetype,
        Universe universe,
        Vibe vibe,
        @NotNull ArtStyle artStyle,
        @NotBlank @Size(max = 50) @ValidFirstName String firstName,
        PhotoMode photoMode,
        @Size(max = 100) String customRole,
        @Size(max = 100) String customUniverse
) {

    /**
     * 022 — secondary constructor preserving the pre-022 7-arg call shape.
     * Delegates to the canonical constructor with {@code customRole = null}
     * and {@code customUniverse = null}.
     */
    public AlterEgoRequest(Pose pose,
                           Archetype archetype,
                           Universe universe,
                           Vibe vibe,
                           ArtStyle artStyle,
                           String firstName,
                           PhotoMode photoMode) {
        this(pose, archetype, universe, vibe, artStyle, firstName, photoMode, null, null);
    }

    /**
     * 022 — 8-arg constructor preserving the pre-029 call shape (customRole
     * but no customUniverse). Delegates with {@code customUniverse = null}.
     */
    public AlterEgoRequest(Pose pose,
                           Archetype archetype,
                           Universe universe,
                           Vibe vibe,
                           ArtStyle artStyle,
                           String firstName,
                           PhotoMode photoMode,
                           String customRole) {
        this(pose, archetype, universe, vibe, artStyle, firstName, photoMode, customRole, null);
    }

    /**
     * Returns the request with a trimmed first name. Bean Validation runs on
     * the original; downstream code uses the trimmed form for substitution
     * into the hero title.
     */
    public AlterEgoRequest withTrimmedFirstName() {
        if (firstName == null) {
            return this;
        }
        String trimmed = firstName.trim();
        return trimmed.equals(firstName)
                ? this
                : new AlterEgoRequest(pose, archetype, universe, vibe, artStyle, trimmed, photoMode, customRole, customUniverse);
    }

    /**
     * Resolves the composition mode to its domain default when the wire field
     * is absent. All downstream consumers (prompt builder, integration tests)
     * MUST call this accessor rather than {@link #photoMode()} so the null
     * branch is funnelled through a single decision point (spec 011 FR-1010).
     */
    public PhotoMode effectivePhotoMode() {
        return photoMode != null ? photoMode : PhotoMode.SINGLE;
    }

    /**
     * 022 — the single decision point for "what role label does this request
     * carry?". 024: superseded by {@code com.aiavatar.alterego.domain.prompt.RoleOfRecord};
     * kept for backwards compatibility with existing call sites and tests.
     * New consumers SHOULD use {@code RoleOfRecord.from(request).value()}.
     *
     * <p>Defensive default for the unreachable "both null" path returns
     * {@code "Engineer"} so prompt builders never see an empty string even if
     * an upstream validator regresses.
     */
    @Deprecated(since = "024")
    public String roleLabel() {
        if (customRole != null && !customRole.isBlank()) {
            return customRole.trim();
        }
        return archetype != null ? archetype.label() : "Innovator";
    }

    /**
     * 029 — the single decision point for "what universe label does this
     * request carry?". Custom universe (trimmed, when non-blank) takes
     * precedence over the prefab universe's label. Defensive default returns
     * {@code "an inspiring future"} so prompt builders never see an empty
     * string even if an upstream validator regresses.
     */
    public String universeLabel() {
        if (customUniverse != null && !customUniverse.isBlank()) {
            return customUniverse.trim();
        }
        return universe != null ? universe.label() : "an inspiring future";
    }
}
