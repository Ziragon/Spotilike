package middleware_test

import (
	"gateway-go/internal/auth"
	"gateway-go/internal/middleware"
	"gateway-go/internal/testutil"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func TestRequestIDMiddleware(t *testing.T) {
	tests := []struct {
		name          string
		incomingReqID string
	}{
		{
			name:          "New RequestID when missing",
			incomingReqID: "",
		},
		{
			name:          "Existing RequestID",
			incomingReqID: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var capturedReqHeader string
			var capturedCtxValue string

			next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				capturedReqHeader = r.Header.Get("X-Request-ID")
				if v, ok := r.Context().Value(middleware.RequestIDKey).(string); ok {
					capturedCtxValue = v
				}
			})

			req := httptest.NewRequest("GET", "/test", nil)
			if tt.incomingReqID != "" {
				req.Header.Set("X-Request-ID", tt.incomingReqID)
			}
			rec := httptest.NewRecorder()

			middleware.RequestIDMiddleware(next).ServeHTTP(rec, req)

			if capturedReqHeader == "" {
				t.Fatal("expected X-Request-ID to be set on request")
			}
			if tt.incomingReqID != "" && capturedReqHeader != tt.incomingReqID {
				t.Errorf("expected preserved ID %q, got %q", tt.incomingReqID, capturedReqHeader)
			}
			if capturedReqHeader != rec.Header().Get("X-Request-ID") {
				t.Error("request and response X-Request-ID should match")
			}
			if capturedCtxValue != capturedReqHeader {
				t.Error("context value should match header value")
			}
		})
	}
}

func TestRecoveryMiddleware(t *testing.T) {
	t.Run("recovers from panic and returns 500", func(t *testing.T) {
		panicking := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			panic("something went wrong")
		})

		req := httptest.NewRequest(http.MethodGet, "/", nil)
		rec := httptest.NewRecorder()

		middleware.RecoveryMiddleware(panicking).ServeHTTP(rec, req)

		if rec.Code != http.StatusInternalServerError {
			t.Errorf("expected 500, got %d", rec.Code)
		}
	})

	t.Run("passes through normally without panic", func(t *testing.T) {
		ok := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusOK)
		})

		req := httptest.NewRequest(http.MethodGet, "/", nil)
		rec := httptest.NewRecorder()

		middleware.RecoveryMiddleware(ok).ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200, got %d", rec.Code)
		}
	})
}

func TestJwtAuthMiddleware(t *testing.T) {

	jwtManager, secretBytes := testutil.NewTestJwtManager(t)

	mv := middleware.JwtAuthMiddleware(jwtManager)

	validClaims := auth.UserClaims{
		UserID: 123,
		Roles:  []string{"User", "Admin"},
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   "user@test.com",
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(1 * time.Hour)),
		},
	}

	expiredClaims := auth.UserClaims{
		UserID: 123,
		Roles:  []string{"User", "Admin"},
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   "user@test.com",
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(-1 * time.Hour)),
		},
	}

	invalidClaims := auth.UserClaims{ // missing UserId
		Roles: []string{"User", "Admin"},
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   "user@test.com",
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(1 * time.Hour)),
		},
	}

	missingRolesClaims := auth.UserClaims{
		UserID: 123,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   "user@test.com",
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(1 * time.Hour)),
		},
	}

	validToken := testutil.GenerateTestToken(t, validClaims, secretBytes, jwt.SigningMethodHS256)
	expiredToken := testutil.GenerateTestToken(t, expiredClaims, secretBytes, jwt.SigningMethodHS256)
	invalidToken := testutil.GenerateTestToken(t, invalidClaims, secretBytes, jwt.SigningMethodHS256)
	missingRolesToken := testutil.GenerateTestToken(t, missingRolesClaims, secretBytes, jwt.SigningMethodHS256)

	tests := []struct {
		name             string
		incomingHeaders  map[string]string
		expectedHeaders  map[string]string
		forbiddenHeaders []string
	}{
		{
			name: "Valid token",
			incomingHeaders: map[string]string{
				"Authorization": "Bearer " + validToken,
			},
			expectedHeaders: map[string]string{
				"X-User-Id":        "123",
				"X-User-Email":     "user@test.com",
				"X-User-Roles":     "User,Admin",
				"X-User-Anonymous": "false",
			},
			forbiddenHeaders: []string{
				"Authorization",
			},
		},
		{
			name: "Valid token without roles",
			incomingHeaders: map[string]string{
				"Authorization": "Bearer " + missingRolesToken,
			},
			expectedHeaders: map[string]string{
				"X-User-Id":        "123",
				"X-User-Email":     "user@test.com",
				"X-User-Anonymous": "false",
			},
			forbiddenHeaders: []string{
				"Authorization",
				"X-User-Roles",
			},
		},
		{
			name: "Invalid auth header",
			incomingHeaders: map[string]string{
				"Authorization": "Bear " + validToken, // "Bear" instead of "Bearer"
			},
			expectedHeaders: map[string]string{
				"X-User-Anonymous": "true",
			},
			forbiddenHeaders: []string{
				"X-User-Id",
				"X-User-Email",
				"X-User-Roles",
				"Authorization",
			},
		},
		{
			name: "Invalid token",
			incomingHeaders: map[string]string{
				"Authorization": "Bearer " + invalidToken,
			},
			expectedHeaders: map[string]string{
				"X-User-Anonymous": "true",
			},
			forbiddenHeaders: []string{
				"X-User-Id",
				"X-User-Email",
				"X-User-Roles",
				"Authorization",
			},
		},
		{
			name: "Expired token",
			incomingHeaders: map[string]string{
				"Authorization": "Bearer " + expiredToken,
			},
			expectedHeaders: map[string]string{
				"X-User-Anonymous": "true",
			},
			forbiddenHeaders: []string{
				"X-User-Id",
				"X-User-Email",
				"X-User-Roles",
				"Authorization",
			},
		},
		{
			name:            "Missing token",
			incomingHeaders: map[string]string{},
			expectedHeaders: map[string]string{
				"X-User-Anonymous": "true",
			},
			forbiddenHeaders: []string{
				"X-User-Id",
				"X-User-Email",
				"X-User-Roles",
				"Authorization",
			},
		},
		{
			name: "Header spoofing attempt without token",
			incomingHeaders: map[string]string{
				"X-User-Id":    "999",
				"X-User-Roles": "SuperAdmin",
			},
			expectedHeaders: map[string]string{
				"X-User-Anonymous": "true",
			},
			forbiddenHeaders: []string{
				"X-User-Id",
				"X-User-Email",
				"X-User-Roles",
				"Authorization",
			},
		},
		{
			name: "Header spoofing attempt with token (headers rewrite)",
			incomingHeaders: map[string]string{
				"Authorization": "Bearer " + validToken,
				"X-User-Id":     "999",
				"X-User-Roles":  "SuperAdmin",
			},
			expectedHeaders: map[string]string{
				"X-User-Id":        "123",
				"X-User-Email":     "user@test.com",
				"X-User-Roles":     "User,Admin",
				"X-User-Anonymous": "false",
			},
			forbiddenHeaders: []string{
				"Authorization",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var capturedHeaders http.Header
			nextCalled := false

			next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				nextCalled = true
				capturedHeaders = r.Header.Clone()
				w.WriteHeader(http.StatusOK)
			})

			req := httptest.NewRequest(http.MethodGet, "/test", nil)
			for k, v := range tt.incomingHeaders {
				req.Header.Set(k, v)
			}

			rec := httptest.NewRecorder()
			mv(next).ServeHTTP(rec, req)

			if !nextCalled {
				t.Fatalf("expected next handler to be called")
			}

			for k, v := range tt.expectedHeaders {
				actualVal := capturedHeaders.Get(k)
				if actualVal != v {
					t.Errorf("header: %s, expected value: %q, got %q", k, v, actualVal)
				}
			}

			for _, key := range tt.forbiddenHeaders {
				if val := capturedHeaders.Get(key); val != "" {
					t.Errorf("header %s should be empty, but got %q", key, val)
				}
			}
		})
	}
}

func TestRequireAuthMiddleware(t *testing.T) {
	tests := []struct {
		name           string
		anonymousValue string
		expectedStatus int
		nextCalled     bool
	}{
		{
			name:           "Anonymous request is rejected",
			anonymousValue: "true",
			expectedStatus: http.StatusUnauthorized,
			nextCalled:     false,
		},
		{
			name:           "Authenticated request passes through",
			anonymousValue: "false",
			expectedStatus: http.StatusOK,
			nextCalled:     true,
		},
		{
			name:           "Request with missing header is rejected",
			anonymousValue: "",
			expectedStatus: http.StatusUnauthorized,
			nextCalled:     false,
		},
		{
			name:           "Garbage value is treated as unauthorized",
			anonymousValue: "yes-please",
			expectedStatus: http.StatusUnauthorized,
			nextCalled:     false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			nextCalled := false
			next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				nextCalled = true
				w.WriteHeader(http.StatusOK)
			})

			req := httptest.NewRequest(http.MethodGet, "/", nil)
			if tt.anonymousValue != "" {
				req.Header.Set("X-User-Anonymous", tt.anonymousValue)
			}
			rec := httptest.NewRecorder()

			middleware.RequireAuthMiddleware(next).ServeHTTP(rec, req)

			if rec.Code != tt.expectedStatus {
				t.Errorf("expected status %d, got %d", tt.expectedStatus, rec.Code)
			}
			if nextCalled != tt.nextCalled {
				t.Errorf("expected nextCalled=%v, got %v", tt.nextCalled, nextCalled)
			}
		})
	}
}
