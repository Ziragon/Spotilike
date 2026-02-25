package com.spotilike.userservice.exception.conflict;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorType;

import java.util.Map;

public class ConflictException extends BaseException {

    public ConflictException(String resource, String field,
                             Object value, ErrorType errorType) {
        super(
                String.format("%s with %s '%s' already exists",
                        resource, field, value),
                errorType,
                Map.of(
                        "resource", resource,
                        "field", field
                )
        );
    }

    public ConflictException(String resource, String field, Object value) {
        this(resource, field, value, ErrorType.DUPLICATE_RESOURCE);
    }
}