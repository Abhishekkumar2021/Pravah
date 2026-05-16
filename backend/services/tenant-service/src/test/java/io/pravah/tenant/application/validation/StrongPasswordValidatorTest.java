package io.pravah.tenant.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StrongPasswordValidatorTest {

  private final StrongPasswordValidator validator = new StrongPasswordValidator();
  private ConstraintValidatorContext context;
  private ConstraintValidatorContext.ConstraintViolationBuilder builder;

  @BeforeEach
  void setUp() {
    context = mock(ConstraintValidatorContext.class);
    builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
    org.mockito.Mockito.when(context.buildConstraintViolationWithTemplate(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(builder);
    org.mockito.Mockito.when(builder.addConstraintViolation()).thenReturn(context);
  }

  @Test
  void acceptsNullPassword() {
    assertThat(validator.isValid(null, context)).isTrue();
  }

  @Test
  void acceptsStrongPassword() {
    assertThat(validator.isValid("PravahDev1!", context)).isTrue();
  }

  @Test
  void rejectsShortPassword() {
    assertThat(validator.isValid("Ab1!", context)).isFalse();
    verify(context).disableDefaultConstraintViolation();
  }

  @Test
  void rejectsMissingUppercase() {
    assertThat(validator.isValid("pravahdev1!", context)).isFalse();
  }

  @Test
  void rejectsMissingDigit() {
    assertThat(validator.isValid("PravahDev!!", context)).isFalse();
  }

  @Test
  void rejectsMissingSpecialCharacter() {
    assertThat(validator.isValid("PravahDev1", context)).isFalse();
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(context).buildConstraintViolationWithTemplate(captor.capture());
    assertThat(captor.getValue()).contains("special");
  }
}
