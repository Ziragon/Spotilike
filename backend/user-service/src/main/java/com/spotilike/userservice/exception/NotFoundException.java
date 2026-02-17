package com.spotilike.userservice.exception;

import java.util.Map;

public class NotFoundException extends BaseException {

    public NotFoundException(String resourceType, Object identifier) {
        super(
                String.format("%s not found with id: %s", resourceType, identifier),
                ErrorType.RESOURCE_NOT_FOUND,
                Map.of("resourceType", resourceType, "identifier", identifier)
        );
    }

    protected NotFoundException(String message, ErrorType errorType, Map<String, Object> details) {
        super(message, errorType, details);
    }
}