package middleware

import (
	"context"
	"gateway-go/internal/auth"
	"log"
	"net/http"
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

func JwtAuthMiddleware(jwtManager *auth.JwtManager) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				http.Error(w, "missing authorization header", http.StatusUnauthorized)
				return
			}

			parts := strings.Split(authHeader, " ")
			if len(parts) != 2 || parts[0] != "Bearer" {
				http.Error(w, "invalid authorization header format", http.StatusUnauthorized)
				return
			}

			tokenStr := parts[1]

			claims, err := jwtManager.GetClaims(tokenStr)
			if err != nil {
				http.Error(w, "invalid or expired token", http.StatusUnauthorized)
				return
			}

			r.Header.Del("X-User-Id")
			r.Header.Del("X-User-Email")
			r.Header.Del("X-User-Roles")
			r.Header.Del("X-User-Anonymous")

			if claims.UserID != "" {
				r.Header.Set("X-User-Id", claims.UserID)
			}

			if claims.Subject != "" {
				r.Header.Set("X-User-Email", claims.Subject)
			}

			if len(claims.Roles) > 0 {
				r.Header.Set("X-User-Roles", strings.Join(claims.Roles, ","))
			}

			log.Printf("%v", claims)

			next.ServeHTTP(w, r)
		})
	}
}
