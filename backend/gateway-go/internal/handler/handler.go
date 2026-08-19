package handler

import (
	"net/http"
	"sync/atomic"
)

type Handler struct {
	isReady *atomic.Bool
}

func NewHandler(isReady *atomic.Bool) *Handler {
	return &Handler{isReady: isReady}
}

func (h *Handler) Healthz(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("OK"))
}

func (h *Handler) Readyz(w http.ResponseWriter, r *http.Request) {
	if h.isReady.Load() {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("OK"))
		return
	}
	w.WriteHeader(http.StatusServiceUnavailable)
	_, _ = w.Write([]byte("Service Unavailable"))
}
