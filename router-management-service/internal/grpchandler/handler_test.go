package grpchandler_test

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"

	apiv1 "router-management-service/api/proto/api/v1"
	"router-management-service/internal/grpchandler"
	"router-management-service/internal/repository"
	"router-management-service/internal/service"
)

const bufSize = 1024 * 1024

func dialBufConn(t *testing.T, register func(*grpc.Server)) *grpc.ClientConn {
	t.Helper()
	lis := bufconn.Listen(bufSize)
	srv := grpc.NewServer()
	register(srv)
	go func() { _ = srv.Serve(lis) }()
	t.Cleanup(func() { srv.Stop() })
	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(func(context.Context, string) (net.Conn, error) {
			return lis.Dial()
		}),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	require.NoError(t, err)
	t.Cleanup(func() { _ = conn.Close() })
	return conn
}

func TestNewServer_NilLoggerUsesDefault(t *testing.T) {
	t.Parallel()
	svc := service.NewCommandService(&repository.MockRouterStore{}, &repository.MockCommandStore{}, slog.Default())
	s := grpchandler.NewServer(svc, nil)
	require.NotNil(t, s)
}

func TestServer_GRPC_RegisterRouterAndSendCommand(t *testing.T) {
	ctx := context.Background()
	rid := uuid.New()
	routers := &repository.MockRouterStore{
		CreateRouterFunc: func(ctx context.Context, r *repository.Router) error { return nil },
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			if id == rid {
				return &repository.Router{ID: rid, SerialNumber: "sn"}, nil
			}
			return nil, nil
		},
	}
	commands := &repository.MockCommandStore{
		CreateFunc: func(ctx context.Context, cmd *repository.Command) error {
			return nil
		},
		CreatePendingForAllRoutersFunc: func(ctx context.Context, commandType, payload string) ([]uuid.UUID, error) {
			return []uuid.UUID{uuid.New(), uuid.New()}, nil
		},
	}
	svc := service.NewCommandService(routers, commands, slog.Default())

	conn := dialBufConn(t, func(s *grpc.Server) {
		apiv1.RegisterRouterManagementServiceServer(s, grpchandler.NewServer(svc, slog.Default()))
	})
	client := apiv1.NewRouterManagementServiceClient(conn)

	reg, err := client.RegisterRouter(ctx, &apiv1.RegisterRouterRequest{Name: "n", InventoryNumber: "inv"})
	require.NoError(t, err)
	require.NotEmpty(t, reg.GetRouterId())
	require.Empty(t, reg.GetError())

	regErr, err := client.RegisterRouter(ctx, &apiv1.RegisterRouterRequest{Name: "n", InventoryNumber: ""})
	require.NoError(t, err)
	require.NotEmpty(t, regErr.GetError())

	sendBad, err := client.SendCommand(ctx, &apiv1.SendCommandRequest{
		RouterId:    "not-a-uuid",
		CommandType: "PING",
	})
	require.NoError(t, err)
	require.NotEmpty(t, sendBad.GetError())

	sendOne, err := client.SendCommand(ctx, &apiv1.SendCommandRequest{
		RouterId:    rid.String(),
		CommandType: "PING",
		Payload:     "{}",
	})
	require.NoError(t, err)
	require.Empty(t, sendOne.GetError())
	require.Len(t, sendOne.GetCommandIds(), 1)

	sendAll, err := client.SendCommand(ctx, &apiv1.SendCommandRequest{CommandType: "PING"})
	require.NoError(t, err)
	require.Empty(t, sendAll.GetError())
	require.Len(t, sendAll.GetCommandIds(), 2)
}

func TestServer_CommandsPoll_InvalidRouterID_ErrorInResponse(t *testing.T) {
	ctx := context.Background()
	svc := service.NewCommandService(&repository.MockRouterStore{}, &repository.MockCommandStore{}, slog.Default())
	conn := dialBufConn(t, func(s *grpc.Server) {
		apiv1.RegisterRouterManagementServiceServer(s, grpchandler.NewServer(svc, slog.Default()))
	})
	client := apiv1.NewRouterManagementServiceClient(conn)

	resp, err := client.CommandsPoll(ctx, &apiv1.CommandsPollRequest{RouterId: "bad"})
	require.NoError(t, err)
	require.NotEmpty(t, resp.GetError())
	require.Contains(t, resp.GetError(), "invalid router_id")
}

func TestServer_CommandsPoll_NotFoundWhenRouterUnknown(t *testing.T) {
	ctx := context.Background()
	rid := uuid.New()
	svc := service.NewCommandService(&repository.MockRouterStore{}, &repository.MockCommandStore{}, slog.Default())
	conn := dialBufConn(t, func(s *grpc.Server) {
		apiv1.RegisterRouterManagementServiceServer(s, grpchandler.NewServer(svc, slog.Default()))
	})
	client := apiv1.NewRouterManagementServiceClient(conn)

	resp, err := client.CommandsPoll(ctx, &apiv1.CommandsPollRequest{RouterId: rid.String()})
	require.NoError(t, err)
	require.NotEmpty(t, resp.GetError())
	require.Contains(t, resp.GetError(), "router not found")
}

func TestServer_CommandsPoll_ServiceErrorInResponse(t *testing.T) {
	ctx := context.Background()
	rid := uuid.New()
	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			if id == rid {
				return &repository.Router{ID: rid, SerialNumber: "sn"}, nil
			}
			return nil, nil
		},
	}
	commands := &repository.MockCommandStore{
		ListPendingOrSentByRouterFunc: func(ctx context.Context, routerID uuid.UUID) ([]*repository.Command, error) {
			return nil, errors.New("db down")
		},
	}
	svc := service.NewCommandService(routers, commands, slog.Default())
	conn := dialBufConn(t, func(s *grpc.Server) {
		apiv1.RegisterRouterManagementServiceServer(s, grpchandler.NewServer(svc, slog.Default()))
	})
	client := apiv1.NewRouterManagementServiceClient(conn)

	resp, err := client.CommandsPoll(ctx, &apiv1.CommandsPollRequest{RouterId: rid.String()})
	require.NoError(t, err)
	require.NotEmpty(t, resp.GetError())
	require.Contains(t, resp.GetError(), "db down")
}

func TestServer_CommandsPoll_And_Ack(t *testing.T) {
	ctx := context.Background()
	rid := uuid.New()
	cmdID := uuid.New()
	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			if id == rid {
				return &repository.Router{ID: rid, SerialNumber: "sn"}, nil
			}
			return nil, nil
		},
	}
	commands := &repository.MockCommandStore{
		ListPendingOrSentByRouterFunc: func(ctx context.Context, routerID uuid.UUID) ([]*repository.Command, error) {
			return []*repository.Command{{
				ID:          cmdID,
				RouterID:    rid,
				CommandType: "X",
				Payload:     "p",
				Status:      "PENDING",
				CreatedAt:   time.Now(),
			}}, nil
		},
	}
	svc := service.NewCommandService(routers, commands, slog.Default())
	conn := dialBufConn(t, func(s *grpc.Server) {
		apiv1.RegisterRouterManagementServiceServer(s, grpchandler.NewServer(svc, slog.Default()))
	})
	client := apiv1.NewRouterManagementServiceClient(conn)

	poll, err := client.CommandsPoll(ctx, &apiv1.CommandsPollRequest{RouterId: rid.String()})
	require.NoError(t, err)
	require.Empty(t, poll.GetError())
	require.Len(t, poll.GetCommands(), 1)

	ackBadRouter, err := client.CommandsAck(ctx, &apiv1.CommandsAckRequest{
		RouterId: "x", CommandId: cmdID.String(),
	})
	require.NoError(t, err)
	require.False(t, ackBadRouter.GetSuccess())
	require.NotEmpty(t, ackBadRouter.GetError())

	ackBadCmd, err := client.CommandsAck(ctx, &apiv1.CommandsAckRequest{
		RouterId: rid.String(), CommandId: "y",
	})
	require.NoError(t, err)
	require.False(t, ackBadCmd.GetSuccess())

	ackOk, err := client.CommandsAck(ctx, &apiv1.CommandsAckRequest{
		RouterId: rid.String(), CommandId: cmdID.String(),
	})
	require.NoError(t, err)
	require.True(t, ackOk.GetSuccess())
}

func TestServer_SendCommand_ServiceErrorInResponse(t *testing.T) {
	ctx := context.Background()
	rid := uuid.New()
	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			return &repository.Router{ID: rid}, nil
		},
	}
	commands := &repository.MockCommandStore{
		CreateFunc: func(ctx context.Context, cmd *repository.Command) error {
			return errors.New("insert failed")
		},
	}
	svc := service.NewCommandService(routers, commands, slog.Default())
	conn := dialBufConn(t, func(s *grpc.Server) {
		apiv1.RegisterRouterManagementServiceServer(s, grpchandler.NewServer(svc, slog.Default()))
	})
	client := apiv1.NewRouterManagementServiceClient(conn)

	resp, err := client.SendCommand(ctx, &apiv1.SendCommandRequest{
		RouterId: rid.String(), CommandType: "PING",
	})
	require.NoError(t, err)
	require.NotEmpty(t, resp.GetError())
}
