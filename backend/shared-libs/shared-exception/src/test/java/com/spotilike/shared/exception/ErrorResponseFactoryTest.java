package com.spotilike.shared.exception;

import com.spotilike.shared.exception.base.ErrorResponse;
import com.spotilike.shared.exception.base.ErrorType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorResponseFactoryTest {

    private ErrorResponseFactory factory;
    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-15T12:00:00Z");

    @BeforeEach
    void setUp() {
        factory = new ErrorResponseFactory(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("buildGenericResponse()")
    class BuildGenericResponse {

        @Test
        @DisplayName("Should build complete response with all fields")
        void shouldBuildCompleteResponse() {
            var response = factory.buildGenericResponse(
                    ErrorType.INTERNAL_ERROR, "Something went wrong", 500, "/api/test");

            assertThat(response.getCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getMessage()).isEqualTo("Something went wrong");
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getPath()).isEqualTo("/api/test");
            assertThat(response.getTimestamp()).isEqualTo(FIXED_INSTANT);
        }
    }

    @Nested
    @DisplayName("buildValidationResponse(ConstraintViolationException)")
    class BuildConstraintViolationResponse {

        @Test
        @DisplayName("Should build response with field violations")
        void shouldBuildWithViolations() {
            var ex = new ConstraintViolationException(Set.of(mockViolation()));

            var response = factory.buildValidationResponse(ex, "/api/register");
            var errors = extractFieldErrors(response);

            assertThat(response.getCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst()).containsEntry("field", "email");
            assertThat(errors.getFirst()).containsEntry("message", "must not be blank");
        }

        private ConstraintViolation<?> mockViolation() {
            var violation = mock(ConstraintViolation.class);
            var path = mock(Path.class);
            when(path.toString()).thenReturn("user.email");
            when(violation.getPropertyPath()).thenReturn(path);
            when(violation.getMessage()).thenReturn("must not be blank");
            return violation;
        }
    }

    @Nested
    @DisplayName("buildValidationResponse(MethodArgumentNotValidException)")
    class BuildMethodArgumentResponse {

        @Test
        @DisplayName("Should hide fields containing sensitive keywords")
        void shouldHideFieldsContainingSensitiveKeywords() throws Exception {
            var ex = createException(List.of(
                    new FieldError("dto", "newPassword", "abc", false, null, null, "Too short"),
                    new FieldError("dto", "confirmPassword", "abc", false, null, null, "Mismatch"),
                    new FieldError("dto", "username", "ok", false, null, null, null)
            ));

            var errors = extractFieldErrors(factory.buildValidationResponse(ex, "/api"));

            assertThat(errors.get(0)).doesNotContainKey("rejected"); // newPassword
            assertThat(errors.get(1)).doesNotContainKey("rejected"); // confirmPassword
            assertThat(errors.get(2)).containsKey("rejected"); // username
        }

        @Test
        @DisplayName("Should use 'Invalid value' when message is null")
        void shouldUseDefaultMessage() throws Exception {
            var ex = createException(List.of(
                    new FieldError("dto", "field", "val", false, null, null, null)
            ));

            var errors = extractFieldErrors(factory.buildValidationResponse(ex, "/api"));
            assertThat(errors.getFirst()).containsEntry("message", "Invalid value");
        }

        @Test
        @DisplayName("Should set VALIDATION_ERROR code and 400 status")
        void shouldSetValidationErrorCodeAndStatus() throws Exception {
            var ex = createException(List.of(
                    new FieldError("dto", "email", "bad@", false, null, null, "Invalid")
            ));

            var response = factory.buildValidationResponse(ex, "/api/auth");

            assertThat(response.getCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should set generic 'Validation failed' message")
        void shouldSetGenericValidationMessage() throws Exception {
            var ex = createException(List.of(new FieldError("dto", "email", "bad@", false, null, null, "Invalid")));
            var response = factory.buildValidationResponse(ex, "/api");

            assertThat(response.getMessage()).isEqualTo("Validation failed");
        }

        private MethodArgumentNotValidException createException(List<FieldError> errors) throws Exception {
            var bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(errors);
            var param = new MethodParameter(getClass().getDeclaredMethod("dummyMethod"), -1);
            return new MethodArgumentNotValidException(param, bindingResult);
        }

        @SuppressWarnings("java:S1186")
        private void dummyMethod() {}
    }

    @Nested
    @DisplayName("buildSpringMvcResponse()")
    class BuildSpringMvcResponse {

        @Test
        @DisplayName("Should populate status, path and timestamp correctly")
        void shouldPopulateCommonFields() {
            var ex = new HttpRequestMethodNotSupportedException("PATCH");
            var response = factory.buildSpringMvcResponse(ex, HttpStatus.METHOD_NOT_ALLOWED, "/api/orders");

            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getPath()).isEqualTo("/api/orders");
            assertThat(response.getTimestamp()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        @DisplayName("Should resolve code and message for HttpMessageNotReadableException")
        void shouldResolveMalformedJson() {
            var ex = new HttpMessageNotReadableException("", new MockHttpInputMessage(new byte[0]));
            var response = factory.buildSpringMvcResponse(ex, HttpStatus.BAD_REQUEST, "/api");

            assertThat(response.getCode()).isEqualTo("MALFORMED_REQUEST");
            assertThat(response.getMessage()).isEqualTo("Malformed JSON request");
        }

        @Test
        @DisplayName("Should resolve code and message for HttpRequestMethodNotSupportedException")
        void shouldResolveMethodNotAllowed() {
            var ex = new HttpRequestMethodNotSupportedException("PATCH");
            var response = factory.buildSpringMvcResponse(ex, HttpStatus.METHOD_NOT_ALLOWED, "/api");

            assertThat(response.getCode()).isEqualTo("METHOD_NOT_ALLOWED");
            assertThat(response.getMessage()).isEqualTo("Method PATCH is not supported");
        }

        @Test
        @DisplayName("Should resolve code and message for MissingServletRequestParameterException")
        void shouldResolveMissingParameter() {
            var ex = new MissingServletRequestParameterException("page", "int");
            var response = factory.buildSpringMvcResponse(ex, HttpStatus.BAD_REQUEST, "/api");

            assertThat(response.getCode()).isEqualTo("MISSING_PARAMETER");
            assertThat(response.getMessage()).isEqualTo("Missing required parameter: page");
        }

        @Test
        @DisplayName("Should resolve code and message for NoResourceFoundException")
        void shouldResolveEndpointNotFound() {
            var ex = new NoResourceFoundException(HttpMethod.GET, "/unknown", "No static resource");
            var response = factory.buildSpringMvcResponse(ex, HttpStatus.NOT_FOUND, "/api");

            assertThat(response.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
            assertThat(response.getMessage()).isEqualTo("Endpoint not found");
        }

        @Test
        @DisplayName("Should resolve code and message for TypeMismatchException")
        void shouldResolveTypeMismatch() {
            var ex = mock(TypeMismatchException.class);
            when(ex.getPropertyName()).thenReturn("id");
            var response = factory.buildSpringMvcResponse(ex, HttpStatus.BAD_REQUEST, "/api");

            assertThat(response.getCode()).isEqualTo("TYPE_MISMATCH");
            assertThat(response.getMessage()).isEqualTo("Invalid value for parameter: id");
        }

        @Test
        @DisplayName("Should fall back to default code/message for unknown exception types")
        void shouldResolveDefault() {
            var response = factory.buildSpringMvcResponse(
                    new IllegalStateException(), HttpStatus.BAD_REQUEST, "/api");

            assertThat(response.getCode()).isEqualTo("BAD_REQUEST");
            assertThat(response.getMessage()).isEqualTo("Bad request");
        }

        @Test
        @DisplayName("Should resolve code and message for HttpMediaTypeNotSupportedException")
        void shouldResolveUnsupportedMediaType() {
            var ex = mock(HttpMediaTypeNotSupportedException.class);
            when(ex.getContentType()).thenReturn(MediaType.TEXT_PLAIN);
            var response = factory.buildSpringMvcResponse(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "/api");

            assertThat(response.getCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
            assertThat(response.getMessage()).contains("is not supported");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractFieldErrors(ErrorResponse response) {
        return (List<Map<String, String>>) response.getDetails().get("fieldErrors");
    }
}