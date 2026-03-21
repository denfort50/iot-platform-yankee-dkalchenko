package telemetry

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestSetup_EmptyEndpointNoOpShutdown(t *testing.T) {
	t.Parallel()
	ctx := context.Background()
	shutdown, err := Setup(ctx, "", "any-name")
	if err != nil {
		t.Fatal(err)
	}
	if shutdown == nil {
		t.Fatal("expected non-nil shutdown func")
	}
	if err := shutdown(ctx); err != nil {
		t.Fatalf("noop shutdown: %v", err)
	}
}

func TestSetup_WithHTTPServer_DefaultServiceName(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method", http.StatusMethodNotAllowed)
			return
		}
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(srv.Close)

	ctx := context.Background()
	endpoint := strings.TrimSuffix(srv.URL, "/") + "/v1/traces"
	shutdown, err := Setup(ctx, endpoint, "  ")
	if err != nil {
		t.Fatal(err)
	}
	if shutdown == nil {
		t.Fatal("expected shutdown")
	}
	if err := shutdown(context.Background()); err != nil {
		t.Fatalf("shutdown: %v", err)
	}
}
