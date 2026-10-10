package com.aiavatar.alterego.domain.model.validation;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoUserSelections;
import com.aiavatar.alterego.domain.model.Universe;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 029 — implements {@link UniverseOfRecordPresent}. Pattern-matches on the two
 * record types that carry a (universe, customUniverse) pair and asserts the
 * OR-invariant. Returns {@code true} for any other target type so unrelated
 * uses of the annotation are not silently rejected — defer to other validators
 * in that case. Mirrors {@link RoleOfRecordPresentValidator}.
 */
public class UniverseOfRecordPresentValidator
        implements ConstraintValidator<UniverseOfRecordPresent, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return true; // @NotNull on the field handles null containers.
        }
        if (value instanceof AlterEgoUserSelections s) {
            return hasUniverseOfRecord(s.universe(), s.customUniverse());
        }
        if (value instanceof AlterEgoRequest r) {
            return hasUniverseOfRecord(r.universe(), r.customUniverse());
        }
        return true;
    }

    private static boolean hasUniverseOfRecord(Universe universe, String customUniverse) {
        return universe != null || (customUniverse != null && !customUniverse.isBlank());
    }
}
