package main

import (
	"context"
	"errors"
	"gateway-go/config"
	"gateway-go/internal/auth"
	"gateway-go/internal/handler"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"sync/atomic"
	"syscall"
	"time"
)

func main() {
	status := &atomic.Bool{}

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	slog.SetDefault(logger)

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

	r, err := handler.SetupRouter(jwtManager, cfg, status)
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
