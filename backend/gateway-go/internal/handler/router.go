package handler

import (
	"fmt"
	"gateway-go/config"
	"gateway-go/internal/auth"
	"gateway-go/internal/middleware"
	"gateway-go/internal/proxy"
	"gateway-go/internal/swagger"
	"net/http"
	"sync/atomic"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/cors"
)

func SetupRouter(jwtManager *auth.JwtManager, cfg *config.Config, isReady *atomic.Bool) (http.Handler, error) {
	r := chi.NewRouter()

	usersProxy, err := proxy.New(cfg.UserServiceURL)
	if err != nil {
		return nil, fmt.Errorf("failed to init user proxy: %w", err)
	}

	r.Use(middleware.RecoveryMiddleware)
	r.Use(middleware.RequestIDMiddleware)
	r.Use(middleware.LoggingMiddleware)
	r.Use(middleware.GatewaySecretMiddleware(cfg.GatewayHeaderName, cfg.GatewaySecretKey))

	r.Group(func(r chi.Router) {
		healthHandler := NewHandler(isReady)
		r.Get("/healthz", healthHandler.Healthz)
		r.Get("/readyz", healthHandler.Readyz)
	})

	var swaggerServices []swagger.Service
	for _, dr := range cfg.DocsRoutes {
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

		r.Use(cors.Handler(cors.Options{
			AllowedOrigins:   cfg.Cors.AllowedOrigins,
			AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
			AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token"},
			ExposedHeaders:   []string{"Link", "X-Total-Count"},
			AllowCredentials: cfg.Cors.AllowCredentials,
			MaxAge:           300,
		}))

		r.Use(middleware.JwtAuthMiddleware(jwtManager))

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
