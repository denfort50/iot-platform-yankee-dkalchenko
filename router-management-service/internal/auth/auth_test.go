package auth

import (
	"context"
	"errors"
	"testing"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

func TestNewJWTAuth_EmptyIssuer(t *testing.T) {
	_, err := NewJWTAuth(context.Background(), "", "client")
	if err == nil {
		t.Fatal("expected error")
	}
	_, err = NewJWTAuth(context.Background(), "   ", "client")
	if err == nil {
		t.Fatal("expected error for whitespace issuer")
	}
}

func TestNewJWTAuth_EmptyClientID(t *testing.T) {
	_, err := NewJWTAuth(context.Background(), "https://issuer/realms/master", "")
	if err == nil {
		t.Fatal("expected error")
	}
	_, err = NewJWTAuth(context.Background(), "https://issuer/realms/master", "  ")
	if err == nil {
		t.Fatal("expected error for whitespace client id")
	}
}

func TestTokenClaimsAllowClient(t *testing.T) {
	t.Parallel()
	want := "my-client"
	t.Run("aud string match", func(t *testing.T) {
		t.Parallel()
		err := tokenClaimsAllowClient(map[string]interface{}{"aud": want}, want)
		if err != nil {
			t.Fatal(err)
		}
	})
	t.Run("aud array match", func(t *testing.T) {
		t.Parallel()
		err := tokenClaimsAllowClient(map[string]interface{}{
			"aud": []interface{}{"other", want},
		}, want)
		if err != nil {
			t.Fatal(err)
		}
	})
	t.Run("azp match", func(t *testing.T) {
		t.Parallel()
		err := tokenClaimsAllowClient(map[string]interface{}{"azp": want}, want)
		if err != nil {
			t.Fatal(err)
		}
	})
	t.Run("no match", func(t *testing.T) {
		t.Parallel()
		err := tokenClaimsAllowClient(map[string]interface{}{
			"aud": []interface{}{"other"},
			"azp": "wrong",
		}, want)
		if err == nil {
			t.Fatal("expected error")
		}
	})
}

func TestJWTAuth_UnaryServerInterceptor(t *testing.T) {
	t.Parallel()

	okHandler := grpc.UnaryHandler(func(ctx context.Context, req any) (any, error) {
		return "ok", nil
	})

	t.Run("missing metadata", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error {
			return nil
		}}
		_, err := a.UnaryServerInterceptor()(context.Background(), nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("missing authorization", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error { return nil }}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("x-other", "v"))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("bearer without token segment", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error { return nil }}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer"))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("invalid scheme", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error { return nil }}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Basic x"))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("bearer missing token", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error { return nil }}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer "))
		_, err := a.UnaryServerInterceptor()(ctx, nil, &grpc.UnaryServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("verify error", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error {
			return status.Errorf(codes.Unauthenticated, "invalid token: %v", errors.New("bad sig"))
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
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error {
			if raw != "valid.jwt" {
				return status.Error(codes.Unauthenticated, "wrong token")
			}
			return nil
		}}
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

type stubServerStream struct {
	ctx context.Context
}

func (s *stubServerStream) Context() context.Context { return s.ctx }

func (s *stubServerStream) SetHeader(md metadata.MD) error { return nil }

func (s *stubServerStream) SendHeader(md metadata.MD) error { return nil }

func (s *stubServerStream) SetTrailer(md metadata.MD) {}

func (s *stubServerStream) SendMsg(m interface{}) error { return nil }

func (s *stubServerStream) RecvMsg(m interface{}) error { return nil }

func TestJWTAuth_StreamServerInterceptor(t *testing.T) {
	t.Parallel()

	okHandler := grpc.StreamHandler(func(srv interface{}, stream grpc.ServerStream) error {
		return nil
	})

	t.Run("missing metadata", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error { return nil }}
		stream := &stubServerStream{ctx: context.Background()}
		err := a.StreamServerInterceptor()(nil, stream, &grpc.StreamServerInfo{}, okHandler)
		st, _ := status.FromError(err)
		if st.Code() != codes.Unauthenticated {
			t.Fatalf("code: %v", st.Code())
		}
	})

	t.Run("success", func(t *testing.T) {
		t.Parallel()
		a := &JWTAuth{checkBearer: func(ctx context.Context, raw string) error { return nil }}
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer valid.jwt"))
		stream := &stubServerStream{ctx: ctx}
		err := a.StreamServerInterceptor()(nil, stream, &grpc.StreamServerInfo{}, okHandler)
		if err != nil {
			t.Fatal(err)
		}
	})
}
