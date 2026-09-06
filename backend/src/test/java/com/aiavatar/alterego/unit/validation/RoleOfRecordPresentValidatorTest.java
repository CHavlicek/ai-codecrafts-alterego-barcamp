package com.aiavatar.alterego.unit.validation;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoUserSelections;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.domain.model.validation.RoleOfRecordPresentValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 022 (issue #50) — direct unit coverage for
 * {@link RoleOfRecordPresentValidator}, the engine behind the class-level
 * {@code @RoleOfRecordPresent} annotation. The truth table:
 *
 * <pre>
 *   archetype != null && customRole == null         → valid
 *   archetype != null && customRole == ""           → valid
 *   archetype != null && customRole == "   "        → valid
 *   archetype != null && customRole == "Tester"     → valid
 *   archetype == null && customRole == "Tester"     → valid
 *   archetype == null && customRole == "  Tester  " → valid
 *   archetype == null && customRole == null         → INVALID
 *   archetype == null && customRole == ""           → INVALID
 *   archetype == null && customRole == "   "        → INVALID
 * </pre>
 */
class RoleOfRecordPresentValidatorTest {

    private final RoleOfRecordPresentValidator validator = new RoleOfRecordPresentValidator();
    private final ConstraintValidatorContext ctx = mock(ConstraintValidatorContext.class);

    @Test
    void selections_archetypeOnly_isValid() {
        assertTrue(validator.isValid(selections(Archetype.SOFTWARE_DEVELOPER, null), ctx));
    }

    @Test
    void selections_archetypeWithBlankCustom_isValid() {
        assertTrue(validator.isValid(selections(Archetype.PEOPLE_CULTURE, ""), ctx));
        assertTrue(validator.isValid(selections(Archetype.PEOPLE_CULTURE, "   "), ctx));
    }

    @Test
    void selections_archetypeWithCustom_isValid() {
        assertTrue(validator.isValid(selections(Archetype.SOFTWARE_DEVELOPER, "Tester"), ctx));
    }

    @Test
    void selections_nullArchetypeWithCustom_isValid() {
        assertTrue(validator.isValid(selections(null, "Tester"), ctx));
        assertTrue(validator.isValid(selections(null, "  Tester  "), ctx));
    }

    @Test
    void selections_nullArchetypeWithBlankCustom_isInvalid() {
        assertFalse(validator.isValid(selections(null, null), ctx));
        assertFalse(validator.isValid(selections(null, ""), ctx));
        assertFalse(validator.isValid(selections(null, "   "), ctx));
        assertFalse(validator.isValid(selections(null, "\t"), ctx));
    }

    @Test
    void request_archetypeOnly_isValid() {
        assertTrue(validator.isValid(request(Archetype.SOFTWARE_DEVELOPER, null), ctx));
    }

    @Test
    void request_nullArchetypeWithCustom_isValid() {
        assertTrue(validator.isValid(request(null, "Tester"), ctx));
    }

    @Test
    void request_nullArchetypeWithBlankCustom_isInvalid() {
        assertFalse(validator.isValid(request(null, null), ctx));
        assertFalse(validator.isValid(request(null, ""), ctx));
        assertFalse(validator.isValid(request(null, "   "), ctx));
    }

    @Test
    void nullTarget_isAccepted() {
        // Defers to @NotNull on the field — class-level validator returns
        // true when the target container itself is null.
        assertTrue(validator.isValid(null, ctx));
    }

    @Test
    void unrelatedTarget_isAccepted() {
        // The annotation might be applied to a non-record class somewhere;
        // the validator's pattern-match returns true for any unknown type
        // so other validators handle the failure.
        assertTrue(validator.isValid("a string", ctx));
        assertTrue(validator.isValid(42, ctx));
    }

    @Test
    void exhaustiveTruthTable_perTheDataModel() {
        // Single defensive sweep through the truth table documented in
        // specs/022-role-options-custom/data-model.md.
        record Case(Archetype a, String c, boolean expected) {}
        Case[] cases = new Case[]{
                new Case(Archetype.SOFTWARE_DEVELOPER, null, true),
                new Case(Archetype.SOFTWARE_DEVELOPER, "", true),
                new Case(Archetype.SOFTWARE_DEVELOPER, "  ", true),
                new Case(Archetype.SOFTWARE_DEVELOPER, "Tester", true),
                new Case(null, "Tester", true),
                new Case(null, "  Tester  ", true),
                new Case(null, null, false),
                new Case(null, "", false),
                new Case(null, "   ", false),
        };
        for (Case tc : cases) {
            assertTrue(validator.isValid(selections(tc.a, tc.c), ctx) == tc.expected,
                    () -> "truth-table mismatch for archetype=" + tc.a + ", customRole=" + tc.c);
            assertTrue(validator.isValid(request(tc.a, tc.c), ctx) == tc.expected,
                    () -> "truth-table mismatch on AlterEgoRequest for archetype=" + tc.a + ", customRole=" + tc.c);
        }
    }

    private static AlterEgoUserSelections selections(Archetype archetype, String customRole) {
        return new AlterEgoUserSelections(
                archetype, Universe.STAR_WARS, ArtStyle.OIL_PAINTING, "Paula", null, customRole);
    }

    private static AlterEgoRequest request(Archetype archetype, String customRole) {
        return new AlterEgoRequest(
                Pose.HEROIC, archetype, Universe.STAR_WARS, Vibe.REBEL,
                ArtStyle.OIL_PAINTING, "Paula", null, customRole);
    }
}
