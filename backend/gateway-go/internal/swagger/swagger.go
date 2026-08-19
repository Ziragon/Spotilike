package swagger

import (
	"bytes"
	"embed"
	"fmt"
	"html/template"
	"net/http"
)

//go:embed templates/index.html.tmpl
var templatesFS embed.FS

type Service struct {
	Name string
	Path string
}

var tmpl = template.Must(template.ParseFS(templatesFS, "templates/index.html.tmpl"))

func NewHandler(services []Service) (http.HandlerFunc, error) {
	var buf bytes.Buffer
	if err := tmpl.Execute(&buf, services); err != nil {
		return nil, fmt.Errorf("failed to render swagger ui template: %w", err)
	}

	htmlBytes := buf.Bytes()

	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "public, max-age=3600")
		_, _ = w.Write(htmlBytes)
	}, nil
}
