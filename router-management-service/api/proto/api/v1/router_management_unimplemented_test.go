package apiv1

import (
	"context"
	"testing"

	"github.com/stretchr/testify/require"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func TestUnimplementedRouterManagementServiceServer_AllRPCs(t *testing.T) {
	t.Parallel()
	var s UnimplementedRouterManagementServiceServer

	_, err := s.RegisterRouter(context.Background(), &RegisterRouterRequest{})
	st, ok := status.FromError(err)
	require.True(t, ok)
	require.Equal(t, codes.Unimplemented, st.Code())

	_, err = s.SendCommand(context.Background(), &SendCommandRequest{})
	st, ok = status.FromError(err)
	require.True(t, ok)
	require.Equal(t, codes.Unimplemented, st.Code())

	_, err = s.CommandsPoll(context.Background(), &CommandsPollRequest{})
	st, ok = status.FromError(err)
	require.True(t, ok)
	require.Equal(t, codes.Unimplemented, st.Code())

	_, err = s.CommandsAck(context.Background(), &CommandsAckRequest{})
	st, ok = status.FromError(err)
	require.True(t, ok)
	require.Equal(t, codes.Unimplemented, st.Code())
}
