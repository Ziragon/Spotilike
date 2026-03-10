package com.spotilike.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String anonymousHeader = request.getHeader("X-User-Anonymous");

        if (anonymousHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean anonymous = "true".equals(anonymousHeader);

        if (anonymous) {
            UserPrincipal principal = new UserPrincipal(null, null, List.of(), true);
            setAuthentication(principal);
        } else {
            String userIdHeader = request.getHeader("X-User-Id");
            String email = request.getHeader("X-User-Email");
            String rolesHeader = request.getHeader("X-User-Roles");

            if (userIdHeader == null || email == null) {
                log.warn("Missing required user headers");
                filterChain.doFilter(request, response);
                return;
            }

            Integer userId = Integer.parseInt(userIdHeader);
            List<String> roles = (rolesHeader != null && !rolesHeader.isBlank())
                    ? Arrays.asList(rolesHeader.split(","))
                    : List.of();

            UserPrincipal principal = new UserPrincipal(userId, email, roles, false);
            setAuthentication(principal);
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(UserPrincipal principal) {
        var authorities = principal.roles().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        var authentication = new PreAuthenticatedAuthenticationToken(
                principal,    // principal
                null,         // credentials (jwt уже проверен)
                authorities   // granted authorities
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}