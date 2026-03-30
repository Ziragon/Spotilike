package com.spotilike.shared.exception;

import jakarta.validation.Path;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExceptionUtilsTest {

    @Nested
    @DisplayName("extractPath()")
    class ExtractPath {

        @Test
        @DisplayName("Should extract URI from ServletWebRequest")
        void shouldExtractFromServletWebRequest() {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/users/123");

            assertThat(ExceptionUtils.extractPath(new ServletWebRequest(request)))
                    .isEqualTo("/api/users/123");
        }

        @Test
        @DisplayName("Should fallback to description for non-servlet WebRequest")
        void shouldFallbackForNonServletRequest() {
            var webRequest = mock(WebRequest.class);
            when(webRequest.getDescription(false)).thenReturn("uri=/fallback");

            assertThat(ExceptionUtils.extractPath(webRequest)).isEqualTo("uri=/fallback");
        }
    }

    @Nested
    @DisplayName("extractFieldName()")
    class ExtractFieldName {

        @Test
        @DisplayName("Should extract field name from nested path")
        void shouldExtractFromNestedPath() {
            var path = mockPath("user.credentials.password");
            assertThat(ExceptionUtils.extractFieldName(path)).isEqualTo("password");
        }

        @Test
        @DisplayName("Should return same name for simple field")
        void shouldReturnSimpleFieldAsIs() {
            var path = mockPath("email");
            assertThat(ExceptionUtils.extractFieldName(path)).isEqualTo("email");
        }

        @Test
        @DisplayName("Should handle empty path")
        void shouldHandleEmptyPath() {
            var path = mockPath("");
            assertThat(ExceptionUtils.extractFieldName(path)).isEmpty();
        }

        private Path mockPath(String value) {
            var path = mock(Path.class);
            when(path.toString()).thenReturn(value);
            return path;
        }
    }

    @Nested
    @DisplayName("isClientAbort()")
    class IsClientAbort {

        @Test
        @DisplayName("Should return true for ClientAbortException")
        void shouldDetectClientAbortException() {
            assertThat(ExceptionUtils.isClientAbort(new ClientAbortException())).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Broken pipe", "Connection reset by peer"})
        @DisplayName("Should detect client abort keywords in IOException")
        void shouldDetectAbortKeywords(String message) {
            assertThat(ExceptionUtils.isClientAbort(new IOException(message))).isTrue();
        }

        @Test
        @DisplayName("Should return false for regular exceptions")
        void shouldReturnFalseForRegularExceptions() {
            assertThat(ExceptionUtils.isClientAbort(new IOException("File not found"))).isFalse();
            assertThat(ExceptionUtils.isClientAbort(new RuntimeException("error"))).isFalse();
            assertThat(ExceptionUtils.isClientAbort(new IOException((String) null))).isFalse();
        }
    }

    @Nested
    @DisplayName("Utility class protection")
    class UtilityClassProtection {

        @Test
        @DisplayName("Should prevent instantiation via reflection")
        void shouldPreventInstantiation() throws NoSuchMethodException {
            var constructor = ExceptionUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            assertThatThrownBy(constructor::newInstance)
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(UnsupportedOperationException.class);
        }
    }
}