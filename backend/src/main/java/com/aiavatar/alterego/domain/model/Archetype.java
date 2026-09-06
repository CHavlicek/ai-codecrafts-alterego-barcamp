package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Engineering role. Re-themed by 002-sleek-tabbed-ui from the 001 developer
 * archetypes ("Bug Hunter", "Cloud Wizard", …) to the six roles shown in the
 * mockup. Drives the generated character's title + tagline tone.
 *
 * <p>Visible label in the UI is "Role" (see spec FR-116). The field name on
 * the wire remains {@code archetype} to keep a single Java identifier across
 * the backend; only the enum values + labels have changed.
 */
public enum Archetype {
    CLOUD_ARCHITECT("cloud-architect", "Cloud Architect"),
    BACKEND_DEV("backend-dev", "Backend Developer"),
    FRONTEND_DEV("frontend-dev", "Frontend Developer"),
    AI_ENGINEER("ai-engineer", "AI Engineer"),
    PLATFORM_ENG("platform-eng", "Platform Engineer"),
    DATA_ENGINEER("data-engineer", "Data Engineer"),
    // 022 (issue #50) — three non-engineering prefab options. Wire values
    // follow the existing kebab-case public-contract convention. UI labels
    // mirror the spec exactly; prompt-side display labels (longer / more
    // grounded for the model) live in each prompt builder's ROLE_LABELS map.
    // 026 (issue #62) — engineering-role labels widened to their full form
    // ("Backend Developer" / "Frontend Developer" / "Platform Engineer") and
    // HR's guest-facing label widened to "People Operations". Wire values
    // and enum constants are unchanged; provider-side ROLE_LABELS likewise
    // unchanged (see specs/026-role-label-wording/research.md §R2).
    HR("hr", "People Operations"),
    ADMINISTRATION("administration", "Administration"),
    CUSTOMER_RELATIONS("customer-relations", "Customer Relations");

    private final String wire;
    private final String label;

    Archetype(String wire, String label) {
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
    public static Archetype fromWire(String value) {
        return Arrays.stream(values())
                .filter(a -> a.wire.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown archetype: " + value));
    }
}
