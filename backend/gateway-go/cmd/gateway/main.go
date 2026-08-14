package main

import (
	"errors"
	"gateway-go/config"
	"gateway-go/internal/auth"
	"gateway-go/internal/middleware"
	"gateway-go/internal/proxy"
	"log"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
)

func main() {

	r := chi.NewRouter()

	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Failed to read config: %v", err)
	}

	jwtManager := auth.NewJwtManager(cfg.JWTSecret)

	usersProxy, err := proxy.New("http://localhost:8081")
	if err != nil {
		log.Fatalf("Failed to init user proxy: %v", err)
	}

	r.Use(middleware.RequestIDMiddleware)
	r.Use(middleware.JwtAuthMiddleware(jwtManager))

	r.Handle("/api/v1/auth/*", usersProxy)

	serverAddr := ":" + cfg.Port

	server := &http.Server{
		Addr:              serverAddr,
		Handler:           r,
		ReadHeaderTimeout: 2 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    1 << 20,
	}

	log.Printf("Server started at %v", server.Addr)

	if err = server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("Server failed to start: %v", err)
	}
}
