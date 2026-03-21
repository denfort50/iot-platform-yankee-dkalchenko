package auth

import (
	"context"
	"fmt"
	"strings"

	"github.com/coreos/go-oidc/v3/oidc"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

// jwtTokenVerifier — минимальный контракт проверки JWT; *oidc.IDTokenVerifier его реализует.
type jwtTokenVerifier interface {
	Verify(ctx context.Context, rawIDToken string) (*oidc.IDToken, error)
}

// JWTAuth отвечает за проверку JWT-токенов (OIDC) от Keycloak.
type JWTAuth struct {
	verifier jwtTokenVerifier
}

// NewJWTAuth инициализирует OIDC-провайдера и верификатор токенов для заданного issuerURL.
// ClientID не проверяется (SkipClientIDCheck=true), так как используется только проверка подписи и iss.
func NewJWTAuth(ctx context.Context, issuerURL string) (*JWTAuth, error) {
	if strings.TrimSpace(issuerURL) == "" {
		return nil, fmt.Errorf("issuer URL is empty")
	}
	provider, err := oidc.NewProvider(ctx, issuerURL)
	if err != nil {
		return nil, fmt.Errorf("init oidc provider: %w", err)
	}
	verifier := provider.Verifier(&oidc.Config{
		SkipClientIDCheck: true,
	})
	return &JWTAuth{verifier: verifier}, nil
}

// UnaryServerInterceptor возвращает gRPC interceptor, который требует Bearer JWT в metadata.authorization.
func (a *JWTAuth) UnaryServerInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		md, ok := metadata.FromIncomingContext(ctx)
		if !ok {
			return nil, status.Error(codes.Unauthenticated, "missing metadata")
		}
		authHeaders := md.Get("authorization")
		if len(authHeaders) == 0 {
			return nil, status.Error(codes.Unauthenticated, "missing authorization header")
		}
		parts := strings.SplitN(authHeaders[0], " ", 2)
		if len(parts) != 2 || !strings.EqualFold(parts[0], "bearer") {
			return nil, status.Error(codes.Unauthenticated, "invalid authorization header")
		}
		rawToken := parts[1]
		if strings.TrimSpace(rawToken) == "" {
			return nil, status.Error(codes.Unauthenticated, "empty token")
		}
		if _, err := a.verifier.Verify(ctx, rawToken); err != nil {
			return nil, status.Errorf(codes.Unauthenticated, "invalid token: %v", err)
		}
		return handler(ctx, req)
	}
}

