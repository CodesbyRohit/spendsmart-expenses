package com.spendsmart.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/** A business-rule validation failure tied to specific request fields, reported in the same shape as Bean Validation errors. */
public class FieldValidationException extends ApiException {

    private final List<FieldIssue> fieldErrors;

    public FieldValidationException(String field, String message) {
        this(List.of(new FieldIssue(field, message)));
    }

    public FieldValidationException(List<FieldIssue> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields need attention.");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldIssue> fieldErrors() {
        return fieldErrors;
    }

    public record FieldIssue(String field, String message) {
    }
}
