package com.spotilike.userservice.config;

import com.spotilike.shared.security.GatewaySecretFilter;
import com.spotilike.shared.security.HeaderAuthenticationFilter;
import lombok.SneakyThrows;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/api/v1/auth/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public GatewaySecretFilter gatewaySecretFilter() {
        return new GatewaySecretFilter();
    }

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    // ОБЯЗАТЕЛЬНО: Отключаем глобальную авторегистрацию для GatewaySecretFilter
    @Bean
    public FilterRegistrationBean<GatewaySecretFilter> disableGatewaySecretAutoRegistration(
            GatewaySecretFilter filter) {
        FilterRegistrationBean<GatewaySecretFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<HeaderAuthenticationFilter> disableHeaderAuthAutoRegistration(
            HeaderAuthenticationFilter filter) {
        FilterRegistrationBean<HeaderAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @SneakyThrows
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GatewaySecretFilter gatewaySecretFilter,
            HeaderAuthenticationFilter headerAuthFilter) {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(gatewaySecretFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(headerAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}