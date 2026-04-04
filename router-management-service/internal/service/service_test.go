// Тесты бизнес-логики CommandService с использованием mock-хранилищ (MockRouterStore, MockCommandStore).
package service

import (
	"context"
	"errors"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"router-management-service/internal/repository"

	"github.com/stretchr/testify/require"
)

// TestCommandService_SendCommand_OneRouter проверяет создание команды для существующего роутера и соответствие полей (router_id, command_type, command_id).
func TestCommandService_SendCommand_OneRouter(t *testing.T) {
	ctx := context.Background()
	r1 := uuid.New()
	var createdCmd *repository.Command
	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			if id == r1 {
				return &repository.Router{ID: id, SerialNumber: "sn1", CreatedAt: time.Now()}, nil
			}
			return nil, nil
		},
	}
	commands := &repository.MockCommandStore{
		CreateFunc: func(ctx context.Context, cmd *repository.Command) error {
			createdCmd = cmd
			return nil
		},
	}
	svc := NewCommandService(routers, commands, slog.Default())

	id, err := svc.SendCommand(ctx, r1, "PING", "")
	if err != nil {
		t.Fatalf("SendCommand: %v", err)
	}
	if id == uuid.Nil {
		t.Error("expected non-nil command id")
	}
	if createdCmd == nil {
		t.Fatal("Create was not called")
	}
	if createdCmd.RouterID != r1 {
		t.Errorf("expected router_id %s, got %s", r1, createdCmd.RouterID)
	}
	if createdCmd.CommandType != "PING" {
		t.Errorf("expected command_type PING, got %q", createdCmd.CommandType)
	}
	if createdCmd.ID != id {
		t.Errorf("expected command id %s, got %s", id, createdCmd.ID)
	}
}

// TestCommandService_SendCommand_WithPayload проверяет, что payload передаётся в созданную команду (например, "{}" для REBOOT).
func TestCommandService_SendCommand_WithPayload(t *testing.T) {
	ctx := context.Background()
	r1 := uuid.New()
	var createdCmd *repository.Command
	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			if id == r1 {
				return &repository.Router{ID: id, SerialNumber: "sn1", CreatedAt: time.Now()}, nil
			}
			return nil, nil
		},
	}
	commands := &repository.MockCommandStore{
		CreateFunc: func(ctx context.Context, cmd *repository.Command) error {
			createdCmd = cmd
			return nil
		},
	}
	svc := NewCommandService(routers, commands, slog.Default())

	_, err := svc.SendCommand(ctx, r1, "REBOOT", "{}")
	if err != nil {
		t.Fatalf("SendCommand: %v", err)
	}
	if createdCmd == nil || createdCmd.Payload != "{}" {
		t.Errorf("expected payload %q, got %q", "{}", createdCmd.Payload)
	}
}

// TestCommandService_SendCommand_EmptyCommandType проверяет, что пустой command_type возвращает ошибку.
func TestCommandService_SendCommand_EmptyCommandType(t *testing.T) {
	ctx := context.Background()
	svc := NewCommandService(&repository.MockRouterStore{}, &repository.MockCommandStore{}, slog.Default())
	_, err := svc.SendCommand(ctx, uuid.New(), "", "")
	if err == nil {
		t.Fatal("expected error for empty command_type")
	}
}

// TestCommandService_SendCommand_RouterNotFound проверяет ошибку при отправке команды несуществующему роутеру.
func TestCommandService_SendCommand_RouterNotFound(t *testing.T) {
	ctx := context.Background()
	r1 := uuid.New()
	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			return nil, nil // router does not exist
		},
	}
	svc := NewCommandService(routers, &repository.MockCommandStore{}, slog.Default())
	_, err := svc.SendCommand(ctx, r1, "REBOOT", "")
	if err == nil {
		t.Fatal("expected error when router not found")
	}
	if !strings.Contains(err.Error(), "router not found") {
		t.Errorf("expected router not found message, got %v", err)
	}
	if !errors.Is(err, ErrRouterNotFound) {
		t.Errorf("expected ErrRouterNotFound, got %v", err)
	}
}

// TestCommandService_PollCommands_UpdatesLastSeen проверяет, что при опросе команд вызывается UpdateLastSeen для зарегистрированного роутера.
func TestCommandService_PollCommands_UpdatesLastSeen(t *testing.T) {
	ctx := context.Background()
	r1 := uuid.New()
	regRouter := &repository.Router{ID: r1, SerialNumber: "sn1", CreatedAt: time.Now()}
	updateCalled := false
	var updateID uuid.UUID
	var updateAt time.Time
	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			if id != r1 {
				t.Errorf("expected router %s, got %s", r1, id)
			}
			return regRouter, nil
		},
		UpdateLastSeenFunc: func(ctx context.Context, routerID uuid.UUID, at time.Time) error {
			updateCalled = true
			updateID = routerID
			updateAt = at
			return nil
		},
	}
	commands := &repository.MockCommandStore{
		ListPendingOrSentByRouterFunc: func(ctx context.Context, routerID uuid.UUID) ([]*repository.Command, error) {
			return []*repository.Command{}, nil
		},
	}
	svc := NewCommandService(routers, commands, slog.Default())

	before := time.Now()
	_, err := svc.PollCommands(ctx, r1)
	if err != nil {
		t.Fatalf("PollCommands: %v", err)
	}
	if !updateCalled {
		t.Fatal("UpdateLastSeen was not called")
	}
	if updateID != r1 {
		t.Errorf("expected UpdateLastSeen router_id %s, got %s", r1, updateID)
	}
	if updateAt.Before(before) {
		t.Errorf("expected last_seen_at >= test start")
	}
}

// TestCommandService_PollCommands_RouterNotFound проверяет ошибку при опросе незарегистрированного роутера.
func TestCommandService_PollCommands_RouterNotFound(t *testing.T) {
	ctx := context.Background()
	r1 := uuid.New()
	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			return nil, nil
		},
	}
	svc := NewCommandService(routers, &repository.MockCommandStore{
		ListPendingOrSentByRouterFunc: func(ctx context.Context, routerID uuid.UUID) ([]*repository.Command, error) {
			t.Fatal("ListPendingOrSentByRouter must not be called when router is unknown")
			return nil, nil
		},
	}, slog.Default())

	_, err := svc.PollCommands(ctx, r1)
	require.Error(t, err)
	require.True(t, errors.Is(err, ErrRouterNotFound), "expected ErrRouterNotFound, got %v", err)
}

// TestCommandService_PollCommands_MarksPendingSent проверяет, что при наличии PENDING команд
// вызывается MarkSent, а команды переводятся в SENT со значением SentAt.
func TestCommandService_PollCommands_MarksPendingSent(t *testing.T) {
	ctx := context.Background()
	r1 := uuid.New()

	cPending := &repository.Command{
		ID:          uuid.New(),
		RouterID:    r1,
		CommandType: "PING",
		Payload:     "",
		Status:      repository.StatusPending,
	}
	cSent := &repository.Command{
		ID:          uuid.New(),
		RouterID:    r1,
		CommandType: "PING",
		Payload:     "",
		Status:      repository.StatusSent,
		SentAt:      nil,
	}

	updateCalled := false
	markSentCalled := false
	var sentAtArg time.Time
	var markedIDs []uuid.UUID

	routers := &repository.MockRouterStore{
		GetByIDFunc: func(ctx context.Context, id uuid.UUID) (*repository.Router, error) {
			require.Equal(t, r1, id)
			return &repository.Router{ID: id, SerialNumber: "sn1", CreatedAt: time.Now()}, nil
		},
		UpdateLastSeenFunc: func(ctx context.Context, routerID uuid.UUID, at time.Time) error {
			updateCalled = true
			require.Equal(t, r1, routerID)
			return nil
		},
	}

	commands := &repository.MockCommandStore{
		ListPendingOrSentByRouterFunc: func(ctx context.Context, routerID uuid.UUID) ([]*repository.Command, error) {
			return []*repository.Command{cPending, cSent}, nil
		},
		MarkSentFunc: func(ctx context.Context, commandIDs []uuid.UUID, sentAt time.Time) error {
			markSentCalled = true
			sentAtArg = sentAt
			markedIDs = append([]uuid.UUID(nil), commandIDs...)
			return nil
		},
	}

	svc := NewCommandService(routers, commands, slog.Default())
	out, err := svc.PollCommands(ctx, r1)
	require.NoError(t, err)

	require.True(t, updateCalled, "expected UpdateLastSeen to be called")
	require.True(t, markSentCalled, "expected MarkSent to be called for pending commands")

	// MarkSent должен получать только pending command_id.
	require.Len(t, markedIDs, 1)
	require.Equal(t, cPending.ID, markedIDs[0])

	require.Len(t, out, 2)

	require.Equal(t, repository.StatusSent, out[0].Status)
	require.NotNil(t, out[0].SentAt)
	require.True(t, out[0].SentAt.Equal(sentAtArg))

	// SENT команда не должна переподставляться.
	require.Equal(t, repository.StatusSent, out[1].Status)
	require.Nil(t, out[1].SentAt)
}

func TestCommandService_RegisterRouter(t *testing.T) {
	ctx := context.Background()

	t.Run("success", func(t *testing.T) {
		inv := "INV-001"
		name := "Router Alpha"
		model := "TP-Link"
		loc := "Office 1"

		createCalled := false
		var created *repository.Router

		routers := &repository.MockRouterStore{
			CreateRouterFunc: func(ctx context.Context, r *repository.Router) error {
				createCalled = true
				created = r
				return nil
			},
		}
		svc := NewCommandService(routers, &repository.MockCommandStore{}, slog.Default())

		id, err := svc.RegisterRouter(ctx, inv, name, model, loc)
		require.NoError(t, err)
		require.NotEqual(t, uuid.Nil, id)
		require.True(t, createCalled, "expected CreateRouter to be called")
		require.NotNil(t, created)
		require.Equal(t, id, created.ID)
		require.Equal(t, inv, created.SerialNumber)
		require.Equal(t, inv, created.InventoryNumber)
		require.Equal(t, name, created.Name)
		require.Equal(t, model, created.Model)
		require.Equal(t, loc, created.Location)
	})

	t.Run("empty inventory_number", func(t *testing.T) {
		routers := &repository.MockRouterStore{
			CreateRouterFunc: func(ctx context.Context, r *repository.Router) error {
				t.Fatal("CreateRouter should not be called")
				return nil
			},
		}
		svc := NewCommandService(routers, &repository.MockCommandStore{}, slog.Default())
		_, err := svc.RegisterRouter(ctx, "", "Name", "", "")
		require.Error(t, err)
		require.Contains(t, err.Error(), "inventory_number is required")
	})

	t.Run("empty name", func(t *testing.T) {
		routers := &repository.MockRouterStore{
			CreateRouterFunc: func(ctx context.Context, r *repository.Router) error {
				t.Fatal("CreateRouter should not be called")
				return nil
			},
		}
		svc := NewCommandService(routers, &repository.MockCommandStore{}, slog.Default())
		_, err := svc.RegisterRouter(ctx, "INV", "", "", "")
		require.Error(t, err)
		require.Contains(t, err.Error(), "name is required")
	})
}

// TestCommandService_SendCommandToAllRouters проверяет делегирование в CreatePendingForAllRouters (атомарная рассылка в хранилище).
func TestCommandService_SendCommandToAllRouters(t *testing.T) {
	ctx := context.Background()
	id1, id2 := uuid.New(), uuid.New()
	var gotType, gotPayload string
	routers := &repository.MockRouterStore{}
	commands := &repository.MockCommandStore{
		CreatePendingForAllRoutersFunc: func(ctx context.Context, commandType, payload string) ([]uuid.UUID, error) {
			gotType = commandType
			gotPayload = payload
			return []uuid.UUID{id1, id2}, nil
		},
	}
	svc := NewCommandService(routers, commands, slog.Default())

	ids, err := svc.SendCommandToAllRouters(ctx, "PING", "")
	if err != nil {
		t.Fatalf("SendCommandToAllRouters: %v", err)
	}
	if len(ids) != 2 {
		t.Fatalf("expected 2 command ids, got %d", len(ids))
	}
	if ids[0] != id1 || ids[1] != id2 {
		t.Errorf("expected ids from store, got %v", ids)
	}
	if gotType != "PING" || gotPayload != "" {
		t.Errorf("unexpected type/payload %q %q", gotType, gotPayload)
	}
}

// TestCommandService_SendCommandToAllRouters_StoreError проверяет ошибку при рассылке.
func TestCommandService_SendCommandToAllRouters_StoreError(t *testing.T) {
	ctx := context.Background()
	commands := &repository.MockCommandStore{
		CreatePendingForAllRoutersFunc: func(ctx context.Context, commandType, payload string) ([]uuid.UUID, error) {
			return nil, errors.New("db unavailable")
		},
	}
	svc := NewCommandService(&repository.MockRouterStore{}, commands, slog.Default())

	ids, err := svc.SendCommandToAllRouters(ctx, "PING", "")
	require.Error(t, err)
	require.Nil(t, ids)
	require.Contains(t, err.Error(), "create commands")
}

// TestCommandService_SendCommandToAllRouters_EmptyCommandType проверяет ошибку при пустом command_type.
func TestCommandService_SendCommandToAllRouters_EmptyCommandType(t *testing.T) {
	ctx := context.Background()
	svc := NewCommandService(&repository.MockRouterStore{}, &repository.MockCommandStore{}, slog.Default())
	_, err := svc.SendCommandToAllRouters(ctx, "", "")
	if err == nil {
		t.Fatal("expected error for empty command_type")
	}
}

// TestCommandService_SendCommandToAllRouters_NoRouters проверяет пустой список command_ids при отсутствии роутеров.
func TestCommandService_SendCommandToAllRouters_NoRouters(t *testing.T) {
	ctx := context.Background()
	svc := NewCommandService(&repository.MockRouterStore{}, &repository.MockCommandStore{}, slog.Default())
	ids, err := svc.SendCommandToAllRouters(ctx, "PING", "")
	if err != nil {
		t.Fatalf("SendCommandToAllRouters: %v", err)
	}
	if len(ids) != 0 {
		t.Errorf("expected 0 command ids when no routers, got %d", len(ids))
	}
}

// TestCommandService_AckCommand проверяет, что AckCommand вызывает Ack в хранилище с правильными command_id и router_id.
func TestCommandService_AckCommand(t *testing.T) {
	ctx := context.Background()
	r1 := uuid.New()
	c1 := uuid.New()
	ackCalled := false
	commands := &repository.MockCommandStore{
		AckFunc: func(ctx context.Context, commandID, routerID uuid.UUID, ackedAt time.Time) error {
			ackCalled = true
			if commandID != c1 || routerID != r1 {
				return errors.New("wrong ids")
			}
			return nil
		},
	}
	svc := NewCommandService(&repository.MockRouterStore{}, commands, slog.Default())

	err := svc.AckCommand(ctx, r1, c1)
	if err != nil {
		t.Fatalf("AckCommand: %v", err)
	}
	if !ackCalled {
		t.Error("Ack was not called")
	}
}

// TestCommandService_FailStaleSentCommands вызывает FailSentBefore в хранилище с корректным cutoff и возвращает количество обновлённых команд.
func TestCommandService_FailStaleSentCommands(t *testing.T) {
	ctx := context.Background()
	var called bool
	var beforeArg time.Time
	r1, r2 := uuid.New(), uuid.New()
	c1, c2 := uuid.New(), uuid.New()
	commands := &repository.MockCommandStore{
		FailSentBeforeFunc: func(ctx context.Context, before time.Time) ([]*repository.Command, error) {
			called = true
			beforeArg = before
			return []*repository.Command{
				{ID: c1, RouterID: r1},
				{ID: c2, RouterID: r2},
			}, nil
		},
	}
	svc := NewCommandService(&repository.MockRouterStore{}, commands, slog.Default())

	affected, err := svc.FailStaleSentCommands(ctx, 10*time.Minute)
	if err != nil {
		t.Fatalf("FailStaleSentCommands: %v", err)
	}
	if !called {
		t.Fatal("FailSentBefore was not called")
	}
	if affected != 2 {
		t.Errorf("expected affected=2, got %d", affected)
	}
	if time.Since(beforeArg) <= 0 {
		t.Errorf("expected cutoff in the past, got %v", beforeArg)
	}
}

// TestCommandService_FailStaleSentCommands_NonPositiveMaxAge не вызывает репозиторий при неположительном maxAge.
func TestCommandService_FailStaleSentCommands_NonPositiveMaxAge(t *testing.T) {
	ctx := context.Background()
	commands := &repository.MockCommandStore{}
	svc := NewCommandService(&repository.MockRouterStore{}, commands, slog.Default())

	affected, err := svc.FailStaleSentCommands(ctx, 0)
	if err != nil {
		t.Fatalf("FailStaleSentCommands: %v", err)
	}
	if affected != 0 {
		t.Errorf("expected affected=0, got %d", affected)
	}
}
