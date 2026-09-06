package com.aiavatar.alterego.unit.domain.prompt;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T029 — canonical role-of-record helper (FR-2408): non-blank
 * {@code customRole} claims precedence; trimmed {@code archetype.label()}
 * is the fallback. Lives on {@link AlterEgoRequest#roleLabel()} (sole call
 * site so prompts + overlays stay in sync).
 */
class RoleOfRecordTest {

    private static AlterEgoRequest req(Archetype archetype, String customRole) {
        return new AlterEgoRequest(
                Pose.HEROIC, archetype, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Sam", PhotoMode.SINGLE, customRole);
    }

    @Test
    void customRolePrecedesArchetypeWhenNonBlank() {
        assertEquals("Quantum Physicist", req(Archetype.SOFTWARE_DEVELOPER, "Quantum Physicist").roleLabel());
    }

    @Test
    void customRoleIsTrimmed() {
        assertEquals("Quantum Physicist", req(Archetype.SOFTWARE_DEVELOPER, "  Quantum Physicist  ").roleLabel());
    }

    @Test
    void blankCustomRoleFallsBackToArchetypeLabel() {
        assertEquals(Archetype.SOFTWARE_DEVELOPER.label(), req(Archetype.SOFTWARE_DEVELOPER, "").roleLabel());
        assertEquals(Archetype.SOFTWARE_DEVELOPER.label(), req(Archetype.SOFTWARE_DEVELOPER, "   ").roleLabel());
    }

    @Test
    void nullCustomRoleFallsBackToArchetypeLabel() {
        assertEquals(Archetype.SOFTWARE_DEVELOPER.label(), req(Archetype.SOFTWARE_DEVELOPER, null).roleLabel());
    }
}
