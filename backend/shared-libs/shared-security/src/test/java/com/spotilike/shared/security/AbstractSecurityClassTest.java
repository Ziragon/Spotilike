package com.spotilike.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractSecurityClassTest {
    // Test child class
    private static class TestSecurityFilter extends AbstractSecurityClass {
        @Override
        protected void doFilterInternal(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull FilterChain filterChain) {
            // not used in tests
        }

        // public wrappers over protected methods
        boolean callShouldNotFilter(HttpServletRequest request) {
            return shouldNotFilter(request);
        }

        void callWriteUnauthorized(HttpServletResponse response) throws IOException {
            writeUnauthorized(response, MESSAGE);
        }
    }

    private static final String MESSAGE = "Some error message";
    private TestSecurityFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new TestSecurityFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api-docs",
            "/api-docs/swagger.json",
            "/actuator/health",
            "/actuator/health/liveness"
    })
    @DisplayName("Should exclude matching paths")
    void shouldExcludeMatchingPaths(String path) {
        request.setRequestURI(path);

        assertThat(filter.callShouldNotFilter(request)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/orders",
            "/actuator/metrics",
            "/",
            ""
    })
    @DisplayName("Should not exclude not matching paths")
    void shouldNotExcludeOtherPaths(String path) {
        request.setRequestURI(path);

        assertThat(filter.callShouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("Should return correct response body")
    void writeUnauthorizedShouldSetStatusAndJsonBody() throws IOException {
        filter.callWriteUnauthorized(response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(response.getContentAsString())
                .contains("\"error\":\"UNAUTHORIZED\"")
                .contains(MESSAGE);
    }
}
