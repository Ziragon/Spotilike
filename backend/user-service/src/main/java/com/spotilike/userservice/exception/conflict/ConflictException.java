package com.spotilike.userservice.exception.conflict;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorType;

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