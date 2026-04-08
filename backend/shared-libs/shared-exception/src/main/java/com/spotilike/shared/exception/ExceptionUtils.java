package com.spotilike.shared.exception;

import jakarta.validation.Path;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;

public final class ExceptionUtils {

    private ExceptionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String extractPath(WebRequest webRequest) {
        if (webRequest instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return webRequest.getDescription(false);
    }

    public static String extractFieldName(Path propertyPath) {
        String fullPath = propertyPath.toString();
        int lastDot = fullPath.lastIndexOf('.');
        return lastDot >= 0 ? fullPath.substring(lastDot + 1) : fullPath;
    }

    public static boolean isClientAbort(Exception ex) {
        if (ex instanceof ClientAbortException) {
            return true;
        }
        if (ex instanceof IOException) {
            String msg = ex.getMessage();
            return msg != null && (msg.contains("Broken pipe")
                    || msg.contains("Connection reset by peer"));
        }
        return false;
    }
}