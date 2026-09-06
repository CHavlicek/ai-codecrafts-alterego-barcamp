package com.aiavatar.alterego.domain.prompt;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;

/**
 * 029 (verbund-rebrand) — canonical universe-of-record helper, mirroring
 * {@link RoleOfRecord}: the single source of truth for "what string to use
 * for this user's fictional universe" across prompt builders and overlays.
 *
 * <p>Resolution: a non-blank {@code customUniverse} claims precedence; the
 * trimmed {@code universe.label()} is the fallback. Pure function of the
 * validated request — no Spring, no HTTP, no I/O. The
 * {@link UniverseOfRecordPresent}-style validation upstream guarantees at
 * least one of the two is present, but this helper is defensively null-safe
 * so a regressed validator never produces an NPE.
 */
public final class UniverseOfRecord {

    private static final String DEFAULT = "an inspiring future";

    private final String value;

    private UniverseOfRecord(String value) {
        this.value = value;
    }

    public static UniverseOfRecord from(AlterEgoRequest request) {
        String custom = request.customUniverse();
        if (custom != null && !custom.isBlank()) {
            return new UniverseOfRecord(custom.strip());
        }
        return new UniverseOfRecord(request.universe() != null ? request.universe().label() : DEFAULT);
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
