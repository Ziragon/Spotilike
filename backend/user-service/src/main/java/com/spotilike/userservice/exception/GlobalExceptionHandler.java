package com.spotilike.userservice.exception;

import com.spotilike.shared.exception.AbstractExceptionHandler;
import com.spotilike.shared.exception.ErrorResponseFactory;
import com.spotilike.shared.exception.base.ErrorResponse;
import com.spotilike.shared.exception.base.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends AbstractExceptionHandler {

    private final ErrorResponseFactory errorFactory;

    @Override
    protected ErrorResponseFactory getErrorFactory() {
        return errorFactory;
    }

    // Spring Security
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request
    ) {
        log.warn("[{}] Access denied at {}: {}",
                ErrorType.ACCESS_DENIED.getCode(),
                request.getRequestURI(),
                ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorFactory.buildGenericResponse(
                        ErrorType.ACCESS_DENIED, "Access denied",
                        HttpStatus.FORBIDDEN.value(), request.getRequestURI()));
    }
}