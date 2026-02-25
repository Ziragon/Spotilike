package com.spotilike.userservice.exception;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorType;

import java.util.Map;

public class ValidationException extends BaseException {
    public ValidationException(String message) {
        super(message, ErrorType.VALIDATION_ERROR);
    }

    public ValidationException(String field, String message) {
        super(message, ErrorType.VALIDATION_ERROR,
                Map.of("field", field, "message", message));
    }
}