package auth

import (
	"fmt"

	"github.com/golang-jwt/jwt/v5"
)

type JwtManager struct {
	secret []byte
}

func NewJwtManager(secret string) *JwtManager {
	return &JwtManager{
		secret: []byte(secret),
	}
}

func (m *JwtManager) GetClaims(tokenStr string) (jwt.Claims, error) {
	token, err := jwt.Parse(tokenStr, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return m.secret, nil
	})

	if err != nil {
		return nil, err
	}

	if claims, ok := token.Claims.(jwt.MapClaims); ok && token.Valid {
		return claims, nil
	}

	return nil, fmt.Errorf("invalid token")
}
