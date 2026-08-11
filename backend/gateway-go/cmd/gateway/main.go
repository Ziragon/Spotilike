package main

import (
	"gateway-go/internal/proxy"
	"log"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
)

func main() {

	r := chi.NewRouter()

	server := &http.Server{
		Addr:              ":8080",
		Handler:           r,
		ReadHeaderTimeout: 2 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    1 << 20,
	}

	usersProxy, err := proxy.New("http://localhost:8081")
	if err != nil {
		log.Fatalf("Failed to init user proxy: %v", err)
	}

	r.Handle("/api/v1/auth/*", usersProxy)

	log.Printf("Server started at %v", server.Addr)

	if err := server.ListenAndServe(); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
