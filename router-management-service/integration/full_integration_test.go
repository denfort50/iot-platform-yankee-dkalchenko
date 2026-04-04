//go:build integration

// Интеграционный тест в стиле device-service FullIntegrationTests: Testcontainers (PostgreSQL),
// поднятие gRPC-сервера на тестовой БД и последовательная проверка всех эндпоинтов с ассёртами.
package integration_test

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"

	apiv1 "router-management-service/api/proto/api/v1"
	"router-management-service/internal/auth"
	"router-management-service/internal/grpchandler"
	"router-management-service/internal/migrate"
	"router-management-service/internal/repository"
	"router-management-service/internal/service"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"
)

const (
	pgImage  = "postgres:17-alpine"
	pgDB     = "router_management"
	pgUser   = "postgres"
	pgPass   = "postgres"
	grpcPort = "127.0.0.1:0"
	kcImage  = "quay.io/keycloak/keycloak:26.2"
)

// TestFullIntegration_AllGrpcEndpoints поднимает PostgreSQL в Testcontainers, выполняет миграции,
// запускает gRPC-сервер и последовательно проверяет: RegisterRouter → SendCommand (одному) →
// CommandsPoll → CommandsAck → SendCommand (всем) → повторный CommandsPoll.
func TestFullIntegration_AllGrpcEndpoints(t *testing.T) {
	ctx := context.Background()

	// 1. Запуск PostgreSQL в контейнере
	pgCtr, err := postgres.Run(ctx,
		pgImage,
		postgres.WithDatabase(pgDB),
		postgres.WithUsername(pgUser),
		postgres.WithPassword(pgPass),
		postgres.BasicWaitStrategies(),
	)
	require.NoError(t, err)
	defer testcontainers.CleanupContainer(t, pgCtr)

	connStr, err := pgCtr.ConnectionString(ctx, "sslmode=disable")
	require.NoError(t, err)

	// 2. Подключение к БД и миграции
	pool, err := pgxpool.New(ctx, connStr)
	require.NoError(t, err)
	defer pool.Close()
	require.NoError(t, pool.Ping(ctx))

	log := slog.Default()
	require.NoError(t, migrate.Run(ctx, pool, log))

	// 3. Запуск Keycloak в контейнере и инициализация test-пользователя
	kcCtr, kcIssuer, accessToken := startAndInitKeycloak(ctx, t)
	defer func() {
		require.NoError(t, kcCtr.Terminate(ctx))
	}()

	// 4. Сборка сервера и запуск на свободном порту с включённой JWT-аутентификацией
	routerStore := repository.NewPostgresRouterStore(pool)
	commandStore := repository.NewPostgresCommandStore(pool)
	svc := service.NewCommandService(routerStore, commandStore, log)

	jwtAuth, err := auth.NewJWTAuth(ctx, kcIssuer, "admin-cli")
	require.NoError(t, err)

	grpcServer := grpc.NewServer(
		grpc.ChainUnaryInterceptor(jwtAuth.UnaryServerInterceptor()),
		grpc.ChainStreamInterceptor(jwtAuth.StreamServerInterceptor()),
	)
	apiv1.RegisterRouterManagementServiceServer(grpcServer, grpchandler.NewServer(svc, log))

	listener, err := net.Listen("tcp", grpcPort)
	require.NoError(t, err)
	go func() { _ = grpcServer.Serve(listener) }()
	defer grpcServer.GracefulStop()

	// 5. gRPC-клиент
	addr := listener.Addr().String()
	conn, err := grpc.NewClient(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	require.NoError(t, err)
	defer conn.Close()

	client := apiv1.NewRouterManagementServiceClient(conn)

	// 6. Проверка, что без токена возвращается Unauthenticated
	_, err = client.RegisterRouter(context.Background(), &apiv1.RegisterRouterRequest{
		InventoryNumber: "INV-UNAUTH",
		Name:            "Unauth",
		Model:           "X",
	})
	require.Error(t, err)
	st, ok := status.FromError(err)
	require.True(t, ok)
	require.Equal(t, codes.Unauthenticated, st.Code())

	// 7. Контекст с валидным Bearer-токеном
	authCtx := metadata.NewOutgoingContext(ctx, metadata.Pairs(
		"authorization", "Bearer "+accessToken,
	))

	// --- 8. Регистрация двух роутеров ---
	reg1, err := client.RegisterRouter(authCtx, &apiv1.RegisterRouterRequest{
		InventoryNumber: "INV-001",
		Name:            "Router Alpha",
		Model:           "TP-Link",
		Location:        "Office 1",
	})
	require.NoError(t, err)
	require.Nil(t, reg1.Error, "expected no error: %v", reg1.Error)
	require.NotEmpty(t, reg1.RouterId, "expected router_id")
	routerID1 := reg1.RouterId

	reg2, err := client.RegisterRouter(authCtx, &apiv1.RegisterRouterRequest{
		InventoryNumber: "INV-002",
		Name:            "Router Beta",
		Model:           "Huawei",
	})
	require.NoError(t, err)
	require.Nil(t, reg2.Error)
	require.NotEmpty(t, reg2.RouterId)
	routerID2 := reg2.RouterId

	// --- 6. SendCommand одному роутеру ---
	sendResp, err := client.SendCommand(authCtx, &apiv1.SendCommandRequest{
		RouterId:    routerID1,
		CommandType: "REBOOT",
		Payload:     `{"delay": 5}`,
	})
	require.NoError(t, err)
	require.Nil(t, sendResp.Error)
	require.Len(t, sendResp.CommandIds, 1, "expected one command_id for single router")
	commandID1 := sendResp.CommandIds[0]
	require.NotEmpty(t, commandID1)

	// --- 7. CommandsPoll для первого роутера: должна быть одна команда ---
	pollResp, err := client.CommandsPoll(authCtx, &apiv1.CommandsPollRequest{RouterId: routerID1})
	require.NoError(t, err)
	require.Nil(t, pollResp.Error)
	require.NotNil(t, pollResp.Commands)
	require.Len(t, pollResp.Commands, 1)
	assert.Equal(t, commandID1, pollResp.Commands[0].CommandId)
	assert.Equal(t, "REBOOT", pollResp.Commands[0].CommandType)
	assert.Equal(t, `{"delay": 5}`, pollResp.Commands[0].Payload)
	assert.Contains(t, []string{"PENDING", "SENT"}, pollResp.Commands[0].Status)

	// --- 8. CommandsAck: подтверждение выполнения команды ---
	ackResp, err := client.CommandsAck(authCtx, &apiv1.CommandsAckRequest{
		RouterId:  routerID1,
		CommandId: commandID1,
	})
	require.NoError(t, err)
	require.True(t, ackResp.Success, "expected ack success: %v", ackResp.Error)

	// --- 9. Повторный CommandsPoll: выданных команд больше нет (статус ACKED не возвращается) ---
	pollResp2, err := client.CommandsPoll(authCtx, &apiv1.CommandsPollRequest{RouterId: routerID1})
	require.NoError(t, err)
	require.Nil(t, pollResp2.Error)
	require.Empty(t, pollResp2.Commands, "acked command should not appear in poll")

	// --- 10. SendCommand без router_id — всем роутерам (должно быть 2 команды) ---
	sendAllResp, err := client.SendCommand(authCtx, &apiv1.SendCommandRequest{
		CommandType: "PING",
		Payload:     "{}",
	})
	require.NoError(t, err)
	require.Nil(t, sendAllResp.Error)
	require.Len(t, sendAllResp.CommandIds, 2, "expected two commands for two routers")

	// --- 11. CommandsPoll для обоих роутеров: у каждого по одной PING ---
	for _, routerID := range []string{routerID1, routerID2} {
		poll, err := client.CommandsPoll(authCtx, &apiv1.CommandsPollRequest{RouterId: routerID})
		require.NoError(t, err)
		require.Nil(t, poll.Error)
		require.Len(t, poll.Commands, 1, "router %s should have one PING command", routerID)
		assert.Equal(t, "PING", poll.Commands[0].CommandType)
	}

	// --- 12. Ошибки: неверный router_id при SendCommand ---
	badSend, err := client.SendCommand(authCtx, &apiv1.SendCommandRequest{
		RouterId:    "00000000-0000-0000-0000-000000000000",
		CommandType: "REBOOT",
	})
	require.NoError(t, err)
	require.NotNil(t, badSend.Error)
	assert.Contains(t, *badSend.Error, "not found")

	// --- 13. RegisterRouter без обязательных полей: ошибка в ответе ---
	regBad, err := client.RegisterRouter(authCtx, &apiv1.RegisterRouterRequest{
		InventoryNumber: "",
		Name:            "No Inv",
	})
	require.NoError(t, err)
	require.NotNil(t, regBad.Error)
	assert.Contains(t, *regBad.Error, "inventory_number")
}

// startAndInitKeycloak поднимает контейнер Keycloak и настраивает test-пользователя по аналогии с Java KeycloakInit.
// Возвращает контейнер (для Terminate), issuer URL и access token тестового пользователя.
func startAndInitKeycloak(ctx context.Context, t *testing.T) (testcontainers.Container, string, string) {
	t.Helper()

	req := testcontainers.ContainerRequest{
		Image: kcImage,
		Env: map[string]string{
			"KC_BOOTSTRAP_ADMIN_USERNAME": "admin",
			"KC_BOOTSTRAP_ADMIN_PASSWORD": "admin",
		},
		ExposedPorts: []string{"8080/tcp"},
		Cmd:          []string{"start-dev"},
		WaitingFor:   wait.ForLog("Listening on:").WithStartupTimeout(2 * time.Minute),
	}

	kcCtr, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	require.NoError(t, err)

	host, err := kcCtr.Host(ctx)
	require.NoError(t, err)
	port, err := kcCtr.MappedPort(ctx, "8080/tcp")
	require.NoError(t, err)

	baseURL := fmt.Sprintf("http://%s:%s", host, port.Port())
	issuer := baseURL + "/realms/master"

	client := &http.Client{
		Timeout: 10 * time.Second,
	}

	adminToken := getAdminToken(ctx, t, client, baseURL)
	createTestUser(ctx, t, client, baseURL, adminToken)
	enableDirectAccessGrants(ctx, t, client, baseURL, adminToken)
	userToken := getTestUserToken(ctx, t, client, baseURL)

	return kcCtr, issuer, userToken
}

func getAdminToken(ctx context.Context, t *testing.T, client *http.Client, base string) string {
	t.Helper()

	form := url.Values{}
	form.Set("grant_type", "password")
	form.Set("client_id", "admin-cli")
	form.Set("username", "admin")
	form.Set("password", "admin")

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, base+"/realms/master/protocol/openid-connect/token", strings.NewReader(form.Encode()))
	require.NoError(t, err)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := client.Do(req)
	require.NoError(t, err)
	defer resp.Body.Close()

	require.Equal(t, http.StatusOK, resp.StatusCode)

	var body struct {
		AccessToken string `json:"access_token"`
	}
	require.NoError(t, json.NewDecoder(resp.Body).Decode(&body))
	require.NotEmpty(t, body.AccessToken)
	return body.AccessToken
}

func createTestUser(ctx context.Context, t *testing.T, client *http.Client, base, adminToken string) {
	t.Helper()

	payload := map[string]any{
		"username": "device-api-user",
		"enabled":  true,
		"credentials": []map[string]any{
			{
				"type":      "password",
				"value":     "device-api-password",
				"temporary": false,
			},
		},
	}

	body, err := json.Marshal(payload)
	require.NoError(t, err)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, base+"/admin/realms/master/users", strings.NewReader(string(body)))
	require.NoError(t, err)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)

	resp, err := client.Do(req)
	require.NoError(t, err)
	defer resp.Body.Close()

	// 201 Created или 204 No Content — успешное создание/обновление; 409 — пользователь уже существует.
	if resp.StatusCode == http.StatusConflict {
		return
	}
	require.Contains(t, []int{http.StatusCreated, http.StatusNoContent}, resp.StatusCode)
}

func enableDirectAccessGrants(ctx context.Context, t *testing.T, client *http.Client, base, adminToken string) {
	t.Helper()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base+"/admin/realms/master/clients?clientId=admin-cli", nil)
	require.NoError(t, err)
	req.Header.Set("Authorization", "Bearer "+adminToken)

	resp, err := client.Do(req)
	require.NoError(t, err)
	defer resp.Body.Close()

	require.Equal(t, http.StatusOK, resp.StatusCode)

	var clients []map[string]any
	require.NoError(t, json.NewDecoder(resp.Body).Decode(&clients))
	require.NotEmpty(t, clients)

	clientNode := clients[0]
	if enabled, ok := clientNode["directAccessGrantsEnabled"].(bool); ok && enabled {
		return
	}

	clientID, ok := clientNode["id"].(string)
	require.True(t, ok)
	clientNode["directAccessGrantsEnabled"] = true

	body, err := json.Marshal(clientNode)
	require.NoError(t, err)

	putReq, err := http.NewRequestWithContext(ctx, http.MethodPut, base+"/admin/realms/master/clients/"+clientID, strings.NewReader(string(body)))
	require.NoError(t, err)
	putReq.Header.Set("Content-Type", "application/json")
	putReq.Header.Set("Authorization", "Bearer "+adminToken)

	putResp, err := client.Do(putReq)
	require.NoError(t, err)
	defer putResp.Body.Close()

	require.Contains(t, []int{http.StatusOK, http.StatusNoContent}, putResp.StatusCode)
}

func getTestUserToken(ctx context.Context, t *testing.T, client *http.Client, base string) string {
	t.Helper()

	form := url.Values{}
	form.Set("grant_type", "password")
	form.Set("client_id", "admin-cli")
	form.Set("username", "device-api-user")
	form.Set("password", "device-api-password")

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, base+"/realms/master/protocol/openid-connect/token", strings.NewReader(form.Encode()))
	require.NoError(t, err)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := client.Do(req)
	require.NoError(t, err)
	defer resp.Body.Close()

	require.Equal(t, http.StatusOK, resp.StatusCode)

	var body struct {
		AccessToken string `json:"access_token"`
	}
	require.NoError(t, json.NewDecoder(resp.Body).Decode(&body))
	require.NotEmpty(t, body.AccessToken)
	return body.AccessToken
}
