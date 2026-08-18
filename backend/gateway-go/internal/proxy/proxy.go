package proxy

import (
	"errors"
	"fmt"
	"gateway-go/internal/response"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"time"
)

func New(targetUrl string) (http.Handler, error) {

	target, err := url.Parse(targetUrl)
	if err != nil {
		return nil, fmt.Errorf("invalid target url: %w", err)
	}

	proxy := httputil.NewSingleHostReverseProxy(target)

	proxy.Transport = &http.Transport{
		DialContext: (&net.Dialer{
			Timeout:   2 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
		MaxIdleConns:          100,
		MaxIdleConnsPerHost:   20,
		IdleConnTimeout:       90 * time.Second,
		ResponseHeaderTimeout: 5 * time.Second,
	}

	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		log.Printf("Proxy error for %s: %v", r.URL.Path, err)

		var netErr net.Error
		if errors.As(err, &netErr) && netErr.Timeout() {
			response.SendError(w, r,
				http.StatusGatewayTimeout,
				"GATEWAY_TIMEOUT",
				"Downstream service took too long to respond",
				nil,
			)
			return
		}

		response.SendError(w, r,
			http.StatusBadGateway,
			"SERVICE_UNAVAILABLE",
			"Downstream service is unreachable",
			nil,
		)
	}

	return proxy, nil
}
