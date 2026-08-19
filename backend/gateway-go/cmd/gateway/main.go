package main

import (
	"context"
	"errors"
	"fmt"
	"gateway-go/config"
	"gateway-go/internal/auth"
	"gateway-go/internal/handler"
	"gateway-go/internal/middleware"
	"gateway-go/internal/proxy"
	"gateway-go/internal/swagger"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
)

type DocsRoute struct {
	Name            string
	GatewayPath     string
	BackendDocsPath string
	TargetURL       string
}

func initLogger() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	slog.SetDefault(logger)
}

func setupRouter(jwtManager *auth.JwtManager, cfg *config.Config, isReady *atomic.Bool) (http.Handler, error) {
	r := chi.NewRouter()

	usersProxy, err := proxy.New(cfg.UserServiceURL)
	if err != nil {
		return nil, fmt.Errorf("failed to init user proxy: %w", err)
	}

	r.Use(middleware.RecoveryMiddleware)
	r.Use(middleware.RequestIDMiddleware)
	r.Use(middleware.LoggingMiddleware)
	r.Use(middleware.JwtAuthMiddleware(jwtManager))

	healthHandler := handler.NewHandler(isReady)
	r.Get("/healthz", healthHandler.Healthz)
	r.Get("/readyz", healthHandler.Readyz)

	docsRoutes := []DocsRoute{
		{Name: "User Service", GatewayPath: "/v3/api-docs/user-service", BackendDocsPath: "/api-docs", TargetURL: cfg.UserServiceURL},
	}
	var swaggerServices []swagger.Service
	for _, dr := range docsRoutes {
		p, err := proxy.NewDocsProxy(dr.TargetURL, dr.BackendDocsPath)
		if err != nil {
			return nil, fmt.Errorf("failed to init docs proxy for %s: %w", dr.Name, err)
		}
		r.Handle(dr.GatewayPath, p)
		swaggerServices = append(swaggerServices, swagger.Service{Name: dr.Name, Path: dr.GatewayPath})
	}
	h, err := swagger.NewHandler(swaggerServices)
	if err != nil {
		return nil, fmt.Errorf("failed to init swagger handler: %w", err)
	}

	r.Get("/swagger-ui", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/swagger-ui/", http.StatusMovedPermanently)
	})
	r.Get("/swagger-ui/", h)
	r.Get("/swagger-ui/index", h)
	r.Get("/swagger-ui/index.html", h)

	r.Group(func(r chi.Router) {

		// Open paths
		r.Handle("/api/v1/auth/login", usersProxy)
		r.Handle("/api/v1/auth/register", usersProxy)
		r.Handle("/api/v1/auth/refresh", usersProxy)
		r.Handle("/actuator/health", usersProxy)

		// Secured paths
		r.Group(func(r chi.Router) {
			r.Use(middleware.RequireAuthMiddleware)

			r.Handle("/api/v1/auth/*", usersProxy)
		})
	})

	return r, nil
}

func main() {
	status := &atomic.Bool{}

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

	r, err := setupRouter(jwtManager, cfg, status)
	if err != nil {
		slog.Error("Failed to init router", "error", err)
		os.Exit(1)
	}

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

	status.Store(true)

	<-stopCtx.Done()
	slog.Info("Shutting down gateway gracefully...")

	status.Store(false)
	time.Sleep(2 * time.Second)

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		slog.Error("Server forced to shutdown", "error", err)
	}

	slog.Info("Gateway stopped cleanly")
}
