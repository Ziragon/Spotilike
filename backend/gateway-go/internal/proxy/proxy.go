package proxy

import (
	"errors"
	"fmt"
	"gateway-go/internal/response"
	"log/slog"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"time"
)

func newBaseProxy(target *url.URL, rewrite func(*httputil.ProxyRequest)) *httputil.ReverseProxy {
	proxy := &httputil.ReverseProxy{
		Rewrite:  rewrite,
		ErrorLog: slog.NewLogLogger(slog.Default().Handler(), slog.LevelError),
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout:   2 * time.Second,
				KeepAlive: 30 * time.Second,
			}).DialContext,
			MaxIdleConns:          100,
			MaxIdleConnsPerHost:   20,
			IdleConnTimeout:       90 * time.Second,
			ResponseHeaderTimeout: 5 * time.Second,
		},
	}
	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		var netErr net.Error
		isTimeout := errors.As(err, &netErr) && netErr.Timeout()
		slog.Error("Downstream proxy error",
			"target", target.String(),
			"method", r.Method,
			"path", r.URL.Path,
			"request_id", r.Header.Get("X-Request-ID"),
			"is_timeout", isTimeout,
			"error", err,
		)
		if isTimeout {
			response.SendError(w, r, http.StatusGatewayTimeout, "GATEWAY_TIMEOUT", "Downstream service took too long to respond", nil)
			return
		}
		response.SendError(w, r, http.StatusBadGateway, "SERVICE_UNAVAILABLE", "Downstream service is unreachable", nil)
	}
	return proxy
}

// New is a default proxy to services
func New(targetUrl string) (*httputil.ReverseProxy, error) {
	target, err := url.Parse(targetUrl)
	if err != nil {
		return nil, fmt.Errorf("invalid target url: %w", err)
	}
	rewrite := func(pr *httputil.ProxyRequest) {
		pr.SetURL(target)
		pr.Out.Host = pr.In.Host
	}
	return newBaseProxy(target, rewrite), nil
}

// NewDocsProxy is a swagger docs proxy
func NewDocsProxy(targetUrl, backendDocsPath string) (*httputil.ReverseProxy, error) {
	target, err := url.Parse(targetUrl)
	if err != nil {
		return nil, fmt.Errorf("invalid target url: %w", err)
	}
	rewrite := func(pr *httputil.ProxyRequest) {
		pr.SetURL(target)
		pr.Out.Host = pr.In.Host
		pr.Out.URL.Path = backendDocsPath
		pr.Out.URL.RawPath = ""
	}
	return newBaseProxy(target, rewrite), nil
}
