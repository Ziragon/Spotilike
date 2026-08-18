package response

import (
	"encoding/json"
	"net/http"
	"time"
)

type ErrorResponse struct {
	Code      string         `json:"code"`
	Message   string         `json:"message"`
	Status    int            `json:"status"`
	Timestamp time.Time      `json:"timestamp"`
	Path      string         `json:"path"`
	Details   map[string]any `json:"details,omitempty"`
}

func SendError(w http.ResponseWriter, r *http.Request, status int, code string, message string, details map[string]any) {
	w.Header().Set("Content-Type", "application/json")

	w.WriteHeader(status)

	resp := ErrorResponse{
		Code:      code,
		Message:   message,
		Status:    status,
		Timestamp: time.Now(),
		Path:      r.URL.Path,
		Details:   details,
	}

	json.NewEncoder(w).Encode(resp)
}
