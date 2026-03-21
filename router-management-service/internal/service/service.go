// Package service — бизнес-логика: регистрация роутеров, отправка команд, опрос команд роутером и подтверждение выполнения.
package service

import (
	"context"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/google/uuid"
	"router-management-service/internal/repository"
)

// CommandService реализует сценарии API: RegisterRouter, SendCommand, PollCommands, AckCommand; использует RouterStore и CommandStore.
type CommandService struct {
	routers  repository.RouterStore
	commands repository.CommandStore
	log      *slog.Logger
}

// NewCommandService создаёт сервис с заданными хранилищами и логгером.
func NewCommandService(routers repository.RouterStore, commands repository.CommandStore, log *slog.Logger) *CommandService {
	if log == nil {
		log = slog.Default()
	}
	return &CommandService{routers: routers, commands: commands, log: log}
}

// RegisterRouter создаёт нового роутера по инвентарному номеру и имени (обязательны), опционально model и location; возвращает сгенерированный UUID.
func (s *CommandService) RegisterRouter(ctx context.Context, inventoryNumber, name, model, location string) (uuid.UUID, error) {
	inventoryNumber = strings.TrimSpace(inventoryNumber)
	name = strings.TrimSpace(name)
	if inventoryNumber == "" {
		return uuid.Nil, fmt.Errorf("inventory_number is required")
	}
	if name == "" {
		return uuid.Nil, fmt.Errorf("name is required")
	}
	r := &repository.Router{
		ID:              uuid.New(),
		SerialNumber:    inventoryNumber,
		InventoryNumber: inventoryNumber,
		Name:            name,
		Model:           strings.TrimSpace(model),
		Location:        strings.TrimSpace(location),
		CreatedAt:       time.Now(),
	}
	if err := s.routers.CreateRouter(ctx, r); err != nil {
		return uuid.Nil, err
	}
	s.log.InfoContext(ctx, "register_router", "router_id", r.ID, "inventory_number", inventoryNumber, "name", name)
	return r.ID, nil
}

// SendCommand создаёт одну команду для указанного роутера; commandType обязателен, payload — опциональный JSON. Возвращает id команды или ошибку, если роутер не найден.
func (s *CommandService) SendCommand(ctx context.Context, routerID uuid.UUID, commandType string, payload string) (uuid.UUID, error) {
	if commandType == "" {
		return uuid.Nil, fmt.Errorf("command_type is required")
	}
	router, err := s.routers.GetByID(ctx, routerID)
	if err != nil {
		return uuid.Nil, fmt.Errorf("get router: %w", err)
	}
	if router == nil {
		return uuid.Nil, fmt.Errorf("router not found: %s", routerID)
	}
	cmd := &repository.Command{
		ID:          uuid.New(),
		RouterID:    routerID,
		CommandType: commandType,
		Payload:     payload,
		Status:      repository.StatusPending,
		CreatedAt:   time.Now(),
	}
	if err := s.commands.Create(ctx, cmd); err != nil {
		return uuid.Nil, err
	}
	s.log.InfoContext(ctx, "send_command: created command", "command_id", cmd.ID, "router_id", routerID)
	return cmd.ID, nil
}

// SendCommandToAllRouters создаёт по одной команде для каждого зарегистрированного роутера; commandType обязателен. Возвращает список id созданных команд (пустой, если роутеров нет).
func (s *CommandService) SendCommandToAllRouters(ctx context.Context, commandType string, payload string) ([]uuid.UUID, error) {
	if commandType == "" {
		return nil, fmt.Errorf("command_type is required")
	}
	routerIDs, err := s.routers.ListRouterIDs(ctx)
	if err != nil {
		return nil, fmt.Errorf("list routers: %w", err)
	}
	ids := make([]uuid.UUID, 0, len(routerIDs))
	for _, routerID := range routerIDs {
		cmd := &repository.Command{
			ID:          uuid.New(),
			RouterID:    routerID,
			CommandType: commandType,
			Payload:     payload,
			Status:      repository.StatusPending,
			CreatedAt:   time.Now(),
		}
		if err := s.commands.Create(ctx, cmd); err != nil {
			return nil, fmt.Errorf("create command for router %s: %w", routerID, err)
		}
		ids = append(ids, cmd.ID)
	}
	s.log.InfoContext(ctx, "send_command_to_all: created commands", "count", len(ids), "router_ids", routerIDs)
	return ids, nil
}

// PollCommands возвращает команды роутера в статусах PENDING/SENT; при выдаче обновляет last_seen_at (EnsureRouter) и переводит PENDING в SENT.
func (s *CommandService) PollCommands(ctx context.Context, routerID uuid.UUID) ([]*repository.Command, error) {
	now := time.Now()
	if _, err := s.routers.EnsureRouter(ctx, routerID, ""); err != nil {
		return nil, fmt.Errorf("ensure router: %w", err)
	}
	commands, err := s.commands.ListPendingOrSentByRouter(ctx, routerID)
	if err != nil {
		return nil, err
	}
	var toMark []uuid.UUID
	for _, c := range commands {
		if c.Status == repository.StatusPending {
			toMark = append(toMark, c.ID)
		}
	}
	if len(toMark) > 0 {
		if err := s.commands.MarkSent(ctx, toMark, now); err != nil {
			return nil, err
		}
		for _, c := range commands {
			if c.Status == repository.StatusPending {
				c.Status = repository.StatusSent
				c.SentAt = &now
			}
		}
	}
	s.log.InfoContext(ctx, "poll_commands", "router_id", routerID, "count", len(commands))
	return commands, nil
}

// AckCommand подтверждает выполнение команды роутером; в хранилище статус переводится в ACKED, записывается acked_at.
func (s *CommandService) AckCommand(ctx context.Context, routerID uuid.UUID, commandID uuid.UUID) error {
	now := time.Now()
	if err := s.commands.Ack(ctx, commandID, routerID, now); err != nil {
		return err
	}
	s.log.InfoContext(ctx, "ack_command", "router_id", routerID, "command_id", commandID)
	return nil
}

// FailStaleSentCommands помечает как FAILED команды в статусе SENT, если с момента sent_at прошло больше maxAge.
// Возвращает количество обновлённых команд.
func (s *CommandService) FailStaleSentCommands(ctx context.Context, maxAge time.Duration) (int64, error) {
	if maxAge <= 0 {
		return 0, nil
	}
	cutoff := time.Now().Add(-maxAge)
	commands, err := s.commands.FailSentBefore(ctx, cutoff)
	if err != nil {
		return 0, err
	}
	affected := int64(len(commands))
	if affected > 0 {
		commandIDs := make([]uuid.UUID, 0, len(commands))
		routerIDs := make([]uuid.UUID, 0, len(commands))
		for _, c := range commands {
			commandIDs = append(commandIDs, c.ID)
			routerIDs = append(routerIDs, c.RouterID)
		}
		s.log.WarnContext(ctx, "fail_stale_sent_commands",
			"affected", affected,
			"cutoff", cutoff,
			"max_age", maxAge,
			"command_ids", commandIDs,
			"router_ids", routerIDs,
		)
	}
	return affected, nil
}
