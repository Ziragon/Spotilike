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

func setupRouter(jwtManager *auth.JwtManager, cfg *config.Config) http.Handler {
	r := chi.NewRouter()

	usersProxy, err := proxy.New(cfg.UserServiceURL)
	if err != nil {
		log.Fatalf("Failed to init user proxy: %v", err)
	}

	r.Use(middleware.RecoveryMiddleware)
	r.Use(middleware.RequestIDMiddleware)
	r.Use(middleware.JwtAuthMiddleware(jwtManager))

	// Open paths
	r.Handle("/api/v1/auth/login", usersProxy)
	r.Handle("/api/v1/auth/register", usersProxy)
	r.Handle("/api/v1/auth/refresh", usersProxy)
	r.Handle("/actuator/health", usersProxy)
	r.Handle("/swagger-ui.html", usersProxy)
	r.Handle("/swagger-ui/*", usersProxy)
	r.Handle("/v3/api-docs/*", usersProxy)
	r.Handle("/webjars/*", usersProxy)

	// Secured paths
	r.Group(func(r chi.Router) {
		r.Use(middleware.RequireAuthMiddleware)

		r.Handle("/api/v1/auth/*", usersProxy)
	})

	return r
}

func main() {

	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Failed to read config: %v", err)
	}

	jwtManager, err := auth.NewJwtManager(cfg.JWTSecret)
	if err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}

	serverAddr := ":" + cfg.Port

	r := setupRouter(jwtManager, cfg)

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
