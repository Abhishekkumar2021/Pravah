package io.pravah.tenant.application.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Validates that a password meets complexity requirements.
 *
 * <p>Requirements:
 *
 * <ul>
 *   <li>8-128 characters
 *   <li>At least one uppercase letter
 *   <li>At least one lowercase letter
 *   <li>At least one digit
 *   <li>At least one special character (!@#$%^&*()_+-=[]{}|;':\",./<>?)
 * </ul>
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface StrongPassword {

  String message() default
      "Password must be 8-128 characters and include uppercase, lowercase, digit, and special character";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
