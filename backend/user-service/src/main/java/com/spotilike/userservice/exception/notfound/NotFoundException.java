package com.spotilike.userservice.exception.notfound;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorType;

import java.util.Map;

public class NotFoundException extends BaseException {

    public NotFoundException(String resourceType, Object identifier) {
        this(resourceType, identifier, ErrorType.RESOURCE_NOT_FOUND);
    }

    protected NotFoundException(String resourceType, Object identifier, ErrorType errorType) {
        super(
                String.format("%s not found with identifier: %s", resourceType, identifier),
                errorType,
                Map.of("resourceType", resourceType, "identifier", identifier)
        );
    }

    protected NotFoundException(String message, ErrorType errorType, Map<String, Object> details) {
        super(message, errorType, details);
    }
}