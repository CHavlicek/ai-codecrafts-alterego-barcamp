package com.aiavatar.alterego.domain.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean Validation constraint for the {@code firstName} field on
 * {@link com.aiavatar.alterego.domain.model.AlterEgoRequest}. Enforces feature
 * 011's input-validation rule families (length, ASCII controls, Unicode
 * invisibles, structural markers, instruction-shaped phrases).
 *
 * <p>Canonical rule list lives in {@code specs/011-input-validation/data-model.md}.
 * Implementation is {@link FirstNameValidator}; both the frontend
 * sibling at {@code frontend/src/features/alterego/validation/firstName.ts}
 * and this validator MUST stay in lock-step.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FirstNameValidator.class)
public @interface ValidFirstName {

    String message() default "firstName.invalidChars";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
