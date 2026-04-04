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

// JWTAuth отвечает за проверку JWT-токенов (OIDC) от Keycloak.
type JWTAuth struct {
	checkBearer func(ctx context.Context, rawToken string) error
}

// NewJWTAuth инициализирует OIDC-провайдера и верификатор токенов.
// clientID — ожидаемый OAuth2 client_id: в JWT должен присутствовать в claim aud (или, как у access token Keycloak, в azp).
func NewJWTAuth(ctx context.Context, issuerURL, clientID string) (*JWTAuth, error) {
	if strings.TrimSpace(issuerURL) == "" {
		return nil, fmt.Errorf("issuer URL is empty")
	}
	if strings.TrimSpace(clientID) == "" {
		return nil, fmt.Errorf("OIDC client ID is empty")
	}
	provider, err := oidc.NewProvider(ctx, issuerURL)
	if err != nil {
		return nil, fmt.Errorf("init oidc provider: %w", err)
	}
	// Access token Keycloak часто не заполняет aud для нашего клиента, но задаёт azp. SkipClientIDCheck
	// отключает только встроенную проверку aud в go-oidc; привязку к клиенту делаем в tokenClaimsAllowClient.
	verifier := provider.Verifier(&oidc.Config{
		SkipClientIDCheck: true,
	})
	want := strings.TrimSpace(clientID)
	return &JWTAuth{
		checkBearer: func(ctx context.Context, raw string) error {
			return verifyBearerJWT(ctx, verifier, want, raw)
		},
	}, nil
}

func verifyBearerJWT(ctx context.Context, v *oidc.IDTokenVerifier, wantClientID, raw string) error {
	idt, err := v.Verify(ctx, raw)
	if err != nil {
		return status.Errorf(codes.Unauthenticated, "invalid token: %v", err)
	}
	if idt == nil {
		return status.Error(codes.Unauthenticated, "invalid token")
	}
	var claims map[string]interface{}
	if err := idt.Claims(&claims); err != nil {
		return status.Errorf(codes.Unauthenticated, "invalid token claims: %v", err)
	}
	if err := tokenClaimsAllowClient(claims, wantClientID); err != nil {
		return status.Errorf(codes.Unauthenticated, "invalid token: %v", err)
	}
	return nil
}

// tokenClaimsAllowClient проверяет, что токен выдан для ожидаемого клиента (aud или azp).
func tokenClaimsAllowClient(claims map[string]interface{}, want string) error {
	if want == "" {
		return fmt.Errorf("client id not configured")
	}
	if audienceContains(claims["aud"], want) {
		return nil
	}
	if azp, ok := claims["azp"].(string); ok && azp == want {
		return nil
	}
	return fmt.Errorf("token not issued for client %q (expected aud or azp)", want)
}

func audienceContains(audClaim interface{}, want string) bool {
	if want == "" || audClaim == nil {
		return false
	}
	switch v := audClaim.(type) {
	case string:
		return v == want
	case []interface{}:
		for _, x := range v {
			if s, ok := x.(string); ok && s == want {
				return true
			}
		}
		return false
	default:
		return false
	}
}

// authenticate проверяет Bearer JWT во входящих метаданных gRPC (unary и stream).
func (a *JWTAuth) authenticate(ctx context.Context) error {
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return status.Error(codes.Unauthenticated, "missing metadata")
	}
	authHeaders := md.Get("authorization")
	if len(authHeaders) == 0 {
		return status.Error(codes.Unauthenticated, "missing authorization header")
	}
	parts := strings.SplitN(authHeaders[0], " ", 2)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "bearer") {
		return status.Error(codes.Unauthenticated, "invalid authorization header")
	}
	rawToken := parts[1]
	if strings.TrimSpace(rawToken) == "" {
		return status.Error(codes.Unauthenticated, "empty token")
	}
	return a.checkBearer(ctx, rawToken)
}

// UnaryServerInterceptor возвращает gRPC interceptor, который требует Bearer JWT в metadata.authorization.
func (a *JWTAuth) UnaryServerInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		if err := a.authenticate(ctx); err != nil {
			return nil, err
		}
		return handler(ctx, req)
	}
}

// StreamServerInterceptor — то же для streaming RPC (в т.ч. gRPC reflection).
func (a *JWTAuth) StreamServerInterceptor() grpc.StreamServerInterceptor {
	return func(srv interface{}, ss grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		if err := a.authenticate(ss.Context()); err != nil {
			return err
		}
		return handler(srv, ss)
	}
}
