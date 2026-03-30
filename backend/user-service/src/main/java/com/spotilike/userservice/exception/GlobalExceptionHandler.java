package com.spotilike.userservice.exception;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorResponse;
import com.spotilike.userservice.exception.base.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("password", "token", "secret", "creditCard", "cvv", "ssn");

    private final Clock clock;

    public GlobalExceptionHandler() {
        this(Clock.systemUTC());
    }

    // Для тестов: new GlobalExceptionHandler(Clock.fixed(...))
    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ========================
    // Бизнес-исключения
    // ========================

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(
            BaseException ex,
            HttpServletRequest request
    ) {
        logException(ex);
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ErrorResponse.from(ex, request.getRequestURI()));
    }

    // ========================
    // Валидация @Validated на @PathVariable / @RequestParam
    // ========================

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<Map<String, String>> violations = ex.getConstraintViolations()
                .stream()
                .map(v -> Map.of(
                        "field", extractFieldName(v.getPropertyPath()),
                        "message", v.getMessage()
                ))
                .toList();

        log.info("[VALIDATION_ERROR] Constraint violation at {}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code(ErrorType.VALIDATION_ERROR.getCode())
                        .message("Validation failed")
                        .status(HttpStatus.BAD_REQUEST.value())
                        .timestamp(Instant.now(clock))
                        .path(request.getRequestURI())
                        .details(Map.of("fieldErrors", violations))
                        .build());
    }

    // ========================
    // Access Denied — ловит только то, что долетело до контроллера.
    // Для Spring Security нужен отдельный AccessDeniedHandler (см. ниже)
    // ========================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        log.warn("[{}] Access denied at {}",
                ErrorType.ACCESS_DENIED.getCode(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.builder()
                        .code(ErrorType.ACCESS_DENIED.getCode())
                        .message("Access denied")
                        .status(HttpStatus.FORBIDDEN.value())
                        .timestamp(Instant.now(clock))
                        .path(request.getRequestURI())
                        .build());
    }

    // ========================
    // Catch-all — только действительно неожиданные ошибки
    // ========================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        // Клиент отключился — не наша проблема, не пишем error
        if (isClientAbort(ex)) {
            log.debug("Client aborted connection at {}", request.getRequestURI());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .code(ErrorType.INTERNAL_ERROR.getCode())
                            .message("Client disconnected")
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .timestamp(Instant.now(clock))
                            .path(request.getRequestURI())
                            .build());
        }

        log.error("Unexpected error at {}: {}",
                request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .code(ErrorType.INTERNAL_ERROR.getCode())
                        .message("An unexpected error occurred")
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .timestamp(Instant.now(clock))
                        .path(request.getRequestURI())
                        .build());
    }

    // ========================
    // Переопределяем формат Spring-ошибок
    // ========================

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest webRequest
    ) {
        String path = extractPath(webRequest);

        // @Valid на @RequestBody — детализация по полям
        if (ex instanceof MethodArgumentNotValidException validEx) {
            List<Map<String, String>> fieldErrors = validEx.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(this::mapFieldError)
                    .toList();

            log.info("[VALIDATION_ERROR] Validation failed at {}", path);

            return ResponseEntity
                    .status(statusCode)
                    .headers(headers)
                    .body(ErrorResponse.builder()
                            .code(ErrorType.VALIDATION_ERROR.getCode())
                            .message("Validation failed")
                            .status(statusCode.value())
                            .timestamp(Instant.now(clock))
                            .path(path)
                            .details(Map.of("fieldErrors", fieldErrors))
                            .build());
        }

        // AsyncRequestTimeoutException → 503
        if (ex instanceof AsyncRequestTimeoutException) {
            log.warn("Async request timeout at {}", path);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .headers(headers)
                    .body(ErrorResponse.builder()
                            .code(ErrorType.SERVICE_UNAVAILABLE.getCode())
                            .message("Request timed out")
                            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                            .timestamp(Instant.now(clock))
                            .path(path)
                            .build());
        }

        // Все остальные Spring-исключения — единый формат
        String message = resolveMessage(ex);
        log.info("[{}] {} at {}", statusCode.value(), message, path);

        return ResponseEntity
                .status(statusCode)
                .headers(headers)
                .body(ErrorResponse.builder()
                        .code(resolveCode(statusCode))
                        .message(message)
                        .status(statusCode.value())
                        .timestamp(Instant.now(clock))
                        .path(path)
                        .build());
    }

    // ========================
    // Вспомогательные методы
    // ========================

    private String resolveMessage(Exception ex) {
        return switch (ex) {
            case HttpMessageNotReadableException e ->
                    "Malformed JSON request";
            case HttpRequestMethodNotSupportedException e ->
                    "Method " + e.getMethod() + " is not supported";
            case HttpMediaTypeNotSupportedException e ->
                    "Content type '" + e.getContentType() + "' is not supported";
            case MissingServletRequestParameterException e ->
                    "Missing required parameter: " + e.getParameterName();
            case NoResourceFoundException e ->
                    "Endpoint not found";
            case TypeMismatchException e ->
                    "Invalid value for parameter: " + e.getPropertyName();
            default ->
                    "Bad request";
        };
    }

    private String resolveCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 404 -> ErrorType.RESOURCE_NOT_FOUND.getCode();
            case 403 -> ErrorType.ACCESS_DENIED.getCode();
            case 405 -> ErrorType.METHOD_NOT_ALLOWED.getCode();
            case 415 -> ErrorType.UNSUPPORTED_MEDIA_TYPE.getCode();
            case 503 -> ErrorType.SERVICE_UNAVAILABLE.getCode();
            default  -> ErrorType.VALIDATION_ERROR.getCode();
        };
    }

    private Map<String, String> mapFieldError(FieldError error) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("field", error.getField());
        entry.put("message", Objects.requireNonNullElse(
                error.getDefaultMessage(), "Invalid value"));

        if (!SENSITIVE_FIELDS.contains(error.getField().toLowerCase())) {
            entry.put("rejected", String.valueOf(error.getRejectedValue()));
        }
        return entry;
    }

    private String extractPath(WebRequest webRequest) {
        if (webRequest instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return webRequest.getDescription(false);
    }

    private String extractFieldName(Path propertyPath) {
        String fullPath = propertyPath.toString();
        // "methodName.paramName" → "paramName"
        int lastDot = fullPath.lastIndexOf('.');
        return lastDot >= 0 ? fullPath.substring(lastDot + 1) : fullPath;
    }

    private boolean isClientAbort(Exception ex) {
        // Tomcat: org.apache.catalina.connector.ClientAbortException
        // Общий случай: IOException с "Broken pipe" / "Connection reset"
        if ("ClientAbortException".equals(ex.getClass().getSimpleName())) {
            return true;
        }
        if (ex instanceof IOException) {
            String msg = ex.getMessage();
            return msg != null && (msg.contains("Broken pipe")
                    || msg.contains("Connection reset by peer"));
        }
        return false;
    }

    private void logException(BaseException ex) {
        switch (ex.getErrorType().getCategory()) {
            case AUTH   -> log.warn("[{}] {}", ex.getErrorCode(), ex.getMessage());
            case SYSTEM -> log.error("[{}] {}", ex.getErrorCode(), ex.getMessage(), ex);
            default     -> log.info("[{}] {}", ex.getErrorCode(), ex.getMessage());
        }
    }
}