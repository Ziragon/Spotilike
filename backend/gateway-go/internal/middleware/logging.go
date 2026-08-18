package middleware

import (
	"log/slog"
	"net/http"
	"time"
)

type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

// WriteHeader rewrite
func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}

func (rw *responseWriter) Write(b []byte) (int, error) {
	if rw.statusCode == 0 {
		rw.statusCode = http.StatusOK
	}
	return rw.ResponseWriter.Write(b)
}

func (rw *responseWriter) Status() int {
	if rw.statusCode == 0 {
		return http.StatusOK
	}
	return rw.statusCode
}

func LoggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()

		wrappedWriter := &responseWriter{
			ResponseWriter: w,
			statusCode:     0,
		}

		next.ServeHTTP(wrappedWriter, r)

		userID := r.Header.Get("X-User-Id")
		if userID == "" {
			userID = "anonymous"
		}

		status := wrappedWriter.Status()
		duration := time.Since(start)

		level := logLevelForStatus(status)

		slog.Log(
			r.Context(),
			level,
			"HTTP Request",
			"method", r.Method,
			"path", r.URL.Path,
			"status", status,
			"duration", duration,
			"request_id", r.Header.Get("X-Request-ID"),
			"user_id", userID,
			"ip", r.RemoteAddr,
		)
	})
}

func logLevelForStatus(status int) slog.Level {
	switch {
	case status >= 500:
		return slog.LevelError
	case status >= 400:
		return slog.LevelWarn
	default:
		return slog.LevelInfo
	}
}
