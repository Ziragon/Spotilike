package com.spotilike.userservice.exception.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {

    private final String code;
    private final String message;
    private final int status;
    private final Instant timestamp;
    private final String path;
    private final Map<String, Object> details;

    public static ErrorResponse from(BaseException ex, String path) {
        return ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .status(ex.getHttpStatus().value())
                .timestamp(ex.getTimestamp())
                .path(path)
                .details(ex.getDetails())
                .build();
    }
}