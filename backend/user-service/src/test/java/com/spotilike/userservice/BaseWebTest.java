package com.spotilike.userservice;

import com.spotilike.userservice.config.SecurityConfig;
import com.spotilike.userservice.security.HeaderAuthenticationFilter;
import com.spotilike.userservice.service.AuthService;
import com.spotilike.userservice.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({HeaderAuthenticationFilter.class, SecurityConfig.class})
public abstract class BaseWebTest {
    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected AuthService authService;

    @MockitoBean
    protected JwtService jwtService;
}