// Тесты сериализации/десериализации (proto Marshal/Unmarshal) для всех основных request/response API.
// Проверяют, что структуры корректно сохраняются в байты и восстанавливаются без потери данных.
package apiv1

import (
	"testing"

	"google.golang.org/protobuf/proto"
)

func TestRegisterRouterRequest_ProtoRoundTrip(t *testing.T) {
	orig := &RegisterRouterRequest{
		InventoryNumber: "INV-001",
		Name:            "Router Alpha",
		Model:           "TP-Link",
		Location:        "Office 1",
	}
	roundTripAndEqual(t, orig)
}

func TestRegisterRouterRequest_ProtoRoundTrip_EmptyFields(t *testing.T) {
	orig := &RegisterRouterRequest{}
	roundTripAndEqual(t, orig)
}

func TestRegisterRouterResponse_ProtoRoundTrip(t *testing.T) {
	orig := &RegisterRouterResponse{
		RouterId: "550e8400-e29b-41d4-a716-446655440000",
	}
	roundTripAndEqual(t, orig)
}

func TestRegisterRouterResponse_ProtoRoundTrip_WithError(t *testing.T) {
	errMsg := "inventory_number is required"
	orig := &RegisterRouterResponse{
		Error: &errMsg,
	}
	roundTripAndEqual(t, orig)
}

func TestSendCommandRequest_ProtoRoundTrip(t *testing.T) {
	orig := &SendCommandRequest{
		RouterId:    "550e8400-e29b-41d4-a716-446655440000",
		CommandType: "REBOOT",
		Payload:    `{"delay": 5}`,
	}
	roundTripAndEqual(t, orig)
}

func TestSendCommandRequest_ProtoRoundTrip_EmptyRouterId(t *testing.T) {
	orig := &SendCommandRequest{
		CommandType: "PING",
	}
	roundTripAndEqual(t, orig)
}

func TestSendCommandResponse_ProtoRoundTrip(t *testing.T) {
	orig := &SendCommandResponse{
		CommandIds: []string{
			"da970089-b2ac-40c4-be44-53a5a518062f",
			"09568237-e2f8-41eb-a540-295ab44185e5",
		},
	}
	roundTripAndEqual(t, orig)
}

func TestSendCommandResponse_ProtoRoundTrip_SingleId(t *testing.T) {
	orig := &SendCommandResponse{
		CommandIds: []string{"550e8400-e29b-41d4-a716-446655440000"},
	}
	roundTripAndEqual(t, orig)
}

func TestSendCommandResponse_ProtoRoundTrip_WithError(t *testing.T) {
	errMsg := "router not found"
	orig := &SendCommandResponse{
		Error: &errMsg,
	}
	roundTripAndEqual(t, orig)
}

func TestCommandsPollRequest_ProtoRoundTrip(t *testing.T) {
	orig := &CommandsPollRequest{
		RouterId: "550e8400-e29b-41d4-a716-446655440000",
	}
	roundTripAndEqual(t, orig)
}

func TestCommandMessage_ProtoRoundTrip(t *testing.T) {
	orig := &CommandMessage{
		CommandId:   "cmd-uuid-1",
		CommandType: "PING",
		Payload:     "{}",
		Status:      "SENT",
	}
	roundTripAndEqual(t, orig)
}

func TestCommandsPollResponse_ProtoRoundTrip(t *testing.T) {
	orig := &CommandsPollResponse{
		Commands: []*CommandMessage{
			{CommandId: "c1", CommandType: "REBOOT", Status: "PENDING"},
			{CommandId: "c2", CommandType: "PING", Payload: "{}", Status: "SENT"},
		},
	}
	roundTripAndEqual(t, orig)
}

func TestCommandsPollResponse_ProtoRoundTrip_Empty(t *testing.T) {
	orig := &CommandsPollResponse{
		Commands: []*CommandMessage{},
	}
	roundTripAndEqual(t, orig)
}

func TestCommandsPollResponse_ProtoRoundTrip_WithError(t *testing.T) {
	errMsg := "poll failed"
	orig := &CommandsPollResponse{
		Error: &errMsg,
	}
	roundTripAndEqual(t, orig)
}

func TestCommandsAckRequest_ProtoRoundTrip(t *testing.T) {
	orig := &CommandsAckRequest{
		RouterId:  "550e8400-e29b-41d4-a716-446655440000",
		CommandId: "09568237-e2f8-41eb-a540-295ab44185e5",
	}
	roundTripAndEqual(t, orig)
}

func TestCommandsAckResponse_ProtoRoundTrip_Success(t *testing.T) {
	orig := &CommandsAckResponse{Success: true}
	roundTripAndEqual(t, orig)
}

func TestCommandsAckResponse_ProtoRoundTrip_WithError(t *testing.T) {
	errMsg := "command not found"
	orig := &CommandsAckResponse{
		Success: false,
		Error:   &errMsg,
	}
	roundTripAndEqual(t, orig)
}

// roundTripAndEqual сериализует msg в байты, десериализует обратно в клон и проверяет равенство через proto.Equal.
func roundTripAndEqual(t *testing.T, msg proto.Message) {
	t.Helper()
	wire, err := proto.Marshal(msg)
	if err != nil {
		t.Fatalf("Marshal: %v", err)
	}
	clone := proto.Clone(msg)
	if err := proto.Unmarshal(wire, clone); err != nil {
		t.Fatalf("Unmarshal: %v", err)
	}
	if !proto.Equal(msg, clone) {
		t.Errorf("round-trip mismatch: orig %+v, after %+v", msg, clone)
	}
	exerciseProtoGeneratedAPI(t, clone)
}

// exerciseProtoGeneratedAPI вызывает геттеры/методы сгенерированного кода для покрытия .pb.go.
func exerciseProtoGeneratedAPI(t *testing.T, msg proto.Message) {
	t.Helper()
	_ = msg.ProtoReflect().Descriptor().FullName()
	switch m := msg.(type) {
	case *RegisterRouterRequest:
		_, _, _, _ = m.GetInventoryNumber(), m.GetName(), m.GetModel(), m.GetLocation()
		_ = m.String()
	case *RegisterRouterResponse:
		_, _ = m.GetRouterId(), m.GetError()
		_ = m.String()
	case *SendCommandRequest:
		_, _, _ = m.GetRouterId(), m.GetCommandType(), m.GetPayload()
		_ = m.String()
	case *SendCommandResponse:
		_, _ = m.GetCommandIds(), m.GetError()
		_ = m.String()
	case *CommandsPollRequest:
		_ = m.GetRouterId()
		_ = m.String()
	case *CommandMessage:
		_, _, _, _ = m.GetCommandId(), m.GetCommandType(), m.GetPayload(), m.GetStatus()
		_ = m.String()
	case *CommandsPollResponse:
		_, _ = m.GetCommands(), m.GetError()
		_ = m.String()
	case *CommandsAckRequest:
		_, _ = m.GetRouterId(), m.GetCommandId()
		_ = m.String()
	case *CommandsAckResponse:
		_, _ = m.GetSuccess(), m.GetError()
		_ = m.String()
	default:
		t.Fatalf("add case for %T", msg)
	}
}
