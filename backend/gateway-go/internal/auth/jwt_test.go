package auth_test

import (
	"gateway-go/internal/auth"
	"testing"
	"time"

	"gateway-go/internal/testutil"

	"github.com/golang-jwt/jwt/v5"
)

func TestJwtManager_GetClaims(t *testing.T) {

	jwtManager, secretBytes := testutil.NewTestJwtManager(t)

	validClaims := auth.UserClaims{
		UserID: 123,
		Roles:  []string{"User"},
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(24 * time.Hour)),
		},
	}

	expiredClaims := auth.UserClaims{
		UserID: 123,
		Roles:  []string{"User"},
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(-1 * time.Hour)),
		},
	}

	tests := []struct {
		name    string
		token   string
		wantErr bool
	}{
		{
			name:    "Valid token",
			token:   testutil.GenerateTestToken(t, validClaims, secretBytes, jwt.SigningMethodHS256),
			wantErr: false,
		},
		{
			name:    "Expired token",
			token:   testutil.GenerateTestToken(t, expiredClaims, secretBytes, jwt.SigningMethodHS256),
			wantErr: true,
		},
		{
			name:    "Invalid secret",
			token:   testutil.GenerateTestToken(t, validClaims, []byte("wrong-secret"), jwt.SigningMethodHS256),
			wantErr: true,
		},
		{
			name:    "Invalid signing method",
			token:   testutil.GenerateTestToken(t, validClaims, jwt.UnsafeAllowNoneSignatureType, jwt.SigningMethodNone),
			wantErr: true,
		},
		{
			name:    "Malformed token",
			token:   "not-a-valid-token-string",
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			claims, err := jwtManager.GetClaims(tt.token)
			if (err != nil) != tt.wantErr {
				t.Errorf("expected error: %v, got %v", tt.wantErr, err)
			}

			if !tt.wantErr && claims.UserID != 123 {
				t.Errorf("expected UserID 123, got %d", claims.UserID)
			}
		})
	}
}

func TestNewJwtManager(t *testing.T) {
	tests := []struct {
		name      string
		secretKey string
		wantErr   bool
	}{
		{
			name:      "Valid base64 key",
			secretKey: "c2VjcmV0a2V5",
			wantErr:   false,
		},
		{
			name:      "Invalid base64 key",
			secretKey: "invalid-key123!!!",
			wantErr:   true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := auth.NewJwtManager(tt.secretKey)
			if (err != nil) != tt.wantErr {
				t.Errorf("expected error: %v, got %v", tt.wantErr, err)
			}
		})
	}
}

func TestUserClaims_Valid(t *testing.T) {
	valid := auth.UserClaims{
		UserID: 123,
		Roles:  []string{"User"},
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   "user@test.com",
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(24 * time.Hour)),
		},
	}

	invalidUserId := auth.UserClaims{
		Roles: []string{"User"},
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   "user@test.com",
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(24 * time.Hour)),
		},
	}

	negativeUserId := auth.UserClaims{
		UserID: -1,
		Roles:  []string{"User"},
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   "user@test.com",
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(24 * time.Hour)),
		},
	}

	invalidSubject := auth.UserClaims{
		UserID: 123,
		Roles:  []string{"User"},
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(24 * time.Hour)),
		},
	}

	tests := []struct {
		name    string
		claims  auth.UserClaims
		wantErr bool
	}{
		{
			name:    "Valid claims",
			claims:  valid,
			wantErr: false,
		},
		{
			name:    "Invalid userId",
			claims:  invalidUserId,
			wantErr: true,
		},
		{
			name:    "Negative subject",
			claims:  negativeUserId,
			wantErr: true,
		},
		{
			name:    "Invalid subject",
			claims:  invalidSubject,
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.claims.Valid()
			if (err != nil) != tt.wantErr {
				t.Errorf("expected error: %v, got %v", tt.wantErr, err)
			}
		})
	}
}
