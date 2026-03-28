package com.spotilike.userservice.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestUtilTest {

    private HttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockRequest = mock(HttpServletRequest.class);
    }

    @Nested
    @DisplayName("extractClientIp")
    class ExtractClientIp {

        @Test
        @DisplayName("Возвращает первый IP из X-Forwarded-For")
        void shouldReturnFirstFromForwardedFor() {
            when(mockRequest.getHeader("X-Forwarded-For"))
                    .thenReturn("10.0.0.1, 192.168.1.1");

            assertThat(RequestUtil.extractClientIp(mockRequest))
                    .isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("Возвращает remoteAddr если X-Forwarded-For пустой")
        void shouldFallbackToRemoteAddr() {
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("");
            when(mockRequest.getRemoteAddr()).thenReturn("127.0.0.1");

            assertThat(RequestUtil.extractClientIp(mockRequest))
                    .isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Возвращает remoteAddr если X-Forwarded-For null")
        void shouldFallbackWhenHeaderMissing() {
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getRemoteAddr()).thenReturn("172.16.0.1");

            assertThat(RequestUtil.extractClientIp(mockRequest))
                    .isEqualTo("172.16.0.1");
        }
    }

    @Nested
    @DisplayName("extractDeviceInfo")
    class ExtractDeviceInfo {

        @Test
        @DisplayName("Возвращает User-Agent")
        void shouldReturnUserAgent() {
            when(mockRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

            assertThat(RequestUtil.extractDeviceInfo(mockRequest))
                    .isEqualTo("Mozilla/5.0");
        }

        @Test
        @DisplayName("Возвращает 'unknown' если User-Agent пустой")
        void shouldReturnUnknownWhenEmpty() {
            when(mockRequest.getHeader("User-Agent")).thenReturn("");

            assertThat(RequestUtil.extractDeviceInfo(mockRequest))
                    .isEqualTo("unknown");
        }

        @Test
        @DisplayName("Возвращает 'unknown' если User-Agent null")
        void shouldReturnUnknownWhenNull() {
            when(mockRequest.getHeader("User-Agent")).thenReturn(null);

            assertThat(RequestUtil.extractDeviceInfo(mockRequest))
                    .isEqualTo("unknown");
        }
    }
}