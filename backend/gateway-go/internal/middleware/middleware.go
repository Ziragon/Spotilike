package middleware

import (
	"context"
	"gateway-go/internal/auth"
	"log"
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
				log.Printf("[PANIC RECOVER] %v\n%s", err, debug.Stack())

				http.Error(w, "Internal server error", http.StatusInternalServerError)
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
				r.Header.Set("X-User-Anonymous", "true")
				next.ServeHTTP(w, r)
				return
			}

			claims, err := jwtManager.GetClaims(tokenStr)
			if err != nil || claims.UserID == 0 || claims.Subject == "" {
				r.Header.Del("Authorization")
				r.Header.Set("X-User-Anonymous", "true")

				next.ServeHTTP(w, r)
				return
			}

			if claims.UserID != 0 {
				r.Header.Set("X-User-Id", strconv.Itoa(claims.UserID))
			}

			if claims.Subject != "" {
				r.Header.Set("X-User-Email", claims.Subject)
			}

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
		if r.Header.Get("X-User-Anonymous") == "true" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		next.ServeHTTP(w, r)
	})
}
