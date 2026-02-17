package com.spotilike.userservice.exception;

import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Getter
public abstract class BaseException extends RuntimeException {

    private final ErrorType errorType;
    private final Instant timestamp;
    private final Map<String, Object> details;

    protected BaseException(String message, ErrorType errorType) {
        this(message, errorType, null, Collections.emptyMap());
    }

    protected BaseException(String message, ErrorType errorType, Map<String, Object> details) {
        this(message, errorType, null, details);
    }

    protected BaseException(String message, ErrorType errorType, Throwable cause) {
        this(message, errorType, cause, Collections.emptyMap());
    }

    protected BaseException(String message, ErrorType errorType, Throwable cause, Map<String, Object> details) {
        super(message, cause);
        this.errorType = errorType;
        this.timestamp = Instant.now();
        this.details = details != null ? Map.copyOf(details) : Collections.emptyMap();
    }

    public String getErrorCode() { return errorType.getCode(); }
    public org.springframework.http.HttpStatus getStatus() { return errorType.getStatus(); }
}