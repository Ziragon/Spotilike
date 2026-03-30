package com.spotilike.userservice.exception;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorResponse;
import com.spotilike.userservice.exception.base.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(
            BaseException ex,
            HttpServletRequest request
    ) {
        logException(ex);

        ErrorResponse body = ErrorResponse.from(ex, request.getRequestURI());

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null
                                ? error.getDefaultMessage()
                                : "Invalid value",
                        "rejected", String.valueOf(error.getRejectedValue())
                ))
                .toList();

        log.info("[{}] Validation failed at {}",
                ErrorType.VALIDATION_ERROR.getCode(), request.getRequestURI());

        ErrorResponse body = ErrorResponse.builder()
                .code(ErrorType.VALIDATION_ERROR.getCode())
                .message("Validation failed")
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .details(Map.of("fieldErrors", fieldErrors))
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        log.warn("[{}] Access denied at {}",
                ErrorType.ACCESS_DENIED.getCode(), request.getRequestURI());

        ErrorResponse body = ErrorResponse.builder()
                .code(ErrorType.ACCESS_DENIED.getCode())
                .message("Access denied")
                .status(HttpStatus.FORBIDDEN.value())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unexpected error at {}: {}",
                request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse body = ErrorResponse.builder()
                .code(ErrorType.INTERNAL_ERROR.getCode())
                .message("An unexpected error occurred")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }

    private void logException(BaseException ex) {
        switch (ex.getErrorType().getCategory()) {
            case AUTH -> log.warn("[{}] {}", ex.getErrorCode(), ex.getMessage());
            case SYSTEM -> log.error("[{}] {}", ex.getErrorCode(), ex.getMessage(), ex);
            default -> log.info("[{}] {}", ex.getErrorCode(), ex.getMessage());
        }
    }
}