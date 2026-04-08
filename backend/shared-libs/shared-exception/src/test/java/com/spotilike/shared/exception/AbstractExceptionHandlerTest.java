package com.spotilike.shared.exception;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AbstractExceptionHandlerTest {

    private MockMvc mockMvc;

    @ControllerAdvice
    static class TestHandler extends AbstractExceptionHandler {
        @Override
        protected ErrorResponseFactory getErrorFactory() {
            return new ErrorResponseFactory(Clock.systemUTC());
        }
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/not-found")
        void notFound() {
            throw new BaseException("Not found", ErrorType.USER_NOT_FOUND);
        }

        @GetMapping("/auth-error")
        void authError() {
            throw new BaseException("Denied", ErrorType.ACCESS_DENIED);
        }

        @GetMapping("/system-error")
        void systemError() {
            throw new BaseException("Failure", ErrorType.INTERNAL_ERROR);
        }

        @GetMapping("/client-abort")
        void clientAbort() throws ClientAbortException {
            throw new ClientAbortException();
        }

        @GetMapping("/broken-pipe")
        void brokenPipe() throws IOException {
            throw new IOException("Broken pipe");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new NullPointerException();
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new TestHandler())
                .build();
    }

    @Nested
    @DisplayName("BaseException handling")
    class BaseExceptionHandling {

        @Test
        @DisplayName("Should return 404 for CLIENT category")
        void shouldHandleClientCategory() throws Exception {
            mockMvc.perform(get("/test/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Not found"))
                    .andExpect(jsonPath("$.path").value("/test/not-found"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Should return 403 for AUTH category")
        void shouldHandleAuthCategory() throws Exception {
            mockMvc.perform(get("/test/auth-error"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("Should return 500 for SYSTEM category")
        void shouldHandleSystemCategory() throws Exception {
            mockMvc.perform(get("/test/system-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }
    }

    @Nested
    @DisplayName("Client abort handling")
    class ClientAbortHandling {

        @Test
        @DisplayName("Should handle ClientAbortException")
        void shouldHandleClientAbort() throws Exception {
            mockMvc.perform(get("/test/client-abort"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Client disconnected"));
        }

        @Test
        @DisplayName("Should handle Broken pipe IOException")
        void shouldHandleBrokenPipe() throws Exception {
            mockMvc.perform(get("/test/broken-pipe"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Client disconnected"));
        }
    }

    @Nested
    @DisplayName("Unexpected exceptions")
    class UnexpectedExceptions {

        @Test
        @DisplayName("Should return generic 500")
        void shouldHandleUnexpected() throws Exception {
            mockMvc.perform(get("/test/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        }
    }

    @Nested
    @DisplayName("Spring MVC errors")
    class SpringMvcErrors {

        @Test
        @DisplayName("Should handle 405 Method Not Allowed")
        void shouldHandle405() throws Exception {
            mockMvc.perform(post("/test/not-found"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }
    }
}