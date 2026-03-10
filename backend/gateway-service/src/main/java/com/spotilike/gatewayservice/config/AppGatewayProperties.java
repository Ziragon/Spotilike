package com.spotilike.gatewayservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app-gateway")
public class AppGatewayProperties {

    private List<String> openPaths;
    private List<String> corsAllowedOrigins = List.of("*");
    private boolean corsAllowCredentials = false;
}