package com.spotilike.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAuthenticationFilterTest {

    private HeaderAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new HeaderAuthenticationFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Bypass Gateway without X-User-Anonymous")
    class BypassGateway {

        @Test
        @DisplayName("401 with JSON")
        void shouldReturn401WithJsonBody() throws Exception {
            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
            assertThat(response.getContentAsString())
                    .contains("UNAUTHORIZED")
                    .contains("Direct access is prohibited");
        }

        @Test
        @DisplayName("The filter chain is not invoked")
        void shouldNotContinueFilterChain() throws Exception {
            filter.doFilter(request, response, filterChain);

            assertThat(filterChain.getRequest()).isNull();
        }
    }

    @Nested
    @DisplayName("Anonymous request")
    class AnonymousRequest {

        @Test
        @DisplayName("Continue without auth")
        void shouldContinueWithoutAuthentication() throws Exception {
            request.addHeader("X-User-Anonymous", "true");

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("Authenticated request")
    class AuthenticatedRequest {

        @BeforeEach
        void setUpHeaders() {
            request.addHeader("X-User-Anonymous", "false");
        }

        @Test
        @DisplayName("Set up SecurityContext with valid headers")
        void shouldSetSecurityContext() throws Exception {
            request.addHeader("X-User-Id", "42");
            request.addHeader("X-User-Email", "test@mail.com");
            request.addHeader("X-User-Roles", "ROLE_USER,ROLE_ADMIN");

            filter.doFilter(request, response, filterChain);

            assertThat(filterChain.getRequest()).isNotNull();

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();

            var principal = (UserPrincipal) auth.getPrincipal();
            assertThat(principal).isNotNull();

            assertThat(principal.userId()).isEqualTo(42);
            assertThat(principal.roles()).containsExactly("ROLE_USER", "ROLE_ADMIN");
        }

        @Test
        @DisplayName("401 without X-User-Id")
        void shouldReturn401WhenUserIdMissing() throws Exception {
            request.addHeader("X-User-Email", "test@mail.com");

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        @DisplayName("401 without X-User-Email")
        void shouldReturn401WhenEmailMissing() throws Exception {
            request.addHeader("X-User-Id", "1");

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        @DisplayName("401 with invalid X-User-Id")
        void shouldReturn401WhenUserIdNotNumeric() throws Exception {
            request.addHeader("X-User-Id", "abc");
            request.addHeader("X-User-Email", "test@mail.com");

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        @DisplayName("Should work without roles")
        void shouldWorkWithoutRoles() throws Exception {
            request.addHeader("X-User-Id", "1");
            request.addHeader("X-User-Email", "test@mail.com");

            filter.doFilter(request, response, filterChain);

            assertThat(filterChain.getRequest()).isNotNull();

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();

            var principal = (UserPrincipal) auth.getPrincipal();
            assertThat(principal).isNotNull();
            assertThat(principal.roles()).isEmpty();
        }
    }
}