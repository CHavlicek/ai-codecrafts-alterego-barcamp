package com.aiavatar.alterego.domain.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 029 (verbund-rebrand) — class-level constraint enforcing the OR-invariant
 * that either {@code universe} is non-null OR {@code customUniverse} is
 * non-blank. Mirrors {@link RoleOfRecordPresent}. Applied to
 * {@link com.aiavatar.alterego.domain.model.AlterEgoUserSelections} (the
 * public DTO) and {@link com.aiavatar.alterego.domain.model.AlterEgoRequest}
 * (the server-internal resolved record) so the prompt builders never see both
 * fields empty.
 *
 * <p>Canonical rule:
 * <pre>
 *   universe != null  OR  (customUniverse != null AND !customUniverse.trim().isEmpty())
 * </pre>
 * Violations surface as RFC 7807 400 Bad Request via the existing
 * {@code ProblemDetailAdvice}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniverseOfRecordPresentValidator.class)
public @interface UniverseOfRecordPresent {

    String message() default "either universe must be set or customUniverse must be non-blank";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
