package com.spotilike.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    private final SecurityContextRepository securityContextRepository =
            new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        log.debug("HeaderAuthenticationFilter called for {}",
                request.getRequestURI());

        String anonymousHeader = request.getHeader("X-User-Anonymous");

        // Обход гейтвея - аноним хедер не может быть null
        if (anonymousHeader == null) {
            log.error("Security violation: Request bypassed Gateway (missing X-User-Anonymous header)");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Direct access is prohibited");
            return;
        }

        // Аноним запрос без токена
        if ("true".equals(anonymousHeader)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Обработка хедеров
        String userIdHeader = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        String rolesHeader = request.getHeader("X-User-Roles");

        if (userIdHeader == null || email == null) {
            log.warn("Gateway failed to provide mandatory user headers for authenticated request");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal credentials");
            return;
        }

        Integer userId = Integer.parseInt(userIdHeader);
        List<String> roles = (rolesHeader != null && !rolesHeader.isBlank())
                ? Arrays.asList(rolesHeader.split(","))
                : List.of();

        UserPrincipal principal = new UserPrincipal(userId, email, roles, false);
        setAuthentication(principal, request, response);

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(UserPrincipal principal,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        var authorities = principal.roles().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        var authentication = new PreAuthenticatedAuthenticationToken(
                principal, null, authorities
        );

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}