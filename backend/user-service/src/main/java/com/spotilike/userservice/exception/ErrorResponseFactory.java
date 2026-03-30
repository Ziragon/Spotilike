package com.spotilike.userservice.exception;

import com.spotilike.userservice.exception.base.ErrorResponse;
import com.spotilike.userservice.exception.base.ErrorType;
import jakarta.validation.ConstraintViolationException;
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
        return ErrorResponse.builder()
                .code(resolveCode(statusCode))
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

    private Map<String, String> mapFieldError(FieldError error) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("field", error.getField());
        entry.put("message", Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"));

        if (!SENSITIVE_FIELDS.contains(error.getField().toLowerCase())) {
            entry.put("rejected", String.valueOf(error.getRejectedValue()));
        }
        return entry;
    }

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
}