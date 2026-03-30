package com.spotilike.shared.exception;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorResponse;
import com.spotilike.shared.exception.base.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
public abstract class AbstractExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String LOG_CODE_MESSAGE = "[{}] {}";

    protected abstract ErrorResponseFactory getErrorFactory();

    // BaseException
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(
            BaseException ex, HttpServletRequest request
    ) {
        logBaseException(ex);
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ErrorResponse.from(ex, request.getRequestURI()));
    }

    // ConstraintViolationException (@Validated)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request
    ) {
        log.info("[VALIDATION_ERROR] Constraint violation at {}", request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(getErrorFactory().buildValidationResponse(ex, request.getRequestURI()));
    }

    // Fallback 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request
    ) {
        String path = request.getRequestURI();

        if (ExceptionUtils.isClientAbort(ex)) {
            log.debug("Client aborted connection at {}", path);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(getErrorFactory().buildGenericResponse(
                            ErrorType.INTERNAL_ERROR, "Client disconnected",
                            HttpStatus.INTERNAL_SERVER_ERROR.value(), path));
        }

        log.error("Unexpected error at {}: {}", path, ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(getErrorFactory().buildGenericResponse(
                        ErrorType.INTERNAL_ERROR, "An unexpected error occurred",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(), path));
    }

    // Spring MVC ошибки
    @Override
    protected @Nullable ResponseEntity<@NonNull Object> handleExceptionInternal(
            @NonNull Exception ex,
            @Nullable Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest webRequest
    ) {
        String path = ExceptionUtils.extractPath(webRequest);

        if (ex instanceof MethodArgumentNotValidException validEx) {
            log.info("[VALIDATION_ERROR] Validation failed at {}", path);
            return ResponseEntity
                    .status(statusCode)
                    .headers(headers)
                    .body(getErrorFactory().buildValidationResponse(validEx, path));
        }

        if (ex instanceof AsyncRequestTimeoutException) {
            log.warn("Async request timeout at {}", path);
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .headers(headers)
                    .body(getErrorFactory().buildGenericResponse(
                            ErrorType.SERVICE_UNAVAILABLE, "Request timed out",
                            HttpStatus.SERVICE_UNAVAILABLE.value(), path));
        }

        log.info("[{}] {} at {}", statusCode.value(), ex.getClass().getSimpleName(), path);
        return ResponseEntity
                .status(statusCode)
                .headers(headers)
                .body(getErrorFactory().buildSpringMvcResponse(ex, statusCode, path));
    }

    // Логирование
    protected void logBaseException(BaseException ex) {
        switch (ex.getErrorType().getCategory()) {
            case AUTH   -> log.warn(LOG_CODE_MESSAGE, ex.getErrorCode(), ex.getMessage());
            case SYSTEM -> log.error(LOG_CODE_MESSAGE, ex.getErrorCode(), ex.getMessage(), ex);
            default     -> log.info(LOG_CODE_MESSAGE, ex.getErrorCode(), ex.getMessage());
        }
    }
}