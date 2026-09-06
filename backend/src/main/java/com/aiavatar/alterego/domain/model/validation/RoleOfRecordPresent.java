package com.aiavatar.alterego.domain.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 022 (issue #50) — class-level constraint enforcing the OR-invariant that
 * either {@code archetype} is non-null OR {@code customRole} is non-blank.
 * Applied to {@link com.aiavatar.alterego.domain.model.AlterEgoUserSelections} (the
 * public DTO) and {@link com.aiavatar.alterego.domain.model.AlterEgoRequest} (the
 * server-internal resolved record) so the prompt builders never see both
 * fields empty.
 *
 * <p>Canonical rule:
 * <pre>
 *   archetype != null  OR  (customRole != null AND !customRole.trim().isEmpty())
 * </pre>
 * Violations surface as RFC 7807 400 Bad Request via the existing
 * {@code ProblemDetailAdvice}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RoleOfRecordPresentValidator.class)
public @interface RoleOfRecordPresent {

    String message() default "either archetype must be set or customRole must be non-blank";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
