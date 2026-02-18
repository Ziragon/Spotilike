package com.spotilike.userservice.exception;

import java.util.Map;

public class ConflictException extends BaseException {
    public ConflictException(String resource, String field, Object value) {
        super(
                String.format("%s with %s '%s' already exists", resource, field, value),
                ErrorType.DUPLICATE_EMAIL,
                Map.of("resource", resource, "field", field, "value", value)
        );
    }
}