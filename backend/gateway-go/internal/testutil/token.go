package testutil

import (
	"encoding/base64"
	"gateway-go/internal/auth"
	"testing"

	"github.com/golang-jwt/jwt/v5"
)

const TestSecretBase64 = "dGVzdC1zZWNyZXQtMzItYnl0ZXMtbG9uZy0wMDAwMDA="

func NewTestJwtManager(t *testing.T) (*auth.JwtManager, []byte) {
	t.Helper()

	secretBytes, err := base64.StdEncoding.DecodeString(TestSecretBase64)
	if err != nil {
		t.Fatalf("bad test secret: %v", err)
	}

	secretB64 := base64.StdEncoding.EncodeToString(secretBytes)

	m, err := auth.NewJwtManager(secretB64)
	if err != nil {
		t.Fatalf("failed to create jwt manager: %v", err)
	}

	return m, secretBytes
}

func GenerateTestToken(t *testing.T, claims jwt.Claims, secret interface{}, alg jwt.SigningMethod) string {
	t.Helper()
	token := jwt.NewWithClaims(alg, claims)
	tokenStr, err := token.SignedString(secret)
	if err != nil {
		t.Fatalf("failed to sign token: %v", err)
	}
	return tokenStr
}
