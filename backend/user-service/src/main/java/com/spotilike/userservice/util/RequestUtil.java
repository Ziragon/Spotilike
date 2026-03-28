package com.spotilike.userservice.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUtil {
    private RequestUtil() {}

    public static String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    public static String extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return (userAgent != null && !userAgent.isBlank()) ? userAgent : "unknown";
    }
}
