package com.aiavatar.alterego.domain.model;

import com.aiavatar.alterego.domain.model.validation.RoleOfRecordPresent;
import com.aiavatar.alterego.domain.model.validation.ValidFirstName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 020 — Public-facing DTO bound from the {@code selections} part of the
 * multipart request body. Replaces {@link AlterEgoRequest} at the controller
 * boundary; {@code AlterEgoRequest} remains as the server-internal "resolved"
 * record that the prompt builders consume (after the service rolls fresh
 * {@code Pose} and {@code Vibe} values via
 * {@link com.aiavatar.alterego.domain.policy.RandomCategorySelector}).
 *
 * <p>The {@code pose} and {@code vibe} fields are intentionally absent here
 * — spec FR-2025: client-supplied values for those categories MUST be ignored.
 * Removing them from the type is the strongest possible enforcement.
 *
 * <p>Delta from 022 (issue #50):
 * <ul>
 *   <li>{@code archetype} loses its {@code @NotNull} — null is permitted
 *       when a non-blank {@code customRole} is supplied. The OR-invariant is
 *       enforced by the class-level {@link RoleOfRecordPresent} constraint
 *       so a request missing both fields returns RFC 7807 400.</li>
 *   <li>{@code customRole} added — optional, {@code @Size(max = 100)},
 *       free-form. When non-blank-after-trim, takes precedence as the
 *       role-of-record (see {@link #roleLabel()} and FR-2211).</li>
 *   <li>A secondary 5-arg constructor preserves the pre-022 call shape so
 *       existing tests compile unchanged.</li>
 * </ul>
 */
@RoleOfRecordPresent
public record AlterEgoUserSelections(
        Archetype archetype,
        @NotNull Universe universe,
        @NotNull ArtStyle artStyle,
        @NotBlank @Size(max = 50) @ValidFirstName String firstName,
        PhotoMode photoMode,
        @Size(max = 100) String customRole
) {

    /**
     * 022 — secondary constructor preserving the pre-022 5-arg call shape.
     * Delegates to the canonical 6-arg constructor with {@code customRole = null}.
     */
    public AlterEgoUserSelections(Archetype archetype,
                                  Universe universe,
                                  ArtStyle artStyle,
                                  String firstName,
                                  PhotoMode photoMode) {
        this(archetype, universe, artStyle, firstName, photoMode, null);
    }

    /**
     * Resolves the composition mode to its domain default when the wire field
     * is absent — same semantics as the pre-020 helper on
     * {@link AlterEgoRequest#effectivePhotoMode()}.
     */
    public PhotoMode effectivePhotoMode() {
        return photoMode != null ? photoMode : PhotoMode.SINGLE;
    }

    /**
     * 022 — role-of-record helper, mirroring {@link AlterEgoRequest#roleLabel()}.
     * Custom role (trimmed, when non-blank) takes precedence over the prefab
     * archetype's label.
     */
    public String roleLabel() {
        if (customRole != null && !customRole.isBlank()) {
            return customRole.trim();
        }
        return archetype != null ? archetype.label() : "Engineer";
    }
}
