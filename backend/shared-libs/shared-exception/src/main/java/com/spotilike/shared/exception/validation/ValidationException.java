package com.spotilike.shared.exception.validation;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;

import java.util.List;
import java.util.Map;

public class ValidationException extends BaseException {

    public ValidationException(String message) {
        super(message, ErrorType.VALIDATION_ERROR);
    }

    public ValidationException(String field, String message) {
        super(
                String.format("Validation failed for field '%s': %s", field, message),
                ErrorType.VALIDATION_ERROR,
                Map.of(
                        "field", field,
                        "message", message
                )
        );
    }

    public ValidationException(List<Map<String, String>> fieldErrors) {
        super(
                "Validation failed",
                ErrorType.VALIDATION_ERROR,
                Map.of("fieldErrors", fieldErrors)
        );
    }
}