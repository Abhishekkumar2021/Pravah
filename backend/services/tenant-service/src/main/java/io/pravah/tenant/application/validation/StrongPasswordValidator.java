package io.pravah.tenant.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator for {@link StrongPassword} constraint.
 *
 * <p>Password complexity requirements are enforced via regex patterns.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

  private static final int MIN_LENGTH = 8;
  private static final int MAX_LENGTH = 128;

  private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
  private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
  private static final Pattern DIGIT = Pattern.compile("[0-9]");
  private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?]");

  @Override
  public boolean isValid(String password, ConstraintValidatorContext context) {
    if (password == null) {
      return true; // Let @NotBlank handle null
    }

    if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
      setMessage(context, "Password must be between 8 and 128 characters");
      return false;
    }

    if (!UPPERCASE.matcher(password).find()) {
      setMessage(context, "Password must contain at least one uppercase letter");
      return false;
    }

    if (!LOWERCASE.matcher(password).find()) {
      setMessage(context, "Password must contain at least one lowercase letter");
      return false;
    }

    if (!DIGIT.matcher(password).find()) {
      setMessage(context, "Password must contain at least one digit");
      return false;
    }

    if (!SPECIAL.matcher(password).find()) {
      setMessage(context, "Password must contain at least one special character");
      return false;
    }

    return true;
  }

  private void setMessage(ConstraintValidatorContext context, String message) {
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
  }
}
