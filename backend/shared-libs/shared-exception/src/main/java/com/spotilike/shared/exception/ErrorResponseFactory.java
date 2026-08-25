package com.spotilike.shared.exception;

import com.spotilike.shared.exception.base.ErrorResponse;
import com.spotilike.shared.exception.base.ErrorType;
import com.spotilike.shared.exception.base.SpringMVCErrorType;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Getter
public class ErrorResponseFactory {

    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("password", "token", "secret", "creditcard", "cvv", "ssn");

    private final Clock clock;

    public ErrorResponse buildGenericResponse(ErrorType type, String message, int status, String path) {
        return ErrorResponse.builder()
                .code(type.getCode())
                .message(message)
                .status(status)
                .timestamp(Instant.now(clock))
                .path(path)
                .build();
    }

    public ErrorResponse buildValidationResponse(ConstraintViolationException ex, String path) {
        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", ExceptionUtils.extractFieldName(v.getPropertyPath()),
                        "message", v.getMessage()
                ))
                .toList();

        return buildValidationResponseWithDetails(violations, path);
    }

    public ErrorResponse buildValidationResponse(MethodArgumentNotValidException ex, String path) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::mapFieldError)
                .toList();

        return buildValidationResponseWithDetails(fieldErrors, path);
    }

    public ErrorResponse buildSpringMvcResponse(Exception ex, HttpStatusCode statusCode, String path) {
        SpringMVCErrorType type = resolveType(ex);
        return ErrorResponse.builder()
                .code(type.getCode())
                .message(resolveMessage(ex))
                .status(statusCode.value())
                .timestamp(Instant.now(clock))
                .path(path)
                .build();
    }

    private ErrorResponse buildValidationResponseWithDetails(List<Map<String, String>> details, String path) {
        return ErrorResponse.builder()
                .code(ErrorType.VALIDATION_ERROR.getCode())
                .message("Validation failed")
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(Instant.now(clock))
                .path(path)
                .details(Map.of("fieldErrors", details))
                .build();
    }

    // Maps error fields and marks with rejected sensitive fields
    private Map<String, String> mapFieldError(FieldError error) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("field", error.getField());
        entry.put("message", Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"));

        if (!isSensitive(error.getField())) {
            entry.put("rejected", String.valueOf(error.getRejectedValue()));
        }
        return entry;
    }

    // Returns message depending on the exception
    private String resolveMessage(Exception ex) {
        return switch (ex) {
            case HttpMessageNotReadableException _ -> "Malformed JSON request";
            case HttpRequestMethodNotSupportedException e -> "Method " + e.getMethod() + " is not supported";
            case HttpMediaTypeNotSupportedException e -> "Content type '" + e.getContentType() + "' is not supported";
            case MissingServletRequestParameterException e -> "Missing required parameter: " + e.getParameterName();
            case NoResourceFoundException _ -> "Endpoint not found";
            case TypeMismatchException e -> "Invalid value for parameter: " + e.getPropertyName();
            default -> "Bad request";
        };
    }

    // Returns custom SpringMVCErrorType depending on exception
    private SpringMVCErrorType resolveType(Exception ex) {
        return switch (ex) {
            case HttpMessageNotReadableException _ -> SpringMVCErrorType.MALFORMED_JSON;
            case HttpRequestMethodNotSupportedException _ -> SpringMVCErrorType.METHOD_NOT_ALLOWED;
            case HttpMediaTypeNotSupportedException _ -> SpringMVCErrorType.UNSUPPORTED_MEDIA_TYPE;
            case MissingServletRequestParameterException _ -> SpringMVCErrorType.MISSING_PARAMETER;
            case NoResourceFoundException _ -> SpringMVCErrorType.ENDPOINT_NOT_FOUND;
            case TypeMismatchException _ -> SpringMVCErrorType.TYPE_MISMATCH;
            default -> SpringMVCErrorType.BAD_REQUEST;
        };
    }

    // Sensitive fields check method
    private boolean isSensitive(String fieldName) {
        String lower = fieldName.toLowerCase();
        return SENSITIVE_FIELDS.stream().anyMatch(lower::contains);
    }
}