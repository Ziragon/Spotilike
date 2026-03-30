package com.spotilike.shared.exception.base;

import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Getter
public class BaseException extends RuntimeException {

    private final ErrorType errorType;
    private final Instant timestamp;
    private final transient Map<String, Object> details;

    public BaseException(String message, ErrorType errorType) {
        this(message, errorType, null, Collections.emptyMap());
    }

    public BaseException(String message, ErrorType errorType,
                         Map<String, Object> details) {
        this(message, errorType, null, details);
    }

    public BaseException(String message, ErrorType errorType,
                         Throwable cause) {
        this(message, errorType, cause, Collections.emptyMap());
    }

    public BaseException(String message, ErrorType errorType,
                         Throwable cause, Map<String, Object> details) {
        super(message, cause);
        this.errorType = errorType;
        this.timestamp = Instant.now();
        this.details = details != null
                ? Collections.unmodifiableMap(details)
                : Collections.emptyMap();
    }

    public String getErrorCode() {
        return errorType.getCode();
    }

    public org.springframework.http.HttpStatus getHttpStatus() {
        return errorType.getStatus();
    }
}