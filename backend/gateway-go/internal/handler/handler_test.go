package handler

import (
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
)

func TestReadyz(t *testing.T) {
	var isReady atomic.Bool
	h := NewHandler(&isReady)

	req := httptest.NewRequest(http.MethodGet, "/readyz", nil)
	rec := httptest.NewRecorder()
	h.Readyz(rec, req)

	if rec.Code != http.StatusServiceUnavailable {
		t.Errorf("expected 503, got %d", rec.Code)
	}

	isReady.Store(true)
	rec2 := httptest.NewRecorder()
	h.Readyz(rec2, req)

	if rec2.Code != http.StatusOK {
		t.Errorf("expected 200, got %d", rec2.Code)
	}
}
