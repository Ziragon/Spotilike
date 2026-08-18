package middleware

import (
	"context"
	"gateway-go/internal/auth"
	"gateway-go/internal/response"
	"log/slog"
	"net/http"
	"runtime/debug"
	"strconv"
	"strings"

	"github.com/google/uuid"
)

type ctxKey string

const RequestIDKey ctxKey = "requestID"

func RequestIDMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {

		reqID := r.Header.Get("X-Request-ID")
		if reqID == "" {
			reqID = uuid.NewString()
		}

		r.Header.Set("X-Request-ID", reqID)
		w.Header().Set("X-Request-ID", reqID)

		ctx := context.WithValue(r.Context(), RequestIDKey, reqID)

		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func RecoveryMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if err := recover(); err != nil {
				slog.Error("Panic recovered",
					"error", err,
					"stack", string(debug.Stack()),
					"path", r.URL.Path,
					"request_id", r.Header.Get("X-Request-ID"),
				)

				response.SendError(w, r,
					http.StatusInternalServerError,
					"INTERNAL_ERROR",
					"An unexpected internal error occurred",
					nil,
				)
			}
		}()

		next.ServeHTTP(w, r)
	})
}

func JwtAuthMiddleware(jwtManager *auth.JwtManager) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {

			r.Header.Del("X-User-Id")
			r.Header.Del("X-User-Email")
			r.Header.Del("X-User-Roles")
			r.Header.Del("X-User-Anonymous")

			authHeader := r.Header.Get("Authorization")
			tokenStr, ok := strings.CutPrefix(authHeader, "Bearer ")

			if !ok || strings.TrimSpace(tokenStr) == "" {
				r.Header.Del("Authorization")
				r.Header.Set("X-User-Anonymous", "true")
				next.ServeHTTP(w, r)
				return
			}

			claims, err := jwtManager.GetClaims(tokenStr)
			if err != nil || claims.Valid() != nil {
				r.Header.Del("Authorization")
				r.Header.Set("X-User-Anonymous", "true")
				next.ServeHTTP(w, r)
				return
			}

			r.Header.Set("X-User-Id", strconv.FormatInt(claims.UserID, 10))
			r.Header.Set("X-User-Email", claims.Subject)

			if len(claims.Roles) > 0 {
				r.Header.Set("X-User-Roles", strings.Join(claims.Roles, ","))
			}

			r.Header.Set("X-User-Anonymous", "false")
			r.Header.Del("Authorization")

			next.ServeHTTP(w, r)
		})
	}
}

func RequireAuthMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-User-Anonymous") != "false" {
			response.SendError(w, r, http.StatusUnauthorized, "UNAUTHORIZED", "Missing or invalid token", nil)
			return
		}
		next.ServeHTTP(w, r)
	})
}
