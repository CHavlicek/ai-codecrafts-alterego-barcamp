package com.aiavatar.alterego.domain.model.validation;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoUserSelections;
import com.aiavatar.alterego.domain.model.Archetype;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 022 — implements {@link RoleOfRecordPresent}. Pattern-matches on the two
 * record types that carry an (archetype, customRole) pair and asserts the
 * OR-invariant. Returns {@code true} for any other target type so unrelated
 * uses of the annotation (e.g. accidental application to a non-record class)
 * are not silently rejected — defer to other validators in that case.
 */
public class RoleOfRecordPresentValidator
        implements ConstraintValidator<RoleOfRecordPresent, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return true; // @NotNull on the field handles null containers.
        }
        if (value instanceof AlterEgoUserSelections s) {
            return hasRoleOfRecord(s.archetype(), s.customRole());
        }
        if (value instanceof AlterEgoRequest r) {
            return hasRoleOfRecord(r.archetype(), r.customRole());
        }
        return true;
    }

    private static boolean hasRoleOfRecord(Archetype archetype, String customRole) {
        return archetype != null || (customRole != null && !customRole.isBlank());
    }
}
