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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
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
            var ex = new ConstraintViolationException(Set.of(
                    mockViolation()
            ));

            var response = factory.buildValidationResponse(ex, "/api/register");

            assertThat(response.getCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(extractFieldErrors(response)).hasSize(1);
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
        @DisplayName("Should hide sensitive field values")
        void shouldHideSensitiveFields() throws Exception {
            var ex = createException(List.of(
                    new FieldError("dto", "email", "bad@", false, null, null, "Invalid"),
                    new FieldError("dto", "password", "123", false, null, null, "Too short")
            ));

            var response = factory.buildValidationResponse(ex, "/api/auth");
            var errors = extractFieldErrors(response);

            assertThat(errors.get(0)).containsKey("rejected");
            assertThat(errors.get(1)).doesNotContainKey("rejected");
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

        @ParameterizedTest
        @CsvSource({
                "404, RESOURCE_NOT_FOUND",
                "403, ACCESS_DENIED",
                "405, METHOD_NOT_ALLOWED",
                "415, UNSUPPORTED_MEDIA_TYPE",
                "400, VALIDATION_ERROR"
        })
        @DisplayName("Should resolve correct error codes for HTTP statuses")
        void shouldResolveCorrectCodes(int status, String expectedCode) {
            var response = factory.buildSpringMvcResponse(
                    new RuntimeException(), HttpStatusCode.valueOf(status), "/api");

            assertThat(response.getCode()).isEqualTo(expectedCode);
        }

        @Test
        @DisplayName("Should resolve messages for specific exceptions")
        void shouldResolveMessages() {
            assertMessage(
                    new HttpMessageNotReadableException("", new MockHttpInputMessage(new byte[0])),
                    "Malformed JSON request");
            assertMessage(
                    new HttpRequestMethodNotSupportedException("PATCH"),
                    "Method PATCH is not supported");
            assertMessage(
                    new MissingServletRequestParameterException("page", "int"),
                    "Missing required parameter: page");
            assertMessage(
                    new NoResourceFoundException(HttpMethod.GET, "/unknown", "No static resource"),
                    "Endpoint not found");
        }

        @Test
        @DisplayName("Should return default message for unknown exception types")
        void shouldReturnDefaultMessage() {
            var response = factory.buildSpringMvcResponse(
                    new IllegalStateException(), HttpStatus.BAD_REQUEST, "/api");

            assertThat(response.getMessage()).isEqualTo("Bad request");
        }

        private void assertMessage(Exception ex, String expected) {
            var response = factory.buildSpringMvcResponse(ex, HttpStatus.BAD_REQUEST, "/api");
            assertThat(response.getMessage()).isEqualTo(expected);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractFieldErrors(ErrorResponse response) {
        return (List<Map<String, String>>) response.getDetails().get("fieldErrors");
    }
}