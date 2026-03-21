// Тесты вызывают сгенерированные gRPC client/server стабы (grpc.pb.go) в пакете apiv1,
// чтобы покрытие считалось при go test ./api/proto/api/v1, без зависимости от internal/.
package apiv1_test

import (
	"context"
	"net"
	"testing"

	"github.com/stretchr/testify/require"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"

	apiv1 "router-management-service/api/proto/api/v1"
)

const bufSize = 1024 * 1024

type grpcServiceStub struct {
	apiv1.UnimplementedRouterManagementServiceServer
}

func (grpcServiceStub) RegisterRouter(_ context.Context, r *apiv1.RegisterRouterRequest) (*apiv1.RegisterRouterResponse, error) {
	_, _, _, _ = r.GetInventoryNumber(), r.GetName(), r.GetModel(), r.GetLocation()
	return &apiv1.RegisterRouterResponse{RouterId: "550e8400-e29b-41d4-a716-446655440000"}, nil
}

func (grpcServiceStub) SendCommand(_ context.Context, r *apiv1.SendCommandRequest) (*apiv1.SendCommandResponse, error) {
	_, _, _ = r.GetRouterId(), r.GetCommandType(), r.GetPayload()
	return &apiv1.SendCommandResponse{CommandIds: []string{"09568237-e2f8-41eb-a540-295ab44185e5"}}, nil
}

func (grpcServiceStub) CommandsPoll(_ context.Context, r *apiv1.CommandsPollRequest) (*apiv1.CommandsPollResponse, error) {
	_ = r.GetRouterId()
	return &apiv1.CommandsPollResponse{
		Commands: []*apiv1.CommandMessage{{
			CommandId: "c1", CommandType: "PING", Payload: "{}", Status: "PENDING",
		}},
	}, nil
}

func (grpcServiceStub) CommandsAck(_ context.Context, r *apiv1.CommandsAckRequest) (*apiv1.CommandsAckResponse, error) {
	_, _ = r.GetRouterId(), r.GetCommandId()
	return &apiv1.CommandsAckResponse{Success: true}, nil
}

func TestRouterManagementService_GRPCGeneratedStack(t *testing.T) {
	t.Parallel()
	testGRPCGeneratedStack(t, true)
}

func TestRouterManagementService_GRPCGeneratedStack_NoUnaryInterceptor(t *testing.T) {
	t.Parallel()
	testGRPCGeneratedStack(t, false)
}

func testGRPCGeneratedStack(t *testing.T, withInterceptor bool) {
	t.Helper()
	ctx := context.Background()
	lis := bufconn.Listen(bufSize)
	var srv *grpc.Server
	if withInterceptor {
		srv = grpc.NewServer(grpc.ChainUnaryInterceptor(func(ctx context.Context, req any, info *grpc.UnaryServerInfo, h grpc.UnaryHandler) (any, error) {
			return h(ctx, req)
		}))
	} else {
		srv = grpc.NewServer()
	}
	apiv1.RegisterRouterManagementServiceServer(srv, grpcServiceStub{})
	go func() { _ = srv.Serve(lis) }()
	t.Cleanup(func() { srv.Stop() })

	conn, err := grpc.NewClient("passthrough:///buf",
		grpc.WithContextDialer(func(context.Context, string) (net.Conn, error) { return lis.Dial() }),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	require.NoError(t, err)
	t.Cleanup(func() { _ = conn.Close() })

	cli := apiv1.NewRouterManagementServiceClient(conn)

	reg, err := cli.RegisterRouter(ctx, &apiv1.RegisterRouterRequest{InventoryNumber: "i", Name: "n"})
	require.NoError(t, err)
	require.NotEmpty(t, reg.GetRouterId())

	send, err := cli.SendCommand(ctx, &apiv1.SendCommandRequest{RouterId: reg.GetRouterId(), CommandType: "PING"})
	require.NoError(t, err)
	require.Len(t, send.GetCommandIds(), 1)

	poll, err := cli.CommandsPoll(ctx, &apiv1.CommandsPollRequest{RouterId: reg.GetRouterId()})
	require.NoError(t, err)
	require.Len(t, poll.GetCommands(), 1)

	ack, err := cli.CommandsAck(ctx, &apiv1.CommandsAckRequest{
		RouterId: reg.GetRouterId(), CommandId: send.GetCommandIds()[0],
	})
	require.NoError(t, err)
	require.True(t, ack.GetSuccess())
}

