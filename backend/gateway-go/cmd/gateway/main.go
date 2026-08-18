package main

import (
	"context"
	"errors"
	"gateway-go/config"
	"gateway-go/internal/auth"
	"gateway-go/internal/middleware"
	"gateway-go/internal/proxy"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
)

func initLogger() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	slog.SetDefault(logger)
}

func setupRouter(jwtManager *auth.JwtManager, cfg *config.Config) http.Handler {
	r := chi.NewRouter()

	usersProxy, err := proxy.New(cfg.UserServiceURL)
	if err != nil {
		slog.Error("Failed to init user proxy", "error", err)
		os.Exit(1)
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
	initLogger()

	cfg, err := config.Load()
	if err != nil {
		slog.Error("Failed to read config", "error", err)
		os.Exit(1)
	}

	jwtManager, err := auth.NewJwtManager(cfg.JWTSecret)
	if err != nil {
		slog.Error("Failed to init JWT manager", "error", err)
		os.Exit(1)
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

	stopCtx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		slog.Info("Gateway started", "port", cfg.Port)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("Server failed to start", "error", err)
			os.Exit(1)
		}
	}()

	<-stopCtx.Done()
	slog.Info("Shutting down gateway gracefully...")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		slog.Error("Server forced to shutdown", "error", err)
	}

	slog.Info("Gateway stopped cleanly")
}
