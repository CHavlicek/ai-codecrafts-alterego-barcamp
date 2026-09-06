package com.aiavatar.alterego.domain.prompt;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;

/**
 * Canonical role-of-record helper (FR-2408): the single source of truth
 * for "what string to use for this user's role" across prompt builders,
 * overlays, and structured logs.
 *
 * <p>Resolution: a non-blank {@code customRole} claims precedence; the
 * trimmed {@code archetype.label()} is the fallback. Pure function of
 * the validated request — no Spring, no HTTP, no I/O (FR-2407 part a).
 *
 * <p>Preserves byte-for-byte the value previously returned by
 * {@link AlterEgoRequest#roleLabel()}; that legacy method now delegates
 * here so existing consumers continue to work during migration.
 */
public final class RoleOfRecord {

    private final String value;

    private RoleOfRecord(String value) {
        this.value = value;
    }

    public static RoleOfRecord from(AlterEgoRequest request) {
        String custom = request.customRole();
        if (custom != null && !custom.isBlank()) {
            return new RoleOfRecord(custom.strip());
        }
        return new RoleOfRecord(request.archetype().label());
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
