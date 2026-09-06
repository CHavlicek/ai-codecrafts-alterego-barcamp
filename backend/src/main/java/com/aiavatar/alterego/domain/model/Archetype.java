package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Corporate role. Re-themed by 029-verbund-rebrand from the developer-heavy
 * 002/022 archetypes to a broad corporate mix that fits the "AI @ Verbund
 * 2026" audience (energy-transition event). "Software Developer" survives as
 * one option among many; the rest span the departments a large utility runs.
 * Drives the generated character's title + tagline tone.
 *
 * <p>Visible label in the UI is "Role" (see spec FR-116). The field name on
 * the wire remains {@code archetype} to keep a single Java identifier across
 * the backend; only the enum values + labels have changed.
 */
public enum Archetype {
    SOFTWARE_DEVELOPER("software-developer", "Software Developer"),
    PROJECT_MANAGER("project-manager", "Project Manager"),
    DATA_ANALYST("data-analyst", "Data Analyst"),
    MARKETING_SPECIALIST("marketing-specialist", "Marketing Specialist"),
    SALES_CUSTOMER_RELATIONS("sales-customer-relations", "Sales & Customer Relations"),
    PEOPLE_CULTURE("people-culture", "People & Culture"),
    OPERATIONS_MANAGER("operations-manager", "Operations Manager"),
    FINANCE_CONTROLLER("finance-controller", "Finance Controller"),
    SUSTAINABILITY_LEAD("sustainability-lead", "Sustainability Lead");

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
