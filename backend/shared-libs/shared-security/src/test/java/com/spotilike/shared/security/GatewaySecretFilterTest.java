package com.spotilike.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecretFilterTest {

    private GatewaySecretFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    private static final String HEADER_NAME = "X-Gateway-Secret";
    private static final String SECRET = "super-secret-value";

    @BeforeEach
    void setUp() {
        filter = new GatewaySecretFilter(HEADER_NAME, SECRET);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @Nested
    @DisplayName("Correct header")
    class BypassFilter {
        @Test
        @DisplayName("Continue with correct header")
        void shouldContinueWithCorrectHeader() throws Exception {
            request.addHeader(HEADER_NAME, SECRET);

            filter.doFilter(request, response, filterChain);

            assertThat(filterChain.getRequest()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Incorrect header")
    class IncorrectHeader {
        @Test
        void shouldReturn401WithoutHeader() throws Exception {
            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        void shouldReturn401WithIncorrectKey() throws Exception {
            request.addHeader(HEADER_NAME, "Incorrect key");

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        void shouldReturn401WithEmptyKey() throws Exception {
            request.addHeader(HEADER_NAME, "");

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }
    }
}
