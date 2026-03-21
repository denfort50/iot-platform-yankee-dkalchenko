package auth

import (
	"context"
	"errors"
	"testing"

	"github.com/coreos/go-oidc/v3/oidc"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

type stubVerifier struct {
	verify func(ctx context.Context, raw string) (*oidc.IDToken, error)
}

func (s *stubVerifier) Verify(ctx context.Context, rawIDToken string) (*oidc.IDToken, error) {
	if s.verify != nil {
		return s.verify(ctx, rawIDToken)
	}
	return nil, nil
}

func TestNewJWTAuth_EmptyIssuer(t *testing.T) {
	_, err := NewJWTAuth(context.Background(), "")
	if err == nil {
		t.Fatal("expected error")
	}
	_, err = NewJWTAuth(context.Background(), "   ")
	if err == nil {
		t.Fatal("expected error for whitespace issuer")
	}
}

func TestJWTAuth_UnaryServerInterceptor(t *testing.T) {
	t.Parallel()

	okHandler := grpc.UnaryHandler(func(ctx context.Context, req any) (any, error) {
		return "ok", nil
	})

	t.Run("missing metadata", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{verifier: &stubVerifier{}}
		_, err := a.UnaryServerInterceptor()(context.Background(), nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("missing authorization", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{verifier: &stubVerifier{}}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("x-other", "v"))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("bearer without token segment", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{verifier: &stubVerifier{}}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer"))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("invalid scheme", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{verifier: &stubVerifier{}}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Basic x"))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("bearer missing token", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{verifier: &stubVerifier{}}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer "))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("verify error", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{verifier: &stubVerifier{
			verify: func(ctx context.Context, raw string) (*oidc.IDToken, error) {
				return nil, errors.New("bad sig")
			},
		}}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer token"))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("success", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{verifier: &stubVerifier{}}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer valid.jwt"))
		got, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		if err != nil {
			t.Fatal(err)
		}
		if got != "ok" {
			t.Fatalf("got %v", got)
		}
	})
}
