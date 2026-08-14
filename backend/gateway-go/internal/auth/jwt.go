package auth

import (
	"encoding/base64"
	"fmt"

	"github.com/golang-jwt/jwt/v5"
)

type JwtManager struct {
	secret []byte
}

type UserClaims struct {
	UserID int      `json:"userId"`
	Roles  []string `json:"roles"`
	jwt.RegisteredClaims
}

func NewJwtManager(secretBase64 string) (*JwtManager, error) {
	secretBytes, err := base64.StdEncoding.DecodeString(secretBase64)
	if err != nil {
		return nil, fmt.Errorf("invalid base64 secret: %w", err)
	}

	return &JwtManager{
		secret: secretBytes,
	}, nil
}

func (m *JwtManager) GetClaims(tokenStr string) (*UserClaims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &UserClaims{}, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return m.secret, nil
	})

	if err != nil {
		return nil, err
	}

	if claims, ok := token.Claims.(*UserClaims); ok && token.Valid {
		return claims, nil
	}

	return nil, fmt.Errorf("invalid token")
}
