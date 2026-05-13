package io.pravah.common.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when input validation fails.
 */
public class ValidationException extends PravahException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final List<FieldError> fieldErrors;

    public ValidationException(String message) {
        super("VALIDATION_FAILED", message);
        this.fieldErrors = Collections.emptyList();
    }

    public ValidationException(List<FieldError> fieldErrors) {
        super("VALIDATION_FAILED", "Validation failed: " + fieldErrors.size() + " error(s)");
        this.fieldErrors = Collections.unmodifiableList(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    @Override
    public int suggestedHttpStatus() {
        return 400;
    }

    /**
     * Represents a validation error on a specific field.
     */
    public record FieldError(String field, String message, Object rejectedValue) {
        public FieldError(String field, String message) {
            this(field, message, null);
        }
    }
}
